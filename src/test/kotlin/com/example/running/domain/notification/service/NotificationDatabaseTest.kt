package com.example.running.domain.notification.service

import com.example.running.domain.notification.repository.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.*

/**
 * Real MariaDB only; no connection to the configured application DB.
 * NOTIFICATION_TEST_DB_URL=jdbc:mariadb://127.0.0.1:13406
 * NOTIFICATION_TEST_DB_PASSWORD=<isolated container root password> ./gradlew test --tests '*NotificationDatabaseTest'
 * Each test creates and drops its own notification_test_<uuid> schema.
 */
@EnabledIfEnvironmentVariable(named = "NOTIFICATION_TEST_DB_URL", matches = ".+")
class NotificationDatabaseTest {
    private val database = "notification_test_" + UUID.randomUUID().toString().replace("-", "")
    private lateinit var admin: JdbcTemplate
    private lateinit var jdbc: JdbcTemplate
    private lateinit var repository: NotificationRepository
    private lateinit var queue: NotificationPushRepository
    private lateinit var transaction: TransactionTemplate
    private val anchor = Instant.parse("2026-10-10T00:00:00Z")

    @BeforeEach
    fun setup() {
        val url = System.getenv("NOTIFICATION_TEST_DB_URL")
        require(url.matches(Regex("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+")))
        require(!url.endsWith(":3306") && !url.endsWith(":3395")) { "Use an isolated test server port" }
        val password = System.getenv("NOTIFICATION_TEST_DB_PASSWORD") ?: ""
        fun source(suffix: String) = DriverManagerDataSource("$url/$suffix", "root", password)
        admin = JdbcTemplate(source(""))
        admin.execute("CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
        val source = source(database)
        jdbc = JdbcTemplate(source)
        jdbc.execute("CREATE TABLE user (id BIGINT UNSIGNED PRIMARY KEY, authority_type CHAR(1) NOT NULL DEFAULT 'U', is_enabled TINYINT(1) NOT NULL DEFAULT 1, is_deleted TINYINT(1) NOT NULL DEFAULT 0)")
        jdbc.execute("CREATE TABLE user_configuration (user_id BIGINT UNSIGNED PRIMARY KEY, language VARCHAR(10) NOT NULL DEFAULT 'system')")
        jdbc.execute("CREATE TABLE league_participant (id BIGINT UNSIGNED PRIMARY KEY)")
        jdbc.execute("CREATE TABLE running_record (user_id BIGINT UNSIGNED, end_datetime DATETIME, is_end TINYINT(1), is_statistic_included TINYINT(1))")
        jdbc.update("INSERT INTO user (id, authority_type) VALUES (7, 'U'), (8, 'A'), (9, 'U')")
        jdbc.update("INSERT INTO user_configuration (user_id, language) VALUES (7, 'ja'), (9, 'system')")
        Flyway.configure().dataSource(source).baselineOnMigrate(true).baselineVersion("1.5.0")
            .locations("filesystem:src/main/resources/db/migration").load().migrate()
        repository = NotificationRepository(jdbc)
        queue = NotificationPushRepository(jdbc)
        transaction = TransactionTemplate(DataSourceTransactionManager(source))
    }

    @AfterEach
    fun cleanup() {
        if (::admin.isInitialized) admin.execute("DROP DATABASE $database")
    }

    @Test
    fun `language backfill preserves system and no reminder is created for a non runner`() {
        assertEquals(repository.languageId("ja"), jdbc.queryForObject("SELECT language_id FROM user_configuration WHERE user_id=7", Int::class.java))
        assertNull(jdbc.queryForObject("SELECT language_id FROM user_configuration WHERE user_id=9", Int::class.java))
        assertTrue(repository.dueReminders(Instant.now()).isEmpty())
    }

    @Test
    fun `old or duplicate Health records cannot rewind the anchor or repeat completed reminders`() {
        repository.recordCompletion(7, anchor)
        val first = repository.dueReminders(anchor.plusSeconds(48 * 3600)).single()
        assertTrue(repository.advanceReminder(first, anchor.plusSeconds(72 * 3600)))
        repository.recordCompletion(7, anchor)
        repository.recordCompletion(7, anchor.minusSeconds(60))
        assertTrue(repository.dueReminders(first.next).isEmpty())
        val second = repository.dueReminders(anchor.plusSeconds(72 * 3600)).single()
        assertTrue(repository.advanceReminder(second, null))
        assertTrue(repository.dueReminders(anchor.plusSeconds(90 * 86400)).isEmpty())
        val newRun = anchor.plusSeconds(80 * 3600)
        repository.recordCompletion(7, newRun)
        assertEquals(newRun, repository.reminderAnchor(7))
        assertEquals(newRun.plusSeconds(48 * 3600), repository.dueReminders(newRun.plusSeconds(48 * 3600)).single().next)
    }

    @Test
    fun `first state after rollout includes a newer existing completed run`() {
        val latest = anchor.plusSeconds(3600)
        jdbc.update("INSERT INTO running_record VALUES (7, ?, 1, 1)", NotificationRepository.utc(latest))
        repository.recordCompletion(7, anchor)
        assertEquals(latest, repository.reminderAnchor(7))
    }

    @Test
    fun `two transactions generate one reminder and an inbox failure rolls back the advance`() {
        repository.recordCompletion(7, anchor)
        val first = repository.dueReminders(anchor.plusSeconds(48 * 3600)).single()
        val next = anchor.plusSeconds(72 * 3600)
        assertFailsWith<IllegalStateException> {
            transaction.execute {
                assertTrue(repository.advanceReminder(first, next))
                error("Simulated inbox failure")
            }
        }
        assertEquals(first, repository.dueReminders(first.next).single())
        val barrier = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = executor.invokeAll(List(2) { Callable {
                barrier.await()
                transaction.execute { NotificationService(repository).generateReminder(first, first.next) }
            } }).map { it.get() }
            assertEquals(1, results.count { it == true })
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification", Int::class.java))
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notification_push", Int::class.java))
        } finally { executor.shutdownNow() }
    }

    @Test
    fun `queue CAS chooses one sender and an expired worker cannot overwrite its replacement`() {
        val now = Instant.now().minusSeconds(120)
        val id = repository.createNotification(7, NotificationType.RUNNING_REMINDER, "test", null, null, now)
        val device = repository.bindDevice(7, UUID.randomUUID().toString(), "ExpoPushToken[test]", "IOS", repository.languageId("ko")!!)
        repository.enqueue(id, device, now)
        val candidate = queue.candidates(now).single()
        assertTrue(queue.claim(candidate, "first", now))
        assertFalse(queue.claim(candidate, "other", now))
        queue.recoverExpired(Instant.now())
        val replacement = queue.candidates(Instant.now()).single()
        assertTrue(queue.claim(replacement, "replacement", Instant.now()))
        assertFalse(queue.finish(candidate.id, "first", "PROVIDER_ACCEPTED", Instant.now()))
        assertTrue(queue.finish(candidate.id, "replacement", "FAILED", Instant.now(), "TEST"))
    }

    @Test
    fun `binding version prevents stale logout and stale provider errors from disabling another account`() {
        val installation = UUID.randomUUID().toString()
        val first = repository.bindDevice(7, installation, "ExpoPushToken[test]", "IOS", repository.languageId("ja")!!)
        val second = repository.bindDevice(9, installation, "ExpoPushToken[test]", "IOS", repository.languageId("ko")!!)
        assertTrue(second.version > first.version)
        repository.unbindDevice(7, installation, first.version)
        repository.disableDevice(first.id, first.version)
        assertEquals(listOf(second), repository.devices(9))
    }

    @Test
    fun `withdrawal detaches every device for that user only`() {
        val languageId = repository.languageId("ko")!!
        repository.bindDevice(7, UUID.randomUUID().toString(), "ExpoPushToken[first]", "IOS", languageId)
        repository.bindDevice(7, UUID.randomUUID().toString(), "ExpoPushToken[second]", "ANDROID", languageId)
        repository.bindDevice(9, UUID.randomUUID().toString(), "ExpoPushToken[other]", "IOS", languageId)
        repository.detachAllDevices(7)
        assertTrue(repository.devices(7).isEmpty())
        assertEquals(1, repository.devices(9).size)
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_device WHERE push_token IS NOT NULL", Int::class.java))
    }

    @Test
    fun `notice publication is idempotent and inbox detail is isolated per user`() {
        val service = NotificationService(repository)
        val notice = transaction.execute { service.createNotice(8, listOf(
            NotificationText("ko", "공지", "본문"), NotificationText("ja", "お知らせ", "本文"))) }!!
        transaction.execute { service.publishNotice(8, notice) }
        transaction.execute { service.publishNotice(8, notice) }
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM notification", Int::class.java))
        val item = service.inbox(7, "ja", null, 20).single()
        assertEquals("お知らせ", item.title)
        assertFailsWith<com.example.running.exception.ApiException> { service.open(9, item.id, "ko") }
        assertNull(repository.inbox(7, "ko", null, 20, Instant.now()).single().readDatetime)
        assertNotNull(service.open(7, item.id, "ja").readDatetime)
    }

    @Test
    fun `rollback removes only new schema and preserves legacy language`() {
        val sql = Files.readString(Path.of("deploy/rollback-notifications.sql"))
            .lineSequence().filterNot { it.trimStart().startsWith("--") }.joinToString("\n")
        sql.split(';').filter { it.isNotBlank() }.forEach(jdbc::execute)
        assertEquals("ja", jdbc.queryForObject("SELECT language FROM user_configuration WHERE user_id=7", String::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name='notification'", Int::class.java, database))
    }
}

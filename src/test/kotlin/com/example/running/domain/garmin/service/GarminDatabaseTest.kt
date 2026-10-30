package com.example.running.domain.garmin.service

import com.example.running.common.filter.RequestResponseWrapperFilter
import com.example.running.config.properties.GarminProperties
import com.example.running.domain.activity.repository.ActivityProviderRepository
import com.example.running.domain.garmin.controller.GarminPrivacyFilter
import com.example.running.domain.garmin.controller.GarminWebhookController
import com.example.running.domain.garmin.repository.*
import com.example.running.domain.running.controller.dto.ImportRunningBatchRequest
import com.example.running.domain.running.controller.dto.ImportRunningBatchResponse
import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.enums.RunningAcceptanceStatus
import com.example.running.domain.running.repository.QRunningRecordRepositoryImpl
import com.example.running.domain.running.service.RunningImportService
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.entity.UserConfiguration
import com.example.running.domain.user.repository.UserRepository
import com.example.running.domain.user.service.UserConfigurationService
import com.example.running.exception.GarminControllerAdvice
import com.example.running.exception.RunningValidationException
import com.example.running.exception.RunningValidationReason
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.querydsl.jpa.impl.JPAQueryFactory
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.aop.support.AopUtils
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.Optional
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

@EnabledIfEnvironmentVariable(named = "GARMIN_TEST_DB_URL", matches = ".+")
class GarminDatabaseTest {
    private val database = "garmin_test_" + UUID.randomUUID().toString().replace("-", "")
    private lateinit var admin: JdbcTemplate
    private lateinit var jdbc: JdbcTemplate
    private lateinit var repository: GarminRepository
    private lateinit var context: AnnotationConfigApplicationContext
    private lateinit var transaction: TransactionTemplate
    private val now = Instant.parse("2026-10-24T12:00:00Z")

    @BeforeEach
    fun setup() {
        val url = System.getenv("GARMIN_TEST_DB_URL")
        require(url.matches(Regex("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+")))
        require(!url.endsWith(":3306") && !url.endsWith(":3395")) { "Use an isolated test server port" }
        fun source(suffix: String) = DriverManagerDataSource("$url/$suffix", "root", System.getenv("GARMIN_TEST_DB_PASSWORD") ?: "")
        admin = JdbcTemplate(source(""))
        admin.execute("CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
        val source = source(database)
        jdbc = JdbcTemplate(source)
        jdbc.execute("CREATE TABLE user (id BIGINT UNSIGNED PRIMARY KEY)")
        jdbc.execute("""CREATE TABLE user_configuration (
            user_id BIGINT UNSIGNED PRIMARY KEY,
            health_import_enabled TINYINT(1) NOT NULL DEFAULT 0,
            health_import_last_synced_at DATETIME NULL
        )""")
        jdbc.execute("""CREATE TABLE running_record (
            id BIGINT UNSIGNED PRIMARY KEY,
            user_id BIGINT UNSIGNED NOT NULL DEFAULT 7,
            distance INT NOT NULL DEFAULT 0,
            is_end TINYINT(1) NOT NULL DEFAULT 0,
            start_datetime DATETIME NOT NULL DEFAULT '2026-10-24 00:00:00',
            end_datetime DATETIME NULL,
            point_awarded TINYINT(1) NOT NULL DEFAULT 0,
            point_eligible TINYINT(1) NOT NULL DEFAULT 0
        )""")
        jdbc.update("INSERT INTO user VALUES (7), (8)")
        Flyway.configure().dataSource(source).baselineOnMigrate(true).baselineVersion("1.6.0")
            .locations("filesystem:src/main/resources/db/migration").load().migrate()
        context = AnnotationConfigApplicationContext().apply {
            beanFactory.registerSingleton("jdbcTemplate", jdbc)
            register(PersistenceExceptionTranslationPostProcessor::class.java, GarminRepository::class.java)
            refresh()
        }
        repository = context.getBean(GarminRepository::class.java)
        transaction = TransactionTemplate(DataSourceTransactionManager(source))
    }

    @AfterEach
    fun cleanup() {
        if (::context.isInitialized) context.close()
        if (::admin.isInitialized) admin.execute("DROP DATABASE $database")
    }

    @Test
    fun `Spring managed repository returns disconnected for a user without creating a connection`() {
        val properties = GarminProperties(enabled = true, clientId = "test", clientSecret = "test",
            encryptionKey = Base64.getEncoder().encodeToString(ByteArray(32) { 7 }))
        val users = mock(UserRepository::class.java)
        `when`(users.findById(7)).thenReturn(Optional.of(User(7)))
        val service = GarminConnectionService(properties, repository, users,
            mock(UserConfigurationService::class.java), GarminTokenCipher(properties),
            mock(GarminApiClient::class.java), transaction)
        assertTrue(AopUtils.isCglibProxy(repository))

        val response = service.connection(7)

        assertTrue(response.available)
        assertEquals(GarminConnectionStatus.DISCONNECTED, response.status)
        assertNull(response.connectedAt)
        assertNull(response.lastReceivedAt)
        assertNull(response.lastImportedAt)
        assertEquals(0, response.lastImportedCount)
        assertFalse(response.permissionGranted)
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM activity_connection", Int::class.java))
    }

    @Test
    fun `import provenance migration leaves legacy rows and reward snapshots unchanged`() {
        jdbc.execute("""ALTER TABLE running_record
            DROP COLUMN source_app_id,
            DROP COLUMN source_app_name,
            DROP COLUMN recording_method,
            DROP COLUMN point_exclusion_reason""")
        jdbc.update("""INSERT INTO running_record
            (id, user_id, distance, is_end, start_datetime, end_datetime, point_awarded, point_eligible,
             acceptance_status, league_distance_override, league_end_datetime_override,
             original_start_datetime, original_end_datetime) VALUES
            (21, 7, 3000, 1, '2026-10-01 12:00:00', '2026-10-01 12:10:00', 1, 1,
             'ACCEPTED', 2000, '2026-10-01 12:10:00', '2026-10-01 12:00:00', '2026-10-01 12:10:00')""")
        val snapshotQuery = """SELECT id, user_id, distance, is_end, start_datetime, end_datetime,
            point_awarded, point_eligible, acceptance_status, league_distance_override,
            league_end_datetime_override, original_start_datetime, original_end_datetime
            FROM running_record WHERE id = 21"""
        val before = jdbc.queryForMap(snapshotQuery)

        val migration = Files.readString(Path.of("src/main/resources/db/migration/V1.10.0__preserve_running_import_provenance.sql"))
        migration.split(';').filter { it.isNotBlank() }.forEach(jdbc::execute)

        assertEquals(before, jdbc.queryForMap(snapshotQuery))
        assertEquals(1, jdbc.queryForObject("""SELECT COUNT(*) FROM running_record WHERE id = 21
            AND source_app_id IS NULL AND source_app_name IS NULL
            AND recording_method IS NULL AND point_exclusion_reason IS NULL""", Int::class.java))
        assertEquals(4, jdbc.queryForObject("""SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'running_record' AND IS_NULLABLE = 'YES'
            AND COLUMN_NAME IN ('source_app_id', 'source_app_name', 'recording_method', 'point_exclusion_reason')""",
            Int::class.java, database))
    }

    @Test
    fun `Health baseline migration preserves legacy cutoffs and leaves unknown first activation null`() {
        jdbc.execute("""ALTER TABLE user_configuration
            DROP COLUMN health_import_first_enabled_at,
            DROP COLUMN health_import_reward_cutoff_at""")
        jdbc.update("""INSERT INTO user_configuration (user_id, health_import_enabled, health_import_last_synced_at) VALUES
            (7, 1, '2026-10-01 12:00:00'),
            (8, 0, '2026-10-02 12:00:00'),
            (9, 1, NULL),
            (10, 0, NULL)""")
        val before = jdbc.queryForObject("SELECT DATE_FORMAT(UTC_TIMESTAMP(3), '%Y-%m-%d %H:%i:%s.%f')", String::class.java)!!

        val migration = Files.readString(Path.of("src/main/resources/db/migration/V1.9.0__separate_health_import_reward_cutoff.sql"))
        migration.split(';').filter { it.isNotBlank() }.forEach(jdbc::execute)

        val after = jdbc.queryForObject("SELECT DATE_FORMAT(UTC_TIMESTAMP(3), '%Y-%m-%d %H:%i:%s.%f')", String::class.java)!!
        assertEquals(listOf("2026-10-01 12:00:00", "2026-10-02 12:00:00"), jdbc.queryForList(
            "SELECT DATE_FORMAT(health_import_reward_cutoff_at, '%Y-%m-%d %H:%i:%s') FROM user_configuration WHERE user_id IN (7, 8) ORDER BY user_id",
            String::class.java))
        assertEquals(1, jdbc.queryForObject("""SELECT COUNT(*) FROM user_configuration
            WHERE user_id = 9 AND health_import_reward_cutoff_at BETWEEN ? AND ?""", Int::class.java, before, after))
        assertNull(jdbc.queryForObject("SELECT health_import_reward_cutoff_at FROM user_configuration WHERE user_id = 10", String::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_configuration WHERE health_import_first_enabled_at IS NOT NULL", Int::class.java))
    }

    @Test
    fun `acceptance migration backfills completed history without moving frozen league values or granted points`() {
        val schema = database + "_migration"
        admin.execute("CREATE DATABASE $schema CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
        val source = DriverManagerDataSource("${System.getenv("GARMIN_TEST_DB_URL")}/$schema", "root",
            System.getenv("GARMIN_TEST_DB_PASSWORD") ?: "")
        try {
            val sql = JdbcTemplate(source)
            sql.execute("""CREATE TABLE running_record (
                id BIGINT UNSIGNED PRIMARY KEY, user_id BIGINT UNSIGNED NOT NULL,
                distance INT NOT NULL, is_end TINYINT(1) NOT NULL,
                start_datetime DATETIME NOT NULL, end_datetime DATETIME NULL,
                league_distance_override INT NULL, league_end_datetime_override DATETIME NULL,
                point_awarded TINYINT(1) NOT NULL, point_eligible TINYINT(1) NOT NULL
            )""")
            sql.update("""INSERT INTO running_record VALUES
                (1, 7, 9000, 1, '2026-10-01 12:00:00', '2026-10-25 12:10:00', 1500, '2026-10-01 12:10:00', 1, 1),
                (2, 7, 2000, 1, '2026-10-02 12:00:00', '2026-10-02 12:10:00', 0, NULL, 0, 1),
                (3, 7, 1000, 1, '2026-10-03 12:00:00', '2026-10-03 12:10:00', NULL, NULL, 0, 1),
                (4, 7, 500, 0, '2026-10-04 12:00:00', NULL, NULL, NULL, 0, 1),
                (5, 7, 500, 1, '2026-10-05 12:00:00', '2026-10-05 12:10:00', 0, NULL, 1, 1)""")

            Flyway.configure().dataSource(source).baselineOnMigrate(true).baselineVersion("1.7.0")
                .target("1.8.0").locations("filesystem:src/main/resources/db/migration").load().migrate()

            assertEquals(listOf("ACCEPTED", "PERSONAL_ONLY", "ACCEPTED", "PENDING", "ACCEPTED"),
                sql.queryForList("SELECT acceptance_status FROM running_record ORDER BY id", String::class.java))
            assertEquals(1500, sql.queryForObject("SELECT league_distance_override FROM running_record WHERE id = 1", Int::class.java))
            assertEquals("2026-10-01 12:10:00", sql.queryForObject(
                "SELECT DATE_FORMAT(original_end_datetime, '%Y-%m-%d %H:%i:%s') FROM running_record WHERE id = 1", String::class.java))
            assertEquals("2026-10-25 12:10:00", sql.queryForObject(
                "SELECT DATE_FORMAT(end_datetime, '%Y-%m-%d %H:%i:%s') FROM running_record WHERE id = 1", String::class.java))
            assertEquals(0, sql.queryForObject("SELECT point_eligible FROM running_record WHERE id = 2", Int::class.java))
            assertEquals(1000, sql.queryForObject("SELECT league_distance_override FROM running_record WHERE id = 3", Int::class.java))
            assertNull(sql.queryForObject("SELECT original_end_datetime FROM running_record WHERE id = 4", java.sql.Timestamp::class.java))
            assertEquals(2, sql.queryForObject("SELECT COUNT(*) FROM running_record WHERE point_awarded = 1", Int::class.java))
        } finally {
            admin.execute("DROP DATABASE $schema")
        }
    }

    @Test
    fun `state can be claimed only once across concurrent transactions`() {
        val hash = "a".repeat(64)
        repository.insertState(GarminOAuthState(hash, 7, "encrypted-verifier", now.plusSeconds(600)))
        val barrier = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = executor.invokeAll(List(2) { Callable {
                barrier.await()
                transaction.execute { repository.consumeState(hash, now) }
            } }).map { it.get() }
            assertEquals(1, results.count { it != null })
            assertNull(transaction.execute { repository.consumeState(hash, now.plusSeconds(1)) })
            assertEquals(now.plusSeconds(600), repository.state(hash)?.expiresAt)
        } finally { executor.shutdownNow() }
    }

    @Test
    fun `new authorization and disconnect invalidate a state already consumed before provider exchange`() {
        val original = "1".repeat(64)
        repository.insertState(GarminOAuthState(original, 7, "verifier", now.plusSeconds(600)))
        assertNotNull(transaction.execute { repository.consumeState(original, now) })
        val replacement = "2".repeat(64)
        transaction.executeWithoutResult {
            repository.insertState(GarminOAuthState(replacement, 7, "replacement", now.plusSeconds(600)))
        }
        assertNull(repository.state(original))
        assertNotNull(transaction.execute { repository.consumeState(replacement, now) })
        transaction.executeWithoutResult { repository.deleteStates(7) }
        assertNull(repository.state(replacement))
    }

    @Test
    fun `an external account conflict cannot change the existing owner`() {
        transaction.executeWithoutResult { repository.saveConnection(value(7)) }
        assertFails {
            transaction.executeWithoutResult { repository.saveConnection(value(8)) }
        }
        assertEquals(7, repository.connectionByGarminId("test-garmin-user")?.userId)
        assertEquals(null, repository.connection(8))
        assertEquals(now, repository.connection(7)?.connectedAt)
    }

    @Test
    fun `duplicate push is stored once and a rollback remains available to the next worker`() {
        repository.enqueue("b".repeat(64), "encrypted-payload", now)
        repository.enqueue("b".repeat(64), "replacement-must-not-overwrite", now)
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM activity_webhook_inbox", Int::class.java))
        assertFails {
            transaction.executeWithoutResult {
                val value = checkNotNull(repository.nextInbox(now))
                assertEquals("encrypted-payload", value.payload)
                repository.completeInbox(value.id, now)
                error("Simulated record import rollback")
            }
        }
        transaction.executeWithoutResult {
            val value = checkNotNull(repository.nextInbox(now))
            repository.completeInbox(value.id, now)
        }
        assertNull(transaction.execute { repository.nextInbox(now) })
        assertNull(jdbc.queryForObject("SELECT payload_encrypted FROM activity_webhook_inbox", String::class.java))
    }

    @Test
    fun `locked inbox item is not processed by another worker`() {
        repository.enqueue("c".repeat(64), "encrypted-payload", now)
        val executor = Executors.newSingleThreadExecutor()
        try {
            transaction.executeWithoutResult {
                assertNotNull(repository.nextInbox(now))
                assertNull(executor.submit<GarminInbox?> { transaction.execute { repository.nextInbox(now) } }.get())
            }
        } finally { executor.shutdownNow() }
    }

    @Test
    fun `revocation clears credentials while retaining import history and failed work is visible`() {
        repository.saveConnection(value(7))
        repository.received(7, now)
        repository.imported(7, now.plusSeconds(5), 1)
        repository.received(7, now.minusSeconds(5))
        repository.clearConnection(7)
        val cleared = checkNotNull(repository.connection(7))
        assertEquals(GarminConnectionStatus.DISCONNECTED, cleared.status)
        assertNull(cleared.accessToken)
        assertNull(cleared.refreshToken)
        assertNull(cleared.externalUserId)
        assertEquals(now.plusSeconds(5), cleared.lastImportedAt)
        assertEquals(now, cleared.lastReceivedAt)
        assertNull(cleared.connectedAt)
        repository.enqueue("d".repeat(64), "encrypted-payload", now)
        val id = transaction.execute { repository.nextInbox(now)?.id }!!
        repeat(10) { repository.failInbox(id, now) }
        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM activity_webhook_inbox WHERE id = ?", String::class.java, id))
        assertNull(jdbc.queryForObject("SELECT payload_encrypted FROM activity_webhook_inbox WHERE id = ?", String::class.java, id))
    }

    @Test
    fun `repeated revocation failures cannot starve maintenance of other connections`() {
        repository.saveConnection(value(7))
        repository.saveConnection(value(8, "another-test-user"))
        repository.maintenanceAttempt(7, now)
        assertEquals(listOf(8L, 7L), repository.dueConnections(now.plusSeconds(86400)))
    }

    @Test
    fun `provider boundaries isolate identities credentials states and inbox events`() {
        val other = ActivityProviderRepository(jdbc, "OTHER")
        repository.saveConnection(value(7))
        other.saveConnection(value(7))
        other.saveConnection(value(8, "TEST-garmin-user"))
        assertEquals(7, other.connectionByExternalId("test-garmin-user")?.userId)
        assertEquals(8, other.connectionByExternalId("TEST-garmin-user")?.userId)
        assertNull(repository.connection(8))
        val hash = "e".repeat(64)
        repository.insertState(GarminOAuthState(hash, 7, "first", now.plusSeconds(600)))
        assertNull(other.consumeState(hash, now))
        assertEquals("encrypted-access", repository.connection(7)?.accessToken)
        repository.insertState(GarminOAuthState("f".repeat(64), 7, "replacement", now.plusSeconds(600)))
        assertNull(repository.consumeState(hash, now))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM activity_connection WHERE provider = 'GARMIN' AND user_id = 7", Int::class.java))
        repository.enqueue(hash, "garmin-payload", now)
        other.enqueue(hash, "other-payload", now)
        val item = transaction.execute { other.nextInbox(now) }!!
        repository.completeInbox(item.id, now)
        assertEquals("other-payload", transaction.execute { other.nextInbox(now) }?.payload)
        repository.clearConnection(7)
        assertEquals("encrypted-access", other.connection(7)?.accessToken)
    }

    @Test
    fun `credential requires a parent connection and access expiry but refresh expiry is optional`() {
        assertFails { jdbc.update("""INSERT INTO activity_oauth_credential
            (connection_id, access_token_encrypted, refresh_token_encrypted, access_expires_at)
            VALUES (999, 'access', 'refresh', CURRENT_TIMESTAMP(3))""") }
        repository.saveConnection(GarminConnection(7, "x".repeat(255), GarminConnectionStatus.CONNECTED,
            "access", "refresh", now.plusSeconds(3600), null, true, now, null, null, 0))
        assertNull(repository.connection(7)?.refreshExpiresAt)
        assertEquals("x".repeat(255), repository.connection(7)?.externalUserId)
        assertFails { jdbc.update("UPDATE activity_oauth_credential SET access_expires_at = NULL") }
        assertFails { jdbc.update("INSERT INTO activity_connection (user_id, provider, status) VALUES (999, 'GARMIN', 'DISCONNECTED')") }
    }

    @Test
    fun `worker rolls back record writes with inbox completion and retries without partial history`() {
        val imports = mock(RunningImportService::class.java)
        val (worker, cipher) = worker(imports)
        repository.saveConnection(value(7, "worker-user"))
        val hash = "9".repeat(64)
        val body = """{"activities":[{"userId":"worker-user","activityId":123,"activityType":"RUNNING",
            "startTimeInSeconds":1792800000,"durationInSeconds":600,"distanceInMeters":2000}]}"""
        repository.enqueue(hash, cipher.encrypt(body, "inbox:$hash"), Instant.now())
        `when`(imports.importGarmin(eq(7L),
            any(ImportRunningBatchRequest::class.java)
                ?: ImportRunningBatchRequest(emptyList()),
            any(OffsetDateTime::class.java) ?: now.atOffset(ZoneOffset.UTC),
            eq(false), any(Instant::class.java) ?: now)).thenAnswer {
                jdbc.update("INSERT INTO running_record (id) VALUES (123)")
                error("Simulated import failure")
            }
        assertTrue(worker.processOne())
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM running_record", Int::class.java))
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM activity_webhook_inbox", String::class.java))
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM activity_webhook_inbox", Int::class.java))
        assertNull(repository.connection(7)?.lastImportedAt)
    }

    @Test
    fun `permanent validation rejection uses original receipt rolls back writes and completes inbox without retry`() {
        val imports = mock(RunningImportService::class.java)
        val (worker, cipher) = worker(imports)
        repository.saveConnection(value(7, "worker-user"))
        val receivedAt = now.plusSeconds(60)
        val hash = "4".repeat(64)
        // End is after the original receipt but before processing time; retries cannot make it valid.
        val body = """{"activities":[{"userId":"worker-user","activityId":54321,"activityType":"RUNNING",
            "startTimeInSeconds":${now.epochSecond},"durationInSeconds":600,"distanceInMeters":2000}]}"""
        repository.enqueue(hash, cipher.encrypt(body, "inbox:$hash"), receivedAt)
        var importCalls = 0
        `when`(imports.importGarmin(eq(7L),
            any(ImportRunningBatchRequest::class.java) ?: ImportRunningBatchRequest(emptyList()),
            any(OffsetDateTime::class.java) ?: now.atOffset(ZoneOffset.UTC),
            eq(false), any(Instant::class.java) ?: now)).thenAnswer { invocation ->
                importCalls += 1
                assertEquals(receivedAt, invocation.getArgument<Instant>(4))
                jdbc.update("INSERT INTO running_record (id) VALUES (54321)")
                throw RunningValidationException(RunningValidationReason.RUNNING_FUTURE_END_TIME)
            }

        assertTrue(worker.processOne())

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM running_record", Int::class.java))
        assertEquals("DONE", jdbc.queryForObject("SELECT status FROM activity_webhook_inbox", String::class.java))
        assertNull(jdbc.queryForObject("SELECT payload_encrypted FROM activity_webhook_inbox", String::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT attempts FROM activity_webhook_inbox", Int::class.java))
        assertNull(repository.connection(7)?.lastImportedAt)
        repository.enqueue(hash, cipher.encrypt(body, "inbox:$hash"), receivedAt.plusSeconds(30))
        assertFalse(worker.processOne())
        assertEquals(1, importCalls)
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM activity_webhook_inbox", Int::class.java))
    }

    @ParameterizedTest
    @ValueSource(strings = [
        """{"userId":"worker-user","activityId":999,"activityType":"RUNNING","startTimeInSeconds":1792800000,"durationInSeconds":-1}""",
        """{"activityId":999,"activityType":"RUNNING","startTimeInSeconds":1792800000,"durationInSeconds":600}""",
    ])
    fun `one invalid event does not block valid siblings or repeat their imports on redelivery`(invalidEvent: String) {
        val imports = mock(RunningImportService::class.java)
        val (worker, _) = worker(imports)
        repository.saveConnection(value(7, "worker-user"))
        `when`(imports.importGarmin(eq(7L),
            any(ImportRunningBatchRequest::class.java)
                ?: ImportRunningBatchRequest(emptyList()),
            any(OffsetDateTime::class.java) ?: now.atOffset(ZoneOffset.UTC),
            eq(false), any(Instant::class.java) ?: now)).thenAnswer { invocation ->
                val request = invocation.getArgument<ImportRunningBatchRequest>(1)
                jdbc.update("INSERT INTO running_record (id) VALUES (?)", request.records.single().externalId.toLong())
                ImportRunningBatchResponse(1, 0, 0, 0, false)
            }
        val first = """{"userId":"worker-user","activityId":101,"activityType":"RUNNING","startTimeInSeconds":1792800000,"durationInSeconds":600,"distanceInMeters":2000}"""
        val second = """{"userId":"worker-user","activityId":102,"activityType":"RUNNING","startTimeInSeconds":1792803600,"durationInSeconds":600,"distanceInMeters":2000}"""
        val receiver = webhookReceiver()
        receiver.receive("t".repeat(43), """{"activities":[$first,$invalidEvent,$second]}""".byteInputStream())

        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM activity_webhook_inbox", Int::class.java))
        assertTrue(worker.processOne())
        assertTrue(worker.processOne())
        assertTrue(worker.processOne())
        assertFalse(worker.processOne())
        assertEquals(listOf(101L, 102L), jdbc.queryForList("SELECT id FROM running_record ORDER BY id", Long::class.java))
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM activity_webhook_inbox WHERE status = 'DONE' AND payload_encrypted IS NULL", Int::class.java))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM activity_webhook_inbox WHERE status = 'PENDING' AND attempts = 1 AND payload_encrypted IS NOT NULL", Int::class.java))

        // Provider retries may regroup already completed events into a different envelope.
        receiver.receive("t".repeat(43), """{"activities":[$second,$first]}""".byteInputStream())
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM activity_webhook_inbox", Int::class.java))
        assertFalse(worker.processOne())
        assertEquals(listOf(101L, 102L), jdbc.queryForList("SELECT id FROM running_record ORDER BY id", Long::class.java))
    }

    @Test
    fun `failure storing a later event rolls back the whole delivery and a retry stores all events`() {
        val failingRepository = spy(repository)
        var writes = 0
        doAnswer { invocation ->
            writes += 1
            if (writes == 2) throw DataAccessResourceFailureException("Simulated storage failure")
            invocation.callRealMethod()
        }.`when`(failingRepository).enqueue(anyString(), anyString(),
            any(Instant::class.java) ?: now)
        val body = """{"activities":[{"userId":"worker-user","activityId":101},{"userId":"worker-user","activityId":102}]}"""

        assertFailsWith<DataAccessResourceFailureException> {
            webhookReceiver(failingRepository).receive("t".repeat(43), body.byteInputStream())
        }
        assertEquals(2, writes)
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM activity_webhook_inbox", Int::class.java))

        webhookReceiver().receive("t".repeat(43), body.byteInputStream())
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM activity_webhook_inbox WHERE status = 'PENDING'", Int::class.java))
    }

    private fun webhookReceiver(target: GarminRepository = repository): GarminWebhookReceiver {
        val properties = GarminProperties(enabled = true, clientId = "test", clientSecret = "test",
            webhookToken = "t".repeat(43), encryptionKey = Base64.getEncoder().encodeToString(ByteArray(32) { 7 }))
        return GarminWebhookReceiver(properties, target, GarminTokenCipher(properties),
            jacksonObjectMapper(), transaction)
    }

    @Test
    fun `worker drops queued activities from before reconnection and after disconnect`() {
        val imports = mock(RunningImportService::class.java)
        val (worker, cipher) = worker(imports)
        repository.saveConnection(value(7, "worker-user"))
        val hash = "8".repeat(64)
        val body = """{"activities":[{"userId":"worker-user","activityId":123,"activityType":"RUNNING",
            "startTimeInSeconds":1792800000,"durationInSeconds":600,"distanceInMeters":2000}]}"""
        repository.enqueue(hash, cipher.encrypt(body, "inbox:$hash"), now.minusSeconds(1))
        assertTrue(worker.processOne())
        repository.setStatus(7, GarminConnectionStatus.DISCONNECTING)
        val second = "7".repeat(64)
        repository.enqueue(second, cipher.encrypt(body, "inbox:$second"), Instant.now())
        assertTrue(worker.processOne())
        verifyNoInteractions(imports)
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM activity_webhook_inbox WHERE status = 'DONE' AND payload_encrypted IS NULL", Int::class.java))
    }

    private fun worker(imports: RunningImportService): Pair<GarminInboxWorker, GarminTokenCipher> {
        val properties = GarminProperties(encryptionKey =
            Base64.getEncoder().encodeToString(ByteArray(32) { 7 }))
        val cipher = GarminTokenCipher(properties)
        val users = mock(UserRepository::class.java)
        `when`(users.findById(7)).thenReturn(Optional.of(User(7)))
        val configurations = mock(UserConfigurationService::class.java)
        return GarminInboxWorker(repository, cipher, jacksonObjectMapper(),
            GarminActivityMapper(), imports, users, configurations, mock(GarminApiClient::class.java), transaction) to cipher
    }

    @Test
    fun `JPA record and JDBC inbox share rollback and league snapshot keeps its original season`() {
        val schema = database + "_jpa"
        admin.execute("CREATE DATABASE $schema CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
        val source = DriverManagerDataSource("${System.getenv("GARMIN_TEST_DB_URL")}/$schema", "root",
            System.getenv("GARMIN_TEST_DB_PASSWORD") ?: "")
        val factory = LocalContainerEntityManagerFactoryBean().apply {
            dataSource = source
            setPackagesToScan("com.example.running.domain")
            jpaVendorAdapter = HibernateJpaVendorAdapter()
            setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "create-drop",
                "hibernate.physical_naming_strategy" to "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"))
        }
        try {
            factory.afterPropertiesSet()
            val emf = checkNotNull(factory.`object`)
            val em = SharedEntityManagerCreator.createSharedEntityManager(emf)
            val tx = TransactionTemplate(JpaTransactionManager(emf))
            val sql = JdbcTemplate(source)
            val migration = Files.readString(Path.of("src/main/resources/db/migration/V1.7.0__create_garmin_integration.sql"))
            migration.substringAfter(';').split(';').filter { it.isNotBlank() }.forEach(sql::execute)
            val repo = GarminRepository(sql)
            val userId = tx.execute {
                val user = User(0)
                em.persist(user)
                em.flush()
                user.id
            }!!
            repo.enqueue("6".repeat(64), "encrypted", now)
            assertFails {
                tx.executeWithoutResult {
                    val user = em.getReference(User::class.java, userId)
                    em.persist(RunningRecord(user = user, distance = 1000))
                    em.flush()
                    repo.completeInbox(checkNotNull(repo.nextInbox(now)).id, now)
                    error("Rollback JPA and JDBC together")
                }
            }
            assertEquals(0, sql.queryForObject("SELECT COUNT(*) FROM running_record", Int::class.java))
            assertEquals("PENDING", sql.queryForObject("SELECT status FROM activity_webhook_inbox", String::class.java))
            val firstEnd = now.atOffset(ZoneOffset.UTC)
            tx.executeWithoutResult {
                val user = em.getReference(User::class.java, userId)
                em.persist(RunningRecord(user = user, distance = 9000,
                    isEnd = true, acceptanceStatus = RunningAcceptanceStatus.ACCEPTED,
                    endDatetime = firstEnd.plusMonths(1), leagueDistanceOverride = 1500,
                    leagueEndDatetimeOverride = firstEnd))
                em.persist(RunningRecord(user = user, distance = 2000,
                    isEnd = true, acceptanceStatus = RunningAcceptanceStatus.PERSONAL_ONLY,
                    endDatetime = firstEnd, leagueDistanceOverride = 0))
                em.persist(RunningRecord(user = user, distance = 500,
                    isEnd = true, acceptanceStatus = RunningAcceptanceStatus.ACCEPTED,
                    endDatetime = firstEnd, leagueDistanceOverride = 500, leagueEndDatetimeOverride = firstEnd))
            }
            val query = QRunningRecordRepositoryImpl(JPAQueryFactory(em))
            assertEquals(2000, query.sumIncludedDistanceByUserIdAndEndDatetimeBetween(userId, firstEnd.minusDays(1), firstEnd.plusDays(1)))
            assertEquals(0, query.sumIncludedDistanceByUserIdAndEndDatetimeBetween(userId, firstEnd.plusMonths(1).minusDays(1), firstEnd.plusMonths(1).plusDays(1)))
        } finally {
            factory.destroy()
            admin.execute("DROP DATABASE $schema")
        }
    }

    @Test
    fun `callback claimed before a new authorization cannot exchange after replacement commits`() {
        callbackInvalidation { it.authorize(7) }
    }

    @Test
    fun `callback claimed before disconnect cannot exchange after disconnect commits`() {
        callbackInvalidation { service -> transaction.executeWithoutResult { service.stopImport(7) } }
    }

    private fun callbackInvalidation(invalidate: (GarminConnectionService) -> Unit) {
        val properties = GarminProperties(enabled = true, clientId = "test", clientSecret = "test",
            encryptionKey = Base64.getEncoder().encodeToString(ByteArray(32) { 7 }))
        val cipher = GarminTokenCipher(properties)
        val users = mock(UserRepository::class.java)
        `when`(users.findById(7)).thenReturn(Optional.of(User(7)))
        val configurations = mock(UserConfigurationService::class.java)
        val reachedLock = CountDownLatch(1)
        val resume = CountDownLatch(1)
        `when`(configurations.getOrCreateForUpdate(7)).thenAnswer {
            if (Thread.currentThread().name == "callback-test") {
                reachedLock.countDown()
                check(resume.await(10, TimeUnit.SECONDS))
            }
            jdbc.queryForObject("SELECT id FROM user WHERE id = 7 FOR UPDATE", Long::class.java)
            UserConfiguration(7)
        }
        val api = mock(GarminApiClient::class.java)
        `when`(api.authorizationUrl(anyString(), anyString())).thenReturn("https://connect.garmin.com/oauth2Confirm")
        val service = GarminConnectionService(properties, repository, users, configurations, cipher, api, transaction)
        val state = "z".repeat(43)
        val hash = GarminPkce.stateHash(state)
        repository.insertState(GarminOAuthState(hash, 7, cipher.encrypt("verifier", "state:$hash"), Instant.now().plusSeconds(600)))
        val executor = Executors.newSingleThreadExecutor { Thread(it, "callback-test") }
        try {
            val callback = executor.submit<String> { service.callback(state, "code", null) }
            assertTrue(reachedLock.await(10, TimeUnit.SECONDS))
            assertNull(transaction.execute { repository.consumeState(hash, Instant.now()) })
            invalidate(service)
            resume.countDown()
            assertTrue(callback.get(10, TimeUnit.SECONDS).contains("garminResult=failed"))
            verify(api, never()).exchangeCode(anyString(), anyString())
        } finally {
            resume.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `HTTP webhook acknowledges committed encrypted storage and rejects bad credentials body or storage failure`() {
        val token = "t".repeat(43)
        val properties = GarminProperties(enabled = true, clientId = "test", clientSecret = "test",
            webhookToken = token, encryptionKey = Base64.getEncoder().encodeToString(ByteArray(32) { 7 }))
        val cipher = GarminTokenCipher(properties)
        val receiver = GarminWebhookReceiver(properties, repository, cipher, jacksonObjectMapper(),
            transaction)
        val mvc = MockMvcBuilders.standaloneSetup(
            GarminWebhookController(receiver))
            .setControllerAdvice(GarminControllerAdvice())
            .addFilters<StandaloneMockMvcBuilder>(
                RequestResponseWrapperFilter(),
                GarminPrivacyFilter()).build()
        val body = """{"activities":[{"userId":"private-user","activityId":123,"activityType":"RUNNING",
            "startTimeInSeconds":1792800000,"durationInSeconds":600,"distanceInMeters":2000}]}"""
        fun post(secret: String, payload: String) = mvc.perform(MockMvcRequestBuilders
            .post("/api/v1/garmin/webhook").param("token", secret).contentType("application/json").content(payload))
        post("wrong", body).andExpect(MockMvcResultMatchers.status().isUnauthorized)
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM activity_webhook_inbox", Int::class.java))
        post(token, body).andExpect(MockMvcResultMatchers.status().isOk)
        post(token, body).andExpect(MockMvcResultMatchers.status().isOk)
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM activity_webhook_inbox", Int::class.java))
        val inbox = checkNotNull(transaction.execute { repository.nextInbox(Instant.now()) })
        assertFalse(inbox.payload.contains("private-user"))
        val mapper = jacksonObjectMapper()
        assertEquals(mapper.readTree(body), mapper.readTree(cipher.decrypt(inbox.payload, "inbox:${inbox.hash}")))
        post(token, "{}").andExpect(MockMvcResultMatchers.status().isBadRequest)
        post(token, "x".repeat(GarminWebhookReceiver.MAX_BYTES + 1))
            .andExpect(MockMvcResultMatchers.status().isPayloadTooLarge)
        jdbc.execute("DROP TABLE activity_webhook_inbox")
        val failed = post(token, body).andExpect(MockMvcResultMatchers.status().isServiceUnavailable)
            .andReturn().response.contentAsString
        assertFalse(failed.contains("private-user"))
        assertFalse(failed.contains(token))
    }

    private fun value(userId: Long, garminId: String = "test-garmin-user") = GarminConnection(userId, garminId, GarminConnectionStatus.CONNECTED,
        "encrypted-access", "encrypted-refresh", now.plusSeconds(86400), now.plusSeconds(7776000), true, now, null, null, 0)
}

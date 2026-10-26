package com.example.running.domain.running.service

import com.example.running.config.properties.GarminProperties
import com.example.running.domain.garmin.repository.GarminConnection
import com.example.running.domain.garmin.repository.GarminConnectionStatus
import com.example.running.domain.garmin.repository.GarminRepository
import com.example.running.domain.garmin.service.GarminActivityMapper
import com.example.running.domain.garmin.service.GarminApiClient
import com.example.running.domain.garmin.service.GarminInboxWorker
import com.example.running.domain.garmin.service.GarminTokenCipher
import com.example.running.domain.league.service.LeagueService
import com.example.running.domain.notification.repository.NotificationRepository
import com.example.running.domain.notification.service.NotificationService
import com.example.running.domain.point.entity.PointType
import com.example.running.domain.point.entity.UserPoint
import com.example.running.domain.point.enums.PointTypeName
import com.example.running.domain.point.repository.QUserPointHistoryRepositoryImpl
import com.example.running.domain.point.repository.UserPointHistoryRepository
import com.example.running.domain.point.repository.UserPointRepository
import com.example.running.domain.point.service.UserPointHistoryService
import com.example.running.domain.point.service.UserPointService
import com.example.running.domain.running.controller.dto.ImportRunningBatchRequest
import com.example.running.domain.running.controller.dto.ImportRunningRecordRequest
import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.enums.RunningAcceptanceStatus
import com.example.running.domain.running.enums.RunningRecordSource
import com.example.running.domain.running.repository.QRunningRecordRepositoryImpl
import com.example.running.domain.running.repository.RunningRecordItemJdbcRepositoryImpl
import com.example.running.domain.running.repository.RunningRecordItemRepository
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.running.service.dto.RunningRecordUpdateDto
import com.example.running.domain.shoe.service.ShoeService
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.entity.UserConfiguration
import com.example.running.domain.user.repository.QUserRepositoryImpl
import com.example.running.domain.user.repository.UserConfigurationRepository
import com.example.running.domain.user.repository.UserRepository
import com.example.running.domain.user.service.UserConfigurationService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.Mockito.*
import org.springframework.aop.framework.ProxyFactory
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.data.repository.core.support.RepositoryComposition.RepositoryFragments
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.*

/** Uses only an explicitly configured isolated localhost MariaDB, never the development database. */
@EnabledIfEnvironmentVariable(named = "GARMIN_TEST_DB_URL", matches = ".+")
class RunningAcceptanceDatabaseTest {
    private val database = "running_acceptance_test_" + UUID.randomUUID().toString().replace("-", "")
    private val start = Instant.parse("2026-10-24T12:00:00Z").atOffset(ZoneOffset.UTC)
    private val receipt = start.plusHours(3).toInstant()
    private lateinit var admin: JdbcTemplate
    private lateinit var jdbc: JdbcTemplate
    private lateinit var factory: LocalContainerEntityManagerFactoryBean
    private lateinit var em: EntityManager
    private lateinit var transaction: TransactionTemplate
    private lateinit var records: RunningRecordRepository
    private lateinit var users: UserRepository
    private lateinit var configurations: UserConfigurationService
    private lateinit var queries: QRunningRecordRepositoryImpl
    private lateinit var imports: RunningImportService
    private lateinit var recordService: RunningRecordService
    private var userId: Long = 0
    private val notifications = mock(NotificationService::class.java)
    private val league = mock(LeagueService::class.java)

    @BeforeEach
    fun setup() {
        val url = System.getenv("GARMIN_TEST_DB_URL")
        require(url.matches(Regex("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+")))
        require(!url.endsWith(":3306") && !url.endsWith(":3395")) { "Use an isolated test server port" }
        val password = System.getenv("GARMIN_TEST_DB_PASSWORD") ?: ""
        admin = JdbcTemplate(DriverManagerDataSource("$url/", "root", password))
        admin.execute("CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
        val source = DriverManagerDataSource("$url/$database", "root", password)
        jdbc = JdbcTemplate(source)
        factory = LocalContainerEntityManagerFactoryBean().apply {
            dataSource = source
            setPackagesToScan("com.example.running.domain")
            jpaVendorAdapter = HibernateJpaVendorAdapter()
            setJpaPropertyMap(mapOf(
                "hibernate.hbm2ddl.auto" to "create-drop",
                "hibernate.jdbc.time_zone" to "UTC",
                "hibernate.physical_naming_strategy" to "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy",
            ))
        }
        factory.afterPropertiesSet()
        val emf = checkNotNull(factory.`object`)
        em = SharedEntityManagerCreator.createSharedEntityManager(emf)
        val manager = JpaTransactionManager(emf)
        transaction = TransactionTemplate(manager)
        val queryFactory = JPAQueryFactory(em)
        val repositoryFactory = JpaRepositoryFactory(em)
        queries = QRunningRecordRepositoryImpl(queryFactory)
        records = repositoryFactory.getRepository(RunningRecordRepository::class.java, RepositoryFragments.just(queries))
        val items = repositoryFactory.getRepository(RunningRecordItemRepository::class.java,
            RepositoryFragments.just(RunningRecordItemJdbcRepositoryImpl(jdbc)))
        users = repositoryFactory.getRepository(UserRepository::class.java,
            RepositoryFragments.just(QUserRepositoryImpl(queryFactory)))
        configurations = UserConfigurationService(
            repositoryFactory.getRepository(UserConfigurationRepository::class.java),
            mock(NotificationRepository::class.java),
        )
        val points = UserPointService(
            UserPointHistoryService(repositoryFactory.getRepository(UserPointHistoryRepository::class.java,
                RepositoryFragments.just(QUserPointHistoryRepositoryImpl(queryFactory)))),
            repositoryFactory.getRepository(UserPointRepository::class.java),
        )
        val validation = RunningValidationService()
        recordService = RunningRecordService(records, mock(ShoeService::class.java), configurations, validation)
        val acceptance = RunningAcceptanceService(records, items, configurations, points,
            league, notifications, recordService, validation)
        // Use the production entry point's @Transactional metadata, not a test-only transaction around imports.
        val target = RunningImportService(records, items, configurations, acceptance, validation)
        imports = ProxyFactory(target).apply {
            isProxyTargetClass = true
            addAdvice(TransactionInterceptor(manager, AnnotationTransactionAttributeSource()))
        }.proxy as RunningImportService
        userId = transaction.execute {
            val user = User(0)
            em.persist(user)
            em.flush()
            em.persist(UserConfiguration(user.id, healthImportEnabled = true,
                healthImportLastSyncedAt = start.minusDays(1)))
            em.persist(UserPoint(user = user, point = 50))
            em.persist(PointType(PointTypeName.RUNNING.id, "RUNNING"))
            user.id
        }!!
    }

    @AfterEach
    fun cleanup() {
        if (::factory.isInitialized) factory.destroy()
        if (::admin.isInitialized) admin.execute("DROP DATABASE IF EXISTS $database")
    }

    @Test
    fun `concurrent Garmin and Health completions award one accepted interval only`() {
        val firstAtCompletion = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        doAnswer {
            firstAtCompletion.countDown()
            check(releaseFirst.await(10, TimeUnit.SECONDS))
            null
        }.`when`(notifications).recordCompletion(userId, start.plusMinutes(10).toInstant())
        val executor = Executors.newFixedThreadPool(2)
        try {
            val garmin = executor.submit(Callable {
                imports.importGarmin(userId, batch("garmin-id"), start.minusDays(1), false, receipt)
            })
            assertTrue(firstAtCompletion.await(10, TimeUnit.SECONDS))
            val health = executor.submit(Callable {
                secondStarted.countDown()
                imports.importBatch(userId, batch("health-id"), receipt)
            })
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS))
            assertFailsWith<TimeoutException> { health.get(200, TimeUnit.MILLISECONDS) }
            releaseFirst.countDown()
            val results = listOf(garmin.get(10, TimeUnit.SECONDS), health.get(10, TimeUnit.SECONDS))
            assertEquals(10, results.sumOf { it.awardedPoint })
            assertEquals(1, results.sumOf { it.duplicateCount })
            assertEquals(listOf("ACCEPTED", "DUPLICATE"),
                jdbc.queryForList("SELECT acceptance_status FROM running_record ORDER BY id", String::class.java))
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_point_history", Int::class.java))
            assertEquals(10, jdbc.queryForObject("SELECT SUM(point) FROM user_point_history", Int::class.java))
            assertEquals(60, jdbc.queryForObject("SELECT point FROM user_point WHERE user_id = ?", Int::class.java, userId))
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM running_record WHERE point_awarded = 1", Int::class.java))
            assertEquals(1000L, leagueDistance())
            assertEquals(listOf("garmin-id", "health-id"),
                jdbc.queryForList("SELECT external_id FROM running_record ORDER BY id", String::class.java))
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `worker reads committed point balance and league distance after waiting behind another completion`() {
        installGarminTables()
        val repository = spy(GarminRepository(jdbc))
        val properties = GarminProperties(encryptionKey = Base64.getEncoder().encodeToString(ByteArray(32) { 7 }))
        val cipher = GarminTokenCipher(properties)
        repository.saveConnection(GarminConnection(userId, "snapshot-user", GarminConnectionStatus.CONNECTED,
            "unused-access", "unused-refresh", receipt.plusSeconds(86400), null,
            true, start.minusDays(1).toInstant(), null, null, 0))
        val hash = "a".repeat(64)
        val body = """{"activities":[{"userId":"snapshot-user","activityId":"worker-record",
            "activityType":"RUNNING","startTimeInSeconds":${start.plusMinutes(20).toEpochSecond()},
            "durationInSeconds":600,"distanceInMeters":1000}]}"""
        repository.enqueue(hash, cipher.encrypt(body, "inbox:$hash"), receipt)
        val firstAtCompletion = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val workerReadConnection = CountDownLatch(1)
        val leagueSnapshots = CopyOnWriteArrayList<Long>()
        doAnswer {
            firstAtCompletion.countDown()
            check(releaseFirst.await(10, TimeUnit.SECONDS))
            null
        }.`when`(notifications).recordCompletion(userId, start.plusMinutes(10).toInstant())
        doAnswer { invocation ->
            // Execute the actual pre-lock JDBC read before signaling: under RR this establishes the stale snapshot.
            invocation.callRealMethod().also { workerReadConnection.countDown() }
        }.`when`(repository).connectionByGarminId("snapshot-user")
        `when`(league.raiseCurrentLeagueDistance(userId)).thenAnswer {
            leagueSnapshots.add(queries.sumIncludedDistanceByUserIdAndEndDatetimeBetween(
                userId, start.minusDays(1), start.plusDays(1)))
            true
        }
        transaction.isolationLevel = TransactionDefinition.ISOLATION_REPEATABLE_READ
        val worker = GarminInboxWorker(repository, cipher, jacksonObjectMapper(), GarminActivityMapper(),
            imports, users, configurations, mock(GarminApiClient::class.java), transaction)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit(Callable {
                imports.importGarmin(userId, batch("prior-record"), start.minusDays(1), false, receipt)
            })
            assertTrue(firstAtCompletion.await(10, TimeUnit.SECONDS))
            val next = executor.submit(Callable { worker.processOne() })
            assertTrue(workerReadConnection.await(10, TimeUnit.SECONDS))
            releaseFirst.countDown()

            assertEquals(10, first.get(10, TimeUnit.SECONDS).awardedPoint)
            assertTrue(next.get(10, TimeUnit.SECONDS))
            assertEquals("DONE", jdbc.queryForObject("SELECT status FROM activity_webhook_inbox", String::class.java))
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM user_point_history", Int::class.java))
            assertEquals(70, jdbc.queryForObject("SELECT point FROM user_point WHERE user_id = ?", Int::class.java, userId))
            assertEquals(listOf(1000L, 2000L), leagueSnapshots.toList())
            assertEquals(2000L, leagueDistance())
            assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ, transaction.isolationLevel)
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `original accepted period blocks overlap after display edit but exact adjacency remains accepted`() {
        imports.importGarmin(userId, batch("original"), start.minusDays(1), false, receipt)
        val id = jdbc.queryForObject("SELECT id FROM running_record", Long::class.java)!!
        transaction.executeWithoutResult {
            recordService.updateRecord(RunningRecordUpdateDto(
                userId = userId, runningRecordId = id, distance = 2000,
                startDatetime = start.plusHours(1), endDatetime = start.plusHours(1).plusMinutes(10),
            ), receipt)
            // Even hiding an accepted record must not release the original anti-overlap interval.
            records.findById(id).orElseThrow().isStatisticIncluded = false
        }

        assertEquals(1, imports.importBatch(userId, batch("overlap"), receipt).duplicateCount)
        assertEquals(10, imports.importBatch(userId, batch("adjacent", start.plusMinutes(10)), receipt).awardedPoint)

        transaction.executeWithoutResult {
            val original = records.findById(id).orElseThrow()
            assertEquals(start, original.originalStartDatetime)
            assertEquals(start.plusMinutes(10), original.originalEndDatetime)
            assertEquals(start.plusHours(1), original.startDatetime)
            assertEquals(1000, original.leagueDistanceOverride)
            assertEquals(start.plusMinutes(10), original.leagueEndDatetimeOverride)
        }
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM user_point_history", Int::class.java))
        assertEquals(70, jdbc.queryForObject("SELECT point FROM user_point WHERE user_id = ?", Int::class.java, userId))
        assertEquals(1000L, leagueDistance())
    }

    @Test
    fun `empty accepted interval earns nothing and does not block a later run spanning its instant`() {
        val instant = start.plusMinutes(5)
        val empty = ImportRunningBatchRequest(listOf(ImportRunningRecordRequest(
            externalId = "empty", distance = 0, durationSec = 0,
            startTimestamp = instant.toEpochSecond(), endTimestamp = instant.toEpochSecond(),
        )))

        val emptyResult = imports.importGarmin(userId, empty, start.minusDays(1), false, receipt)

        assertEquals(1, emptyResult.createdCount)
        assertEquals(0, emptyResult.awardedPoint)
        assertEquals("ACCEPTED", jdbc.queryForObject("SELECT acceptance_status FROM running_record", String::class.java))
        assertEquals(false, jdbc.queryForObject("SELECT point_awarded FROM running_record", Boolean::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_point_history", Int::class.java))
        val positiveResult = imports.importBatch(userId, batch("spanning"), receipt)
        assertEquals(0, positiveResult.duplicateCount)
        assertEquals(10, positiveResult.awardedPoint)
        assertEquals(listOf("ACCEPTED", "ACCEPTED"),
            jdbc.queryForList("SELECT acceptance_status FROM running_record ORDER BY id", String::class.java))
        assertEquals(listOf(false, true),
            jdbc.queryForList("SELECT point_awarded FROM running_record ORDER BY id", Boolean::class.java))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_point_history", Int::class.java))
        assertEquals(60, jdbc.queryForObject("SELECT point FROM user_point WHERE user_id = ?", Int::class.java, userId))
        assertEquals(1000L, leagueDistance())
    }

    @Test
    fun `recorded import replaces a personal interval while retaining its provider identity`() {
        imports.importBatch(userId, batch("manual", method = "MANUAL"), receipt)
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_point_history", Int::class.java))

        val result = imports.importGarmin(userId, batch("recorded"), start.minusDays(1), false, receipt)

        assertEquals(10, result.awardedPoint)
        assertEquals(listOf("DUPLICATE", "ACCEPTED"),
            jdbc.queryForList("SELECT acceptance_status FROM running_record ORDER BY id", String::class.java))
        assertEquals("manual", jdbc.queryForObject("SELECT external_id FROM running_record WHERE source = 'HEALTH'", String::class.java))
        assertEquals(false, jdbc.queryForObject("SELECT is_statistic_included FROM running_record WHERE source = 'HEALTH'", Boolean::class.java))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_point_history", Int::class.java))
        assertEquals(1000L, leagueDistance())
    }

    @Test
    fun `point history persistence failure rolls back acceptance personal demotion balance and Health cursor`() {
        transaction.executeWithoutResult {
            val personal = RunningRecord(user = em.getReference(User::class.java, userId),
                distance = 1000, durationSec = 600, source = RunningRecordSource.GARMIN,
                externalId = "manual-before-failure", startDatetime = start, endDatetime = start.plusMinutes(10))
            personal.finishAcceptance(RunningAcceptanceStatus.PERSONAL_ONLY, false)
            em.persist(personal)
        }
        val cursor = jdbc.queryForObject("SELECT health_import_last_synced_at FROM user_configuration WHERE user_id = ?",
            java.sql.Timestamp::class.java, userId)
        jdbc.execute("""CREATE TRIGGER reject_test_point_history BEFORE INSERT ON user_point_history
            FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Simulated point persistence failure'""")

        assertFails { imports.importBatch(userId, batch("rejected"), receipt) }

        assertEquals(listOf("PERSONAL_ONLY"),
            jdbc.queryForList("SELECT acceptance_status FROM running_record", String::class.java))
        assertEquals(true, jdbc.queryForObject("SELECT is_statistic_included FROM running_record", Boolean::class.java))
        assertEquals("manual-before-failure", jdbc.queryForObject("SELECT external_id FROM running_record", String::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_point_history", Int::class.java))
        assertEquals(50, jdbc.queryForObject("SELECT point FROM user_point WHERE user_id = ?", Int::class.java, userId))
        assertEquals(cursor, jdbc.queryForObject("SELECT health_import_last_synced_at FROM user_configuration WHERE user_id = ?",
            java.sql.Timestamp::class.java, userId))
        assertEquals(0L, leagueDistance())
    }

    private fun installGarminTables() {
        val migration = Files.readString(Path.of("src/main/resources/db/migration/V1.7.0__create_garmin_integration.sql"))
        // Hibernate already created the running-record columns; use the migration's actual provider tables.
        migration.substringAfter(';').split(';').filter { it.isNotBlank() }.forEach(jdbc::execute)
    }

    private fun batch(id: String, at: OffsetDateTime = start, method: String = "RECORDED") =
        ImportRunningBatchRequest(listOf(ImportRunningRecordRequest(
            externalId = id, distance = 1000, durationSec = 600,
            startTimestamp = at.toEpochSecond(), endTimestamp = at.plusMinutes(10).toEpochSecond(),
            recordingMethod = method, sourceBundleId = "test.recording.source",
        )))

    private fun leagueDistance(): Long = transaction.execute {
        queries.sumIncludedDistanceByUserIdAndEndDatetimeBetween(userId, start.minusDays(1), start.plusDays(1))
    }!!
}

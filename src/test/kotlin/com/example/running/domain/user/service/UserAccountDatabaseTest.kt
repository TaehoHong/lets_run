package com.example.running.domain.user.service

import com.example.running.domain.auth.entity.OAuthToken
import com.example.running.domain.auth.repository.OAuthTokenRepository
import com.example.running.domain.auth.service.OAuthTokenService
import com.example.running.domain.common.enums.AccountTypeName
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.entity.UserAccount
import com.example.running.domain.user.repository.QUserRepositoryImpl
import com.example.running.domain.user.repository.UserAccountRepository
import com.example.running.domain.user.repository.UserAccountRepositoryImpl
import com.example.running.domain.user.repository.UserRepository
import com.example.running.exception.ApiException
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
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Requires an existing isolated localhost test schema with the application tables; performs DML only. */
@EnabledIfEnvironmentVariable(named = "USER_ACCOUNT_TEST_DB_URL", matches = ".+")
class UserAccountDatabaseTest {
    private lateinit var jdbc: JdbcTemplate
    private lateinit var factory: LocalContainerEntityManagerFactoryBean
    private lateinit var em: EntityManager
    private lateinit var transaction: TransactionTemplate
    private lateinit var service: UserAccountService
    private lateinit var tokens: OAuthTokenService
    private var userId: Long = 0
    private var firstId: Long = 0
    private var secondId: Long = 0

    @BeforeEach
    fun setup() {
        val url = System.getenv("USER_ACCOUNT_TEST_DB_URL")
        require(url.matches(Regex("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/(?:test_[a-z0-9_]+|[a-z0-9_]+_test)")))
        require(!url.substringBeforeLast('/').endsWith(":3306") && !url.substringBeforeLast('/').endsWith(":3395")) {
            "Use an isolated test server port and an existing test schema"
        }
        val source = DriverManagerDataSource(url, System.getenv("USER_ACCOUNT_TEST_DB_USERNAME") ?: "root",
            System.getenv("USER_ACCOUNT_TEST_DB_PASSWORD") ?: "")
        jdbc = JdbcTemplate(source)
        factory = LocalContainerEntityManagerFactoryBean().apply {
            dataSource = source
            setPackagesToScan("com.example.running.domain")
            jpaVendorAdapter = HibernateJpaVendorAdapter()
            setJpaPropertyMap(mapOf(
                "hibernate.hbm2ddl.auto" to "none",
                "hibernate.jdbc.time_zone" to "UTC",
                "hibernate.physical_naming_strategy" to "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy",
            ))
        }
        factory.afterPropertiesSet()
        val emf = checkNotNull(factory.`object`)
        em = SharedEntityManagerCreator.createSharedEntityManager(emf)
        val manager = JpaTransactionManager(emf)
        transaction = TransactionTemplate(manager).apply {
            isolationLevel = TransactionDefinition.ISOLATION_REPEATABLE_READ
        }
        val queries = JPAQueryFactory(em)
        val repositories = JpaRepositoryFactory(em)
        val users = repositories.getRepository(UserRepository::class.java,
            RepositoryFragments.just(QUserRepositoryImpl(queries)))
        val accounts = repositories.getRepository(UserAccountRepository::class.java,
            RepositoryFragments.just(UserAccountRepositoryImpl(queries)))
        tokens = spy(OAuthTokenService(repositories.getRepository(OAuthTokenRepository::class.java)))
        val target = UserAccountService(mock(PasswordEncoder::class.java), accounts, tokens, users)
        service = ProxyFactory(target).apply {
            isProxyTargetClass = true
            addAdvice(TransactionInterceptor().apply {
                transactionManager = manager
                transactionAttributeSource = AnnotationTransactionAttributeSource()
            })
        }.proxy as UserAccountService
        transaction.executeWithoutResult {
            val user = User(0)
            em.persist(user)
            em.flush()
            userId = user.id
            val first = UserAccount(user, AccountTypeName.GOOGLE.id, "first-$userId@example.test", null)
            val second = UserAccount(user, AccountTypeName.APPLE.id, "second-$userId@example.test", null)
            em.persist(first)
            em.persist(second)
            em.flush()
            firstId = first.id
            secondId = second.id
            em.persist(OAuthToken(first, "test-first-token"))
            em.persist(OAuthToken(second, "test-second-token"))
        }
    }

    @AfterEach
    fun cleanup() {
        try {
            if (userId != 0L) {
                jdbc.update("DELETE FROM oauth_token WHERE user_account_id IN (?, ?)", firstId, secondId)
                jdbc.update("DELETE FROM user_account WHERE user_id = ?", userId)
                jdbc.update("DELETE FROM user WHERE id = ?", userId)
            }
        } finally {
            if (::factory.isInitialized) factory.destroy()
        }
    }

    @Test
    fun `concurrent deletions preserve one login and OAuth token even with a stale repeatable read snapshot`() {
        val firstDeleting = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondReadOldAccounts = CountDownLatch(1)
        doAnswer { invocation ->
            invocation.callRealMethod()
            firstDeleting.countDown()
            check(releaseFirst.await(10, TimeUnit.SECONDS))
            null
        }.`when`(tokens).deleteByUserAccountId(firstId)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit(Callable { service.hardDelete(firstId, userId) })
            assertTrue(firstDeleting.await(10, TimeUnit.SECONDS))
            val second = executor.submit(Callable {
                assertFailsWith<ApiException> {
                    transaction.executeWithoutResult {
                        // Establish RR's old snapshot before waiting on the user's production DB lock.
                        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE user_id = ?",
                            Int::class.java, userId))
                        secondReadOldAccounts.countDown()
                        service.hardDelete(secondId, userId)
                    }
                }.code
            })
            assertTrue(secondReadOldAccounts.await(10, TimeUnit.SECONDS))
            releaseFirst.countDown()

            first.get(10, TimeUnit.SECONDS)
            assertEquals("LAST_ACCOUNT_CANNOT_DISCONNECT", second.get(10, TimeUnit.SECONDS))
            assertEquals(listOf(secondId), jdbc.queryForList(
                "SELECT id FROM user_account WHERE user_id = ? AND is_enabled = 1 AND is_deleted = 0",
                Long::class.java, userId))
            assertEquals(listOf(secondId), jdbc.queryForList(
                "SELECT user_account_id FROM oauth_token WHERE user_account_id IN (?, ?)",
                Long::class.java, firstId, secondId))
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }
}

package com.clang.session.service

import com.clang.session.IntegrationTest
import com.clang.session.domain.PersistentToken
import com.clang.session.domain.User
import com.clang.session.repository.PersistentTokenRepository
import com.clang.session.repository.UserRepository
import com.clang.session.repository.search.UserSearchRepository
import org.apache.commons.lang3.RandomStringUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.data.auditing.AuditingHandler
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.transaction.annotation.Transactional
import tech.jhipster.security.RandomUtil
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAccessor
import java.util.Optional
import kotlin.test.assertNotNull

private const val DEFAULT_LOGIN = "johndoe"
private const val DEFAULT_EMAIL = "johndoe@localhost"
private const val DEFAULT_FIRSTNAME = "john"
private const val DEFAULT_LASTNAME = "doe"
private const val DEFAULT_IMAGEURL = "http://placehold.it/50x50"
private const val DEFAULT_LANGKEY = "dummy"

/**
 * Integration tests for [UserService].
 */
@IntegrationTest
@Transactional
class UserServiceIT {

    @Autowired
    private lateinit var persistentTokenRepository: PersistentTokenRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userService: UserService

    /**
     * This repository is mocked in the com.clang.session.repository.search test package.
     *
     * @see com.clang.session.repository.search.UserSearchRepositoryMockConfiguration
     */
    @SpyBean
    private lateinit var spiedUserSearchRepository: UserSearchRepository

    @Autowired
    private lateinit var auditingHandler: AuditingHandler

    @MockBean
    private lateinit var dateTimeProvider: DateTimeProvider

    private lateinit var user: User

    @BeforeEach
    fun init() {
        persistentTokenRepository.deleteAll()
        user = User(
            login = DEFAULT_LOGIN,
            password = RandomStringUtils.randomAlphanumeric(60),
            activated = true,
            email = DEFAULT_EMAIL,
            firstName = DEFAULT_FIRSTNAME,
            lastName = DEFAULT_LASTNAME,
            imageUrl = DEFAULT_IMAGEURL,
            langKey = DEFAULT_LANGKEY
        )

        `when`<Optional<TemporalAccessor>>(dateTimeProvider.now).thenReturn(Optional.of(LocalDateTime.now()))
        auditingHandler.setDateTimeProvider(dateTimeProvider)
    }

    @Test
    @Transactional
    fun testRemoveOldPersistentTokens() {
        userRepository.saveAndFlush(user)
        val existingCount = persistentTokenRepository.findByUser(user).size
        val today = LocalDate.now()
        generateUserToken(user, "1111-1111", today)
        generateUserToken(user, "2222-2222", today.minusDays(32))
        assertThat(persistentTokenRepository.findByUser(user)).hasSize(existingCount + 2)
        userService.removeOldPersistentTokens()
        assertThat(persistentTokenRepository.findByUser(user)).hasSize(existingCount + 1)
    }

    @Test
    @Transactional
    fun assertThatUserMustExistToResetPassword() {
        userRepository.saveAndFlush(user)
        var maybeUser = userService.requestPasswordReset("invalid.login@localhost")
        assertThat(maybeUser).isNotPresent

        maybeUser = userService.requestPasswordReset(user.email!!)
        assertThat(maybeUser).isPresent
        assertThat(maybeUser.orElse(null).email).isEqualTo(user.email)
        assertThat(maybeUser.orElse(null).resetDate).isNotNull()
        assertThat(maybeUser.orElse(null).resetKey).isNotNull()
    }

    @Test
    @Transactional
    fun assertThatOnlyActivatedUserCanRequestPasswordReset() {
        user.activated = false
        userRepository.saveAndFlush(user)

        val maybeUser = userService.requestPasswordReset(user.login!!)
        assertThat(maybeUser).isNotPresent
        userRepository.delete(user)
    }

    @Test
    @Transactional
    fun assertThatResetKeyMustNotBeOlderThan24Hours() {
        val daysAgo = Instant.now().minus(25, ChronoUnit.HOURS)
        val resetKey = RandomUtil.generateResetKey()
        user.activated = true
        user.resetDate = daysAgo
        user.resetKey = resetKey
        userRepository.saveAndFlush(user)

        val maybeUser = userService.completePasswordReset("johndoe2", user.resetKey!!)
        assertThat(maybeUser).isNotPresent
        userRepository.delete(user)
    }

    @Test
    @Transactional
    fun assertThatResetKeyMustBeValid() {
        val daysAgo = Instant.now().minus(25, ChronoUnit.HOURS)
        user.activated = true
        user.resetDate = daysAgo
        user.resetKey = "1234"
        userRepository.saveAndFlush(user)

        val maybeUser = userService.completePasswordReset("johndoe2", user.resetKey!!)
        assertThat(maybeUser).isNotPresent
        userRepository.delete(user)
    }

    @Test
    @Transactional
    fun assertThatUserCanResetPassword() {
        val oldPassword = user.password
        val daysAgo = Instant.now().minus(2, ChronoUnit.HOURS)
        val resetKey = RandomUtil.generateResetKey()
        user.activated = true
        user.resetDate = daysAgo
        user.resetKey = resetKey
        userRepository.saveAndFlush(user)

        val maybeUser = userService.completePasswordReset("johndoe2", user.resetKey!!)
        assertThat(maybeUser).isPresent
        assertThat(maybeUser.orElse(null).resetDate).isNull()
        assertThat(maybeUser.orElse(null).resetKey).isNull()
        assertThat(maybeUser.orElse(null).password).isNotEqualTo(oldPassword)

        userRepository.delete(user)
    }

    @Test
    @Transactional
    fun assertThatNotActivatedUsersWithNotNullActivationKeyCreatedBefore3DaysAreDeleted() {
        val now = Instant.now()
        `when`<Optional<TemporalAccessor>>(dateTimeProvider.now).thenReturn(Optional.of(now.minus(4, ChronoUnit.DAYS)))
        user.activated = false
        user.activationKey = RandomStringUtils.random(20)
        val dbUser = userRepository.saveAndFlush(user)
        assertNotNull(dbUser)
        dbUser.createdDate = now.minus(4, ChronoUnit.DAYS)
        userRepository.saveAndFlush(user)
        val threeDaysAgo = now.minus(3, ChronoUnit.DAYS)
        var users = userRepository.findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(
            threeDaysAgo
        )
        assertThat(users).isNotEmpty
        userService.removeNotActivatedUsers()
        users =
            userRepository.findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(
                threeDaysAgo
            )
        assertThat(users).isEmpty()

        // Verify Elasticsearch mock
        verify(spiedUserSearchRepository, times(1)).delete(user)
    }

    @Test
    @Transactional
    fun assertThatNotActivatedUsersWithNullActivationKeyCreatedBefore3DaysAreNotDeleted() {
        val now = Instant.now()
        `when`(dateTimeProvider.getNow()).thenReturn(Optional.of(now.minus(4, ChronoUnit.DAYS)))
        user.activated = false
        val dbUser = userRepository.saveAndFlush(user)
        dbUser.createdDate = now.minus(4, ChronoUnit.DAYS)
        userRepository.saveAndFlush(user)
        val threeDaysAgo = now.minus(3, ChronoUnit.DAYS)
        val users =
            userRepository.findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(
                threeDaysAgo
            )
        assertThat(users).isEmpty()
        userService.removeNotActivatedUsers()
        val maybeDbUser = userRepository.findById(dbUser.id)
        assertThat(maybeDbUser).contains(dbUser)

        // Verify Elasticsearch mock
        verify(spiedUserSearchRepository, never()).delete(user)
    }

    private fun generateUserToken(user: User, tokenSeries: String, localDate: LocalDate) {
        val token = PersistentToken(
            series = tokenSeries,
            user = user,
            tokenValue = "$tokenSeries-data",
            tokenDate = localDate,
            ipAddress = "127.0.0.1",
            userAgent = "Test agent"
        )
        persistentTokenRepository.saveAndFlush(token)
    }
}

package app.trainer.backend.auth.password

import app.trainer.backend.auth.AuthProperties
import app.trainer.backend.auth.AuthTokensResponse
import app.trainer.backend.auth.SessionOpener
import app.trainer.backend.auth.SessionService
import app.trainer.backend.config.FieldConflictException
import app.trainer.backend.config.TooManyAttemptsException
import app.trainer.backend.user.UserEntity
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val SIGN_IN_REJECTED = "Неверная почта, логин или пароль"
private const val EMAIL_FIELD = "email"
private const val LOGIN_FIELD = "login"

@Service
class PasswordAuthService(
    private val userRepository: UserRepository,
    private val passwordStore: PasswordStore,
    private val sessionService: SessionService,
    private val sessionOpener: SessionOpener,
    private val properties: AuthProperties,
    private val clock: Clock,
) {

    @Transactional
    fun signUp(request: PasswordSignUpRequest): AuthTokensResponse {
        val email = requireFreeEmail(raw = request.email, ownerId = null)
        val login = request.login?.takeIf { it.isNotBlank() }?.let { requireFreeLogin(raw = it, ownerId = null) }
        requireAcceptablePassword(request.password)

        val now = Instant.now(clock)
        val user = userRepository.save(
            UserEntity(
                id = UUID.randomUUID(),
                displayName = request.displayName.trim(),
                phone = null,
                email = email,
                login = login,
                createdAt = now,
            )
        )
        passwordStore.save(userId = user.id, password = request.password, now = now)
        return sessionOpener.openSession(userId = user.id, deviceInfo = request.deviceInfo)
    }

    @Transactional(noRollbackFor = [ResponseStatusException::class, TooManyAttemptsException::class])
    fun signIn(request: PasswordSignInRequest): AuthTokensResponse {
        val now = Instant.now(clock)
        val credential = credentialOf(request.identifier)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, SIGN_IN_REJECTED)
        requireUnlocked(credential = credential, now = now)

        if (!passwordStore.matches(credential = credential, password = request.password)) {
            registerFailedAttempt(credential = credential, now = now)
            requireUnlocked(credential = credential, now = now)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, SIGN_IN_REJECTED)
        }

        credential.failedAttempts = 0
        credential.lockedUntil = null
        credential.lockStreak = 0
        return sessionOpener.openSession(userId = credential.userId, deviceInfo = request.deviceInfo)
    }

    @Transactional
    fun setPassword(userId: UUID, currentSessionId: UUID?, request: SetPasswordRequest) {
        requireAcceptablePassword(request.newPassword)
        val user = userRepository.findByIdOrNull(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден")
        val credential = passwordStore.credentialOf(userId)

        if (credential == null) {
            adoptIdentifiers(user = user, request = request)
        } else {
            requireCurrentPassword(credential = credential, provided = request.currentPassword)
        }
        passwordStore.save(userId = userId, password = request.newPassword, now = Instant.now(clock))
        sessionService.revokeOtherSessions(userId = userId, currentSessionId = currentSessionId)
    }

    private fun adoptIdentifiers(user: UserEntity, request: SetPasswordRequest) {
        val requestedEmail = request.email?.takeIf { it.isNotBlank() }
        if (user.email == null && requestedEmail == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите почту — ею вы будете входить")
        }
        if (requestedEmail != null) {
            user.email = requireFreeEmail(raw = requestedEmail, ownerId = user.id)
        }
        request.login?.takeIf { it.isNotBlank() }?.let { user.login = requireFreeLogin(raw = it, ownerId = user.id) }
    }

    private fun requireCurrentPassword(credential: PasswordCredentialEntity, provided: String?) {
        val matches = provided != null && passwordStore.matches(credential = credential, password = provided)
        if (!matches) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Текущий пароль неверен")
        }
    }

    private fun credentialOf(identifier: String): PasswordCredentialEntity? {
        val normalized = normalizedIdentifier(identifier)
        val user = userRepository.findByEmail(normalized) ?: userRepository.findByLogin(normalized) ?: return null
        return passwordStore.credentialOf(user.id)
    }

    private fun requireUnlocked(credential: PasswordCredentialEntity, now: Instant) {
        val lockedUntil = credential.lockedUntil ?: return
        if (lockedUntil.isBefore(now)) return
        val secondsLeft = ChronoUnit.SECONDS.between(now, lockedUntil)
        throw TooManyAttemptsException(
            retryAfterSeconds = secondsLeft,
            explanation = "Вход закрыт после нескольких неудачных попыток",
        )
    }

    private fun registerFailedAttempt(credential: PasswordCredentialEntity, now: Instant) {
        credential.failedAttempts += 1
        if (credential.failedAttempts < properties.passwordMaxFailedAttempts) return
        credential.failedAttempts = 0
        credential.lockStreak += 1
        credential.lockedUntil = now.plus(lockMinutesAfter(credential.lockStreak), ChronoUnit.MINUTES)
    }

    private fun lockMinutesAfter(streak: Int): Long {
        var minutes = properties.passwordLockMinutes
        repeat(streak - 1) {
            minutes *= 2
            if (minutes >= properties.passwordLockMaxMinutes) return properties.passwordLockMaxMinutes
        }
        return minutes
    }

    private fun requireFreeEmail(raw: String, ownerId: UUID?): String {
        val email = normalizedEmailOrNull(raw)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Проверьте адрес почты")
        val taken = userRepository.findByEmail(email)
        if (taken != null && taken.id != ownerId) {
            throw FieldConflictException(field = EMAIL_FIELD, explanation = "Эта почта уже занята")
        }
        return email
    }

    private fun requireFreeLogin(raw: String, ownerId: UUID?): String {
        val login = normalizedLoginOrNull(raw)
            ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Логин: латинские буквы, цифры, точка, дефис или подчёркивание, от 3 до 32 символов",
            )
        val taken = userRepository.findByLogin(login)
        if (taken != null && taken.id != ownerId) {
            throw FieldConflictException(field = LOGIN_FIELD, explanation = "Этот логин уже занят")
        }
        return login
    }
}

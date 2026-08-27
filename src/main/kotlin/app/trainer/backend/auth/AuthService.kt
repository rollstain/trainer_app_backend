package app.trainer.backend.auth

import app.trainer.backend.chat.ChatService
import app.trainer.backend.coach.CoachClientEntity
import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.user.UserEntity
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class AuthService(
    private val inviteRepository: InviteRepository,
    private val deviceSessionRepository: DeviceSessionRepository,
    private val userRepository: UserRepository,
    private val coachRepository: CoachRepository,
    private val coachClientRepository: CoachClientRepository,
    private val chatService: ChatService,
    private val tokenService: TokenService,
    private val inviteCodeGenerator: InviteCodeGenerator,
    private val properties: AuthProperties,
    private val clock: Clock,
) {

    @Transactional
    fun createInvite(coachUserId: UUID): InviteResponse {
        val coach = coachRepository.findByUserId(coachUserId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Только тренер может создавать приглашения")
        val now = Instant.now(clock)
        val invite = InviteEntity(
            id = UUID.randomUUID(),
            coachId = coach.id,
            targetUserId = null,
            code = inviteCodeGenerator.nextUnusedCode(),
            expiresAt = now.plus(properties.inviteTtlHours, ChronoUnit.HOURS),
            usedAt = null,
            usedByUserId = null,
            createdAt = now,
        )
        inviteRepository.save(invite)
        return InviteResponse(code = invite.code, expiresAt = invite.expiresAt)
    }

    @Transactional
    fun redeemInvite(request: RedeemInviteRequest): AuthTokensResponse {
        val now = Instant.now(clock)
        val invite = inviteRepository.findByCode(request.code.uppercase())
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Приглашение не найдено")
        if (invite.usedAt != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Приглашение уже использовано")
        }
        if (invite.expiresAt.isBefore(now)) {
            throw ResponseStatusException(HttpStatus.GONE, "Срок приглашения истёк")
        }

        val existingUserId = invite.targetUserId
        val userId = if (existingUserId != null) {
            existingUserId
        } else {
            joinAsNewClient(invite = invite, displayName = request.displayName, now = now)
        }

        invite.usedAt = now
        invite.usedByUserId = userId

        return openSession(userId = userId, deviceInfo = request.deviceInfo)
    }

    private fun joinAsNewClient(invite: InviteEntity, displayName: String?, now: Instant): UUID {
        val name = displayName?.trim().orEmpty()
        if (name.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Не указано имя")
        }
        val user = userRepository.save(
            UserEntity(
                id = UUID.randomUUID(),
                displayName = name,
                phone = null,
                email = null,
                createdAt = now,
            )
        )
        coachClientRepository.save(
            CoachClientEntity(
                id = UUID.randomUUID(),
                coachId = invite.coachId,
                userId = user.id,
                status = CoachClientStatus.ACTIVE,
                createdAt = now,
            )
        )
        chatService.openDialog(coachId = invite.coachId, clientUserId = user.id)
        return user.id
    }

    @Transactional
    fun refresh(request: RefreshRequest): AuthTokensResponse {
        val now = Instant.now(clock)
        val session = deviceSessionRepository.findByRefreshTokenHash(tokenService.hash(request.refreshToken))
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Сессия не найдена")
        if (session.revokedAt != null) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Сессия отозвана")
        }
        val sessionExpiresAt = session.createdAt.plus(properties.refreshTokenTtlDays, ChronoUnit.DAYS)
        if (sessionExpiresAt.isBefore(now)) {
            session.revokedAt = now
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Срок сессии истёк")
        }

        val refreshToken = tokenService.generateRefreshToken()
        session.refreshTokenHash = tokenService.hash(refreshToken)
        session.lastSeenAt = now

        val accessToken = tokenService.issueAccessToken(session.userId)
        return AuthTokensResponse(
            accessToken = accessToken.value,
            refreshToken = refreshToken,
            accessTokenExpiresAt = accessToken.expiresAt,
        )
    }

    private fun openSession(userId: UUID, deviceInfo: String): AuthTokensResponse {
        val now = Instant.now(clock)
        val refreshToken = tokenService.generateRefreshToken()
        deviceSessionRepository.save(
            DeviceSessionEntity(
                id = UUID.randomUUID(),
                userId = userId,
                refreshTokenHash = tokenService.hash(refreshToken),
                deviceInfo = deviceInfo,
                createdAt = now,
                lastSeenAt = now,
                revokedAt = null,
            )
        )
        val accessToken = tokenService.issueAccessToken(userId)
        return AuthTokensResponse(
            accessToken = accessToken.value,
            refreshToken = refreshToken,
            accessTokenExpiresAt = accessToken.expiresAt,
        )
    }
}

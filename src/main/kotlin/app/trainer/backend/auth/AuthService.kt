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
    private val userRepository: UserRepository,
    private val coachRepository: CoachRepository,
    private val coachClientRepository: CoachClientRepository,
    private val chatService: ChatService,
    private val sessionOpener: SessionOpener,
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

    @Transactional(readOnly = true)
    fun previewInvite(code: String): InvitePreviewResponse {
        val invite = requireUsableInvite(code = code, now = Instant.now(clock))
        val coach = coachRepository.findById(invite.coachId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Тренер не найден")
        }
        val coachUser = userRepository.findById(coach.userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Тренер не найден")
        }
        return InvitePreviewResponse(
            coachDisplayName = coachUser.displayName,
            expiresAt = invite.expiresAt,
            needsDisplayName = invite.targetUserId == null,
        )
    }

    @Transactional
    fun redeemInvite(request: RedeemInviteRequest): AuthTokensResponse {
        val now = Instant.now(clock)
        val invite = requireUsableInvite(code = request.code, now = now)

        val existingUserId = invite.targetUserId
        val userId = if (existingUserId != null) {
            existingUserId
        } else {
            joinAsNewClient(invite = invite, displayName = request.displayName, now = now)
        }

        invite.usedAt = now
        invite.usedByUserId = userId

        return sessionOpener.openSession(userId = userId, deviceInfo = request.deviceInfo)
    }

    @Transactional
    fun joinCoachByCode(userId: UUID, code: String) {
        val now = Instant.now(clock)
        val invite = requireUsableInvite(code = code, now = now)
        if (invite.targetUserId != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Этот код выдан для входа, а не для приглашения")
        }
        val existingLink = coachClientRepository.findByCoachIdAndUserId(coachId = invite.coachId, userId = userId)
        if (existingLink != null) {
            existingLink.status = CoachClientStatus.ACTIVE
        } else {
            coachClientRepository.save(
                CoachClientEntity(
                    id = UUID.randomUUID(),
                    coachId = invite.coachId,
                    userId = userId,
                    status = CoachClientStatus.ACTIVE,
                    createdAt = now,
                )
            )
            chatService.openDialog(coachId = invite.coachId, clientUserId = userId)
        }
        invite.usedAt = now
        invite.usedByUserId = userId
    }

    private fun requireUsableInvite(code: String, now: Instant): InviteEntity {
        val invite = inviteRepository.findByCode(code.uppercase())
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Приглашение не найдено")
        if (invite.usedAt != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Приглашение уже использовано")
        }
        if (invite.expiresAt.isBefore(now)) {
            throw ResponseStatusException(HttpStatus.GONE, "Срок приглашения истёк")
        }
        return invite
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
                login = null,
                isOwner = false,
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
}

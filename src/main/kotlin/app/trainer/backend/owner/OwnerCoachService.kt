package app.trainer.backend.owner

import app.trainer.backend.auth.external.ExternalIdentityRepository
import app.trainer.backend.auth.password.PasswordStore
import app.trainer.backend.config.EXTRA_ROW_TO_DETECT_NEXT_PAGE
import app.trainer.backend.config.Page
import app.trainer.backend.config.PageCursor
import app.trainer.backend.config.decodeCursor
import app.trainer.backend.config.encodeCursor
import app.trainer.backend.config.pageSizeOf
import app.trainer.backend.user.UserRepository
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val COACHES_PER_PAGE = 50

@Service
class OwnerCoachService(
    private val userRepository: UserRepository,
    private val ownerCoachRepository: OwnerCoachRepository,
    private val externalIdentityRepository: ExternalIdentityRepository,
    private val passwordStore: PasswordStore,
) {

    @Transactional(readOnly = true)
    fun coaches(ownerUserId: UUID, limit: Int?, after: String?): Page<OwnerCoachResponse> {
        requireOwner(ownerUserId)
        val pageSize = pageSizeOf(limit) ?: COACHES_PER_PAGE
        val cursor = decodeCursor(after)
        val fetched = ownerCoachRepository.findPage(
            afterCreatedAt = cursor?.sortKey,
            afterId = cursor?.id,
            pageSize = pageSize + EXTRA_ROW_TO_DETECT_NEXT_PAGE,
        )
        val rows = fetched.take(pageSize)
        val hasMore = fetched.size > pageSize
        val last = rows.lastOrNull()?.takeIf { hasMore }
        return Page(
            items = rows.map {
                OwnerCoachResponse(
                    coachId = it.getCoachId(),
                    displayName = it.getDisplayName(),
                    createdAt = it.getCreatedAt(),
                    activeClients = it.getActiveClients().toInt(),
                    isOwner = it.getOwner(),
                )
            },
            nextCursor = last?.let {
                encodeCursor(PageCursor(sortKey = it.getCreatedAt().toString(), id = it.getCoachId()))
            },
        )
    }

    @Transactional(readOnly = true)
    fun card(ownerUserId: UUID, coachId: UUID): OwnerCoachCardResponse {
        requireOwner(ownerUserId)
        val row = ownerCoachRepository.findCard(coachId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Тренер не найден")
        val userId = row.getUserId()
        return OwnerCoachCardResponse(
            coachId = row.getCoachId(),
            displayName = row.getDisplayName(),
            email = row.getEmail(),
            phone = row.getPhone(),
            login = row.getLogin(),
            zoneId = row.getZoneId(),
            createdAt = row.getCreatedAt(),
            activeClients = row.getActiveClients().toInt(),
            archivedClients = row.getArchivedClients().toInt(),
            lastSeenAt = row.getLastSeenAt(),
            hasPassword = passwordStore.credentialOf(userId) != null,
            providers = externalIdentityRepository.findByUserId(userId).map { it.provider.name }.sorted(),
            isOwner = row.getOwner(),
        )
    }

    private fun requireOwner(userId: UUID) {
        val user = userRepository.findByIdOrNull(userId)
        if (user == null || !user.isOwner) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Список тренеров виден владельцу")
        }
    }
}

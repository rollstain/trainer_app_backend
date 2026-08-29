package app.trainer.backend.owner

import java.time.Instant
import java.util.UUID

data class OwnerCoachResponse(
    val coachId: UUID,
    val displayName: String,
    val createdAt: Instant,
    val activeClients: Int,
    val isOwner: Boolean,
)

data class OwnerCoachCardResponse(
    val coachId: UUID,
    val displayName: String,
    val email: String?,
    val phone: String?,
    val login: String?,
    val zoneId: String,
    val createdAt: Instant,
    val activeClients: Int,
    val archivedClients: Int,
    val lastSeenAt: Instant?,
    val hasPassword: Boolean,
    val providers: List<String>,
    val isOwner: Boolean,
)

package app.trainer.backend.auth.external

import java.util.UUID

data class ConfirmedTelegramLogin(
    val targetUserId: UUID?,
    val identity: VerifiedIdentity?,
)

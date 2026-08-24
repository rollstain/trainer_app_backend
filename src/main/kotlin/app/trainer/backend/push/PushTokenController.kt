package app.trainer.backend.push

import app.trainer.backend.config.CurrentUserId
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class RegisterPushTokenRequest(
    @field:NotBlank
    val token: String,
    val platform: PushPlatform,
)

@Service
class PushTokenService(
    private val tokenRepository: PushTokenRepository,
    private val clock: Clock,
) {

    @Transactional
    fun register(userId: UUID, request: RegisterPushTokenRequest) {
        val now = Instant.now(clock)
        val existing = tokenRepository.findByToken(request.token)
        if (existing == null) {
            tokenRepository.save(
                PushTokenEntity(
                    id = UUID.randomUUID(),
                    userId = userId,
                    platform = request.platform,
                    token = request.token,
                    updatedAt = now,
                )
            )
            return
        }
        existing.userId = userId
        existing.platform = request.platform
        existing.updatedAt = now
    }

    @Transactional
    fun unregister(token: String) {
        tokenRepository.deleteByToken(token)
    }
}

@RestController
@RequestMapping("/me/push-tokens")
class PushTokenController(private val pushTokenService: PushTokenService) {

    @PostMapping
    fun register(
        @CurrentUserId userId: UUID,
        @Valid @RequestBody request: RegisterPushTokenRequest,
    ) {
        pushTokenService.register(userId = userId, request = request)
    }

    @DeleteMapping
    fun unregister(@RequestBody request: RegisterPushTokenRequest) {
        pushTokenService.unregister(token = request.token)
    }
}

package app.trainer.backend.auth.external

import app.trainer.backend.auth.AuthTokensResponse
import app.trainer.backend.auth.SessionOpener
import app.trainer.backend.user.UserEntity
import app.trainer.backend.user.UserRepository
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val SUBJECT_SEPARATOR = ":"
private const val ONLY_WAY_IN = 1

@Service
class ExternalAuthService(
    private val identityRepository: ExternalIdentityRepository,
    private val userRepository: UserRepository,
    private val sessionOpener: SessionOpener,
    verifiers: List<ExternalIdentityVerifier>,
    private val clock: Clock,
) {

    private val verifierByProvider = verifiers.associateBy { it.provider }

    @Transactional
    fun signIn(request: ExternalSignInRequest): AuthTokensResponse {
        val verified = verify(provider = request.provider, token = request.token)
        val known = identityRepository.findByProviderAndSubjectHash(
            provider = verified.provider,
            subjectHash = hashOf(verified),
        )
        val userId = known?.userId ?: registerUser(verified)
        return sessionOpener.openSession(userId = userId, deviceInfo = request.deviceInfo)
    }

    @Transactional
    fun link(userId: UUID, request: LinkIdentityRequest): List<LinkedIdentityResponse> {
        val verified = verify(provider = request.provider, token = request.token)
        val subjectHash = hashOf(verified)
        val owner = identityRepository.findByProviderAndSubjectHash(
            provider = verified.provider,
            subjectHash = subjectHash,
        )
        if (owner != null && owner.userId != userId) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Этот аккаунт уже привязан к другому профилю")
        }
        if (owner == null) {
            identityRepository.save(
                ExternalIdentityEntity(
                    id = UUID.randomUUID(),
                    userId = userId,
                    provider = verified.provider,
                    subjectHash = subjectHash,
                    createdAt = Instant.now(clock),
                )
            )
        }
        return linkedIdentities(userId)
    }

    @Transactional
    fun linkVerified(userId: UUID, verified: VerifiedIdentity) {
        val subjectHash = hashOf(verified)
        val owner = identityRepository.findByProviderAndSubjectHash(
            provider = verified.provider,
            subjectHash = subjectHash,
        )
        if (owner != null && owner.userId != userId) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Этот аккаунт уже привязан к другому профилю")
        }
        if (owner != null) return
        identityRepository.save(
            ExternalIdentityEntity(
                id = UUID.randomUUID(),
                userId = userId,
                provider = verified.provider,
                subjectHash = subjectHash,
                createdAt = Instant.now(clock),
            )
        )
    }

    @Transactional
    fun claimVerified(userId: UUID, verified: VerifiedIdentity) {
        val subjectHash = hashOf(verified)
        val owner = identityRepository.findByProviderAndSubjectHash(
            provider = verified.provider,
            subjectHash = subjectHash,
        )
        if (owner != null && owner.userId == userId) return
        if (owner != null) {
            identityRepository.delete(owner)
        }
        identityRepository.save(
            ExternalIdentityEntity(
                id = UUID.randomUUID(),
                userId = userId,
                provider = verified.provider,
                subjectHash = subjectHash,
                createdAt = Instant.now(clock),
            )
        )
    }

    @Transactional
    fun unlink(userId: UUID, provider: ExternalProvider): List<LinkedIdentityResponse> {
        val identities = identityRepository.findByUserId(userId)
        if (identities.size <= ONLY_WAY_IN) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Это единственный способ входа — сначала привяжите другой",
            )
        }
        val identity = identities.firstOrNull { it.provider == provider }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Такой аккаунт не привязан")
        identityRepository.delete(identity)
        return linkedIdentities(userId)
    }

    @Transactional(readOnly = true)
    fun linkedIdentities(userId: UUID): List<LinkedIdentityResponse> = identityRepository
        .findByUserId(userId)
        .map { LinkedIdentityResponse(provider = it.provider, linkedAt = it.createdAt) }

    private fun verify(provider: ExternalProvider, token: String): VerifiedIdentity {
        val verifier = verifierByProvider[provider]
            ?: throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Вход через $provider не поддерживается")
        return verifier.verify(token)
    }

    private fun registerUser(verified: VerifiedIdentity): UUID {
        val now = Instant.now(clock)
        val user = userRepository.save(
            UserEntity(
                id = UUID.randomUUID(),
                displayName = verified.displayName.orEmpty(),
                phone = null,
                email = null,
                createdAt = now,
            )
        )
        identityRepository.save(
            ExternalIdentityEntity(
                id = UUID.randomUUID(),
                userId = user.id,
                provider = verified.provider,
                subjectHash = hashOf(verified),
                createdAt = now,
            )
        )
        return user.id
    }

    private fun hashOf(verified: VerifiedIdentity): String {
        val source = verified.provider.name + SUBJECT_SEPARATOR + verified.subject
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}

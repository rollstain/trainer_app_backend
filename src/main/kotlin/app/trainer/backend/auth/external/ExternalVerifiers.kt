package app.trainer.backend.auth.external

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException

private const val YANDEX_USER_INFO_URL = "https://login.yandex.ru/info?format=json"
private const val VK_USER_INFO_URL = "https://id.vk.com/oauth2/user_info"
private const val APPLE_JWK_SET_URL = "https://appleid.apple.com/auth/keys"
private const val GOOGLE_JWK_SET_URL = "https://www.googleapis.com/oauth2/v3/certs"
private const val APPLE_ISSUER = "https://appleid.apple.com"
private const val GOOGLE_ISSUER = "https://accounts.google.com"
private const val NAME_SEPARATOR = " "

@ConfigurationProperties(prefix = "trainer.auth.external")
data class ExternalAuthProperties(
    val vkClientId: String,
    val appleClientId: String,
    val googleClientId: String,
)

private fun providerNotConfigured(provider: ExternalProvider): Nothing {
    throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Вход через $provider не настроен")
}

private fun tokenRejected(provider: ExternalProvider): Nothing {
    throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "$provider не подтвердил вход")
}

@Component
class YandexIdentityVerifier(private val restClient: RestClient) : ExternalIdentityVerifier {

    override val provider = ExternalProvider.YANDEX

    override fun verify(token: String): VerifiedIdentity {
        val info = try {
            restClient.get()
                .uri(YANDEX_USER_INFO_URL)
                .header("Authorization", "OAuth $token")
                .retrieve()
                .body(YandexUserInfo::class.java)
        } catch (failure: RestClientException) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Яндекс не подтвердил вход", failure)
        }
        val subject = info?.id ?: tokenRejected(provider)
        return VerifiedIdentity(
            provider = provider,
            subject = subject,
            displayName = info.realName?.takeIf { it.isNotBlank() } ?: info.displayName?.takeIf { it.isNotBlank() },
        )
    }

    data class YandexUserInfo(
        val id: String?,
        val displayName: String?,
        val realName: String?,
    )
}

@Component
class VkIdentityVerifier(
    private val restClient: RestClient,
    private val properties: ExternalAuthProperties,
) : ExternalIdentityVerifier {

    override val provider = ExternalProvider.VK

    override fun verify(token: String): VerifiedIdentity {
        if (properties.vkClientId.isBlank()) providerNotConfigured(provider)
        val response = try {
            restClient.post()
                .uri(VK_USER_INFO_URL)
                .body(mapOf("access_token" to token, "client_id" to properties.vkClientId))
                .retrieve()
                .body(VkUserInfoResponse::class.java)
        } catch (failure: RestClientException) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "VK не подтвердил вход", failure)
        }
        val user = response?.user ?: tokenRejected(provider)
        val subject = user.userId ?: tokenRejected(provider)
        val name = listOfNotNull(user.firstName, user.lastName)
            .filter { it.isNotBlank() }
            .joinToString(NAME_SEPARATOR)
        return VerifiedIdentity(provider = provider, subject = subject, displayName = name.ifBlank { null })
    }

    data class VkUserInfoResponse(val user: VkUser?)

    data class VkUser(val userId: String?, val firstName: String?, val lastName: String?)
}

abstract class SignedTokenVerifier(
    private val jwkSetUrl: String,
    private val issuer: String,
) : ExternalIdentityVerifier {

    protected abstract val audience: String

    private val decoder: JwtDecoder by lazy { NimbusJwtDecoder.withJwkSetUri(jwkSetUrl).build() }

    override fun verify(token: String): VerifiedIdentity {
        if (audience.isBlank()) providerNotConfigured(provider)
        val jwt = try {
            decoder.decode(token)
        } catch (failure: JwtException) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "$provider не подтвердил вход", failure)
        }
        if (jwt.issuer?.toString() != issuer || !jwt.audience.contains(audience)) tokenRejected(provider)
        val subject = jwt.subject ?: tokenRejected(provider)
        return VerifiedIdentity(
            provider = provider,
            subject = subject,
            displayName = jwt.getClaimAsString("name")?.takeIf { it.isNotBlank() },
        )
    }
}

@Component
class AppleIdentityVerifier(
    private val properties: ExternalAuthProperties,
) : SignedTokenVerifier(jwkSetUrl = APPLE_JWK_SET_URL, issuer = APPLE_ISSUER) {

    override val provider = ExternalProvider.APPLE

    override val audience: String get() = properties.appleClientId
}

@Component
class GoogleIdentityVerifier(
    private val properties: ExternalAuthProperties,
) : SignedTokenVerifier(jwkSetUrl = GOOGLE_JWK_SET_URL, issuer = GOOGLE_ISSUER) {

    override val provider = ExternalProvider.GOOGLE

    override val audience: String get() = properties.googleClientId
}

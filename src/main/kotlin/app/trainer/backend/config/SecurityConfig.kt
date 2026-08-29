package app.trainer.backend.config

import app.trainer.backend.auth.AuthProperties
import app.trainer.backend.auth.external.ExternalAuthProperties
import app.trainer.backend.auth.external.TelegramProperties
import app.trainer.backend.link.InviteLinkProperties
import com.nimbusds.jose.jwk.source.ImmutableSecret
import javax.crypto.spec.SecretKeySpec
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.web.SecurityFilterChain

private const val JWT_SECRET_MIN_LENGTH = 32
private const val HMAC_ALGORITHM = "HmacSHA256"

@Configuration
@EnableConfigurationProperties(
    AuthProperties::class,
    ExternalAuthProperties::class,
    TelegramProperties::class,
    InviteLinkProperties::class,
)
class SecurityConfig(private val properties: AuthProperties) {

    private val secretKey = SecretKeySpec(requireStrongSecret().toByteArray(), HMAC_ALGORITHM)

    @Bean
    fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(secretKey))

    @Bean
    fun jwtDecoder(): JwtDecoder = NimbusJwtDecoder
        .withSecretKey(secretKey)
        .macAlgorithm(MacAlgorithm.HS256)
        .build()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.GET, "/auth/invites/*").permitAll()
                it.requestMatchers("/auth/invites/redeem", "/auth/refresh", "/auth/external").permitAll()
                it.requestMatchers("/auth/telegram/start", "/auth/telegram/confirm").permitAll()
                it.requestMatchers("/auth/telegram/coach-request").permitAll()
                it.requestMatchers("/admin/**").permitAll()
                it.requestMatchers("/ws/**").permitAll()
                it.requestMatchers("/i/*", "/.well-known/**").permitAll()
                it.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt { } }
            .build()
    }

    private fun requireStrongSecret(): String {
        require(properties.jwtSecret.length >= JWT_SECRET_MIN_LENGTH) {
            "trainer.auth.jwt-secret должен быть не короче $JWT_SECRET_MIN_LENGTH символов"
        }
        return properties.jwtSecret
    }
}

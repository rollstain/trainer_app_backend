package app.trainer.backend.config

import app.trainer.backend.auth.AuthProperties
import app.trainer.backend.auth.REFRESH_TOKEN_COOKIE_NAME
import app.trainer.backend.auth.SESSION_TRANSPORT_HEADER
import app.trainer.backend.auth.external.ExternalAuthProperties
import app.trainer.backend.auth.external.TelegramProperties
import app.trainer.backend.legal.LegalProperties
import app.trainer.backend.link.InviteLinkProperties
import app.trainer.backend.mail.MailProperties
import com.nimbusds.jose.jwk.source.ImmutableSecret
import jakarta.servlet.http.HttpServletRequest
import java.time.Duration
import javax.crypto.spec.SecretKeySpec
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

private const val JWT_SECRET_MIN_LENGTH = 32
private const val HMAC_ALGORITHM = "HmacSHA256"
private const val BCRYPT_STRENGTH = 12
private const val PREFLIGHT_CACHE_SECONDS = 3600L
private const val CSRF_HEADER = "X-XSRF-TOKEN"
private const val CROSS_SITE_REQUESTS_FORBIDDEN = "Strict"

private val METHODS_WITHOUT_SIDE_EFFECTS = setOf("GET", "HEAD", "OPTIONS", "TRACE")

@Configuration
@EnableConfigurationProperties(
    AuthProperties::class,
    ExternalAuthProperties::class,
    TelegramProperties::class,
    InviteLinkProperties::class,
    LegalProperties::class,
    MailProperties::class,
    WebClientProperties::class,
)
class SecurityConfig(
    private val properties: AuthProperties,
    private val webClientProperties: WebClientProperties,
) {

    private val secretKey = SecretKeySpec(requireStrongSecret().toByteArray(), HMAC_ALGORITHM)

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(BCRYPT_STRENGTH)

    @Bean
    fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(secretKey))

    @Bean
    fun jwtDecoder(): JwtDecoder = NimbusJwtDecoder
        .withSecretKey(secretKey)
        .macAlgorithm(MacAlgorithm.HS256)
        .build()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/admin/**", CorsConfiguration())
        source.registerCorsConfiguration("/**", browserClientConfiguration())
        return source
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        corsConfigurationSource: CorsConfigurationSource,
        csrfTokenRepository: CookieCsrfTokenRepository,
    ): SecurityFilterChain {
        return http
            .cors { it.configurationSource(corsConfigurationSource) }
            .csrf { csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository)
                csrf.csrfTokenRequestHandler(CsrfTokenRequestAttributeHandler())
                csrf.requireCsrfProtectionMatcher(CookieAuthenticatedRequestMatcher())
            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.GET, "/auth/invites/*").permitAll()
                it.requestMatchers("/auth/invites/redeem", "/auth/refresh", "/auth/external").permitAll()
                it.requestMatchers("/auth/telegram/start", "/auth/telegram/confirm").permitAll()
                it.requestMatchers(
                    "/auth/password/sign-up",
                    "/auth/password/sign-in",
                    "/auth/password/forgot",
                    "/auth/password/reset/telegram",
                    "/auth/password/reset/email",
                    "/auth/email/confirm",
                ).permitAll()
                it.requestMatchers("/admin/**").permitAll()
                it.requestMatchers("/ws/chat").permitAll()
                it.requestMatchers("/i/*", "/r/*", "/c/*", "/legal/*", "/.well-known/**").permitAll()
                it.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                it.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt { } }
            .build()
    }

    private fun browserClientConfiguration(): CorsConfiguration {
        return CorsConfiguration().apply {
            allowedOrigins = webClientProperties.allowedOrigins
            allowedMethods = listOf(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name(),
            )
            allowedHeaders = listOf(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                SESSION_TRANSPORT_HEADER,
                CSRF_HEADER,
            )
            allowCredentials = true
            maxAge = PREFLIGHT_CACHE_SECONDS
        }
    }

    @Bean
    fun csrfTokenRepository(): CookieCsrfTokenRepository {
        val repository = CookieCsrfTokenRepository.withHttpOnlyFalse()
        val livesAsLongAsSession = Duration.ofDays(properties.refreshTokenIdleDays)
        repository.setCookieCustomizer { cookie ->
            cookie.secure(webClientProperties.cookieSecure)
            cookie.sameSite(CROSS_SITE_REQUESTS_FORBIDDEN)
            cookie.maxAge(livesAsLongAsSession)
            if (webClientProperties.cookieDomain.isNotBlank()) {
                cookie.domain(webClientProperties.cookieDomain)
            }
        }
        return repository
    }

    private fun requireStrongSecret(): String {
        require(properties.jwtSecret.length >= JWT_SECRET_MIN_LENGTH) {
            "trainer.auth.jwt-secret должен быть не короче $JWT_SECRET_MIN_LENGTH символов"
        }
        return properties.jwtSecret
    }
}

private class CookieAuthenticatedRequestMatcher : RequestMatcher {

    override fun matches(request: HttpServletRequest): Boolean {
        if (request.method in METHODS_WITHOUT_SIDE_EFFECTS) return false
        return request.cookies?.any { it.name == REFRESH_TOKEN_COOKIE_NAME } == true
    }
}

package app.trainer.backend.config

import app.trainer.backend.auth.SESSION_ID_CLAIM
import java.util.UUID
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.server.ResponseStatusException

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentSessionId

@Component
class CurrentSessionIdResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.hasParameterAnnotation(CurrentSessionId::class.java)
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): UUID? {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        val jwt = principal as? Jwt
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Запрос без токена")
        return jwt.getClaimAsString(SESSION_ID_CLAIM)?.let(UUID::fromString)
    }
}

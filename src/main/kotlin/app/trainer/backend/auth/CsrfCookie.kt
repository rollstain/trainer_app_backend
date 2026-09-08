package app.trainer.backend.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.stereotype.Component

@Component
class CsrfCookie(private val repository: CookieCsrfTokenRepository) {

    fun write(request: HttpServletRequest, response: HttpServletResponse) {
        val token = repository.loadToken(request) ?: repository.generateToken(request)
        repository.saveToken(token, request, response)
    }
}

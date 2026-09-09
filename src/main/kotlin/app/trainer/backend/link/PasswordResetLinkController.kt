package app.trainer.backend.link

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

private const val TOKEN_LENGTH = 43
private const val DEEP_LINK_PREFIX = "trainer://reset/"

@RestController
class PasswordResetLinkController(private val properties: InviteLinkProperties) {

    @GetMapping("/r/{token}", produces = [MediaType.TEXT_HTML_VALUE])
    fun resetPage(@PathVariable token: String): String {
        if (token.length != TOKEN_LENGTH || !token.all(::isTokenCharacter)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Ссылка не найдена")
        }
        return resetPageHtml(
            token = token,
            webBaseUrl = properties.webBaseUrl,
            downloadUrl = properties.appDownloadUrl,
        )
    }

    private fun isTokenCharacter(symbol: Char): Boolean =
        symbol.isLetterOrDigit() || symbol == '-' || symbol == '_'
}

private fun resetPageHtml(token: String, webBaseUrl: String, downloadUrl: String): String =
    linkPageHtml(
        title = "Новый пароль",
        heading = "Задайте новый пароль",
        explanation = "Продолжите в браузере — откроется форма нового пароля. " +
            "Ссылка работает один раз.",
        webUrl = "$webBaseUrl/r/$token",
        appUrl = "$DEEP_LINK_PREFIX$token",
        downloadUrl = downloadUrl,
    )

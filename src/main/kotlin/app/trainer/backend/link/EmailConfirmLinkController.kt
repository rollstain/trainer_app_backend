package app.trainer.backend.link

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

private const val TOKEN_LENGTH = 43
private const val DEEP_LINK_PREFIX = "trainer://confirm/"

@RestController
class EmailConfirmLinkController(private val properties: InviteLinkProperties) {

    @GetMapping("/c/{token}", produces = [MediaType.TEXT_HTML_VALUE])
    fun confirmPage(@PathVariable token: String): String {
        if (token.length != TOKEN_LENGTH || !token.all(::isTokenCharacter)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Ссылка не найдена")
        }
        return confirmPageHtml(
            token = token,
            webBaseUrl = properties.webBaseUrl,
            downloadUrl = properties.appDownloadUrl,
        )
    }

    private fun isTokenCharacter(symbol: Char): Boolean =
        symbol.isLetterOrDigit() || symbol == '-' || symbol == '_'
}

private fun confirmPageHtml(token: String, webBaseUrl: String, downloadUrl: String): String =
    linkPageHtml(
        title = "Подтверждение почты",
        heading = "Подтвердите почту",
        explanation = "Продолжите в браузере — адрес подтвердится сразу. " +
            "Ссылка работает один раз.",
        webUrl = "$webBaseUrl/c/$token",
        appUrl = "$DEEP_LINK_PREFIX$token",
        downloadUrl = downloadUrl,
    )

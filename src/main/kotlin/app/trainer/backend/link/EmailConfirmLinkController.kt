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
        return confirmPageHtml(token = token, downloadUrl = properties.appDownloadUrl)
    }

    private fun isTokenCharacter(symbol: Char): Boolean =
        symbol.isLetterOrDigit() || symbol == '-' || symbol == '_'
}

private fun confirmPageHtml(token: String, downloadUrl: String): String = """
<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Подтверждение почты</title>
<style>
body { font-family: -apple-system, system-ui, sans-serif; margin: 0; padding: 32px 20px;
       background: #f5f5f4; color: #1c1917; display: flex; justify-content: center; }
main { max-width: 420px; width: 100%; }
h1 { font-size: 24px; margin: 0 0 8px; }
p { color: #57534e; line-height: 1.5; margin: 0 0 24px; }
a.button { display: block; text-align: center; text-decoration: none; border-radius: 12px;
           padding: 16px; font-weight: 600; margin-bottom: 12px; }
a.primary { background: #2f4fea; color: #fff; }
a.secondary { background: #fff; color: #1c1917; }
</style>
</head>
<body>
<main>
<h1>Подтвердите почту</h1>
<p>Откройте приложение — оно подтвердит адрес и сообщит об этом.
Ссылка работает один раз.</p>
<a class="button primary" href="$DEEP_LINK_PREFIX$token">Открыть приложение</a>
<a class="button secondary" href="$downloadUrl">Установить приложение</a>
</main>
<script>window.location.href = "$DEEP_LINK_PREFIX$token";</script>
</body>
</html>
""".trimIndent()

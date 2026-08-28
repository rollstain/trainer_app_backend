package app.trainer.backend.link

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

private const val CODE_LENGTH = 6
private const val DEEP_LINK_PREFIX = "trainer://invite/"
private const val RELATION_HANDLE_ALL_URLS = "delegate_permission/common.handle_all_urls"

@ConfigurationProperties(prefix = "trainer.links")
data class InviteLinkProperties(
    val androidPackage: String,
    val androidSha256: String,
    val appDownloadUrl: String,
)

@RestController
class InviteLinkController(private val properties: InviteLinkProperties) {

    @GetMapping("/i/{code}", produces = [MediaType.TEXT_HTML_VALUE])
    fun invitePage(@PathVariable code: String): String {
        val safeCode = code.filter(Char::isLetterOrDigit).uppercase()
        if (safeCode.length != CODE_LENGTH) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Приглашение не найдено")
        }
        return invitePageHtml(code = safeCode, downloadUrl = properties.appDownloadUrl)
    }

    @GetMapping("/.well-known/assetlinks.json", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun assetLinks(): ResponseEntity<List<AssetLinkStatement>> {
        if (properties.androidPackage.isBlank() || properties.androidSha256.isBlank()) {
            return ResponseEntity.notFound().build()
        }
        return ResponseEntity.ok(
            listOf(
                AssetLinkStatement(
                    relation = listOf(RELATION_HANDLE_ALL_URLS),
                    target = AssetLinkTarget(
                        packageName = properties.androidPackage,
                        sha256CertFingerprints = properties.androidSha256.split(",").map(String::trim),
                    ),
                )
            )
        )
    }
}

data class AssetLinkStatement(
    val relation: List<String>,
    val target: AssetLinkTarget,
)

data class AssetLinkTarget(
    val namespace: String = "android_app",
    val packageName: String,
    val sha256CertFingerprints: List<String>,
)

private fun invitePageHtml(code: String, downloadUrl: String): String = """
<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Приглашение от тренера</title>
<style>
body { font-family: -apple-system, system-ui, sans-serif; margin: 0; padding: 32px 20px;
       background: #f5f5f4; color: #1c1917; display: flex; justify-content: center; }
main { max-width: 420px; width: 100%; }
h1 { font-size: 24px; margin: 0 0 8px; }
p { color: #57534e; line-height: 1.5; margin: 0 0 24px; }
.code { font-family: ui-monospace, monospace; font-size: 32px; letter-spacing: 6px;
        background: #fff; border-radius: 12px; padding: 16px; text-align: center; margin-bottom: 24px; }
a.button { display: block; text-align: center; text-decoration: none; border-radius: 12px;
           padding: 16px; font-weight: 600; margin-bottom: 12px; }
a.primary { background: #2f4fea; color: #fff; }
a.secondary { background: #fff; color: #1c1917; }
</style>
</head>
<body>
<main>
<h1>Вас пригласил тренер</h1>
<p>Откройте приложение — код подставится сам. Если приложения ещё нет, сначала установите его.</p>
<div class="code">$code</div>
<a class="button primary" href="$DEEP_LINK_PREFIX$code">Открыть приложение</a>
<a class="button secondary" href="$downloadUrl">Установить приложение</a>
</main>
<script>window.location.href = "$DEEP_LINK_PREFIX$code";</script>
</body>
</html>
""".trimIndent()

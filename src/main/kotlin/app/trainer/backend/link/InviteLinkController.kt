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
    val webBaseUrl: String,
)

@RestController
class InviteLinkController(private val properties: InviteLinkProperties) {

    @GetMapping("/i/{code}", produces = [MediaType.TEXT_HTML_VALUE])
    fun invitePage(@PathVariable code: String): String {
        val safeCode = code.filter(Char::isLetterOrDigit).uppercase()
        if (safeCode.length != CODE_LENGTH) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Приглашение не найдено")
        }
        return invitePageHtml(
            code = safeCode,
            webBaseUrl = properties.webBaseUrl,
            downloadUrl = properties.appDownloadUrl,
        )
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

private fun invitePageHtml(code: String, webBaseUrl: String, downloadUrl: String): String =
    linkPageHtml(
        title = "Приглашение от тренера",
        heading = "Вас пригласил тренер",
        explanation = "Продолжите в браузере — код подставится сам. " +
            "На Android можно открыть приложение, если оно установлено.",
        webUrl = "$webBaseUrl/i/$code",
        appUrl = "$DEEP_LINK_PREFIX$code",
        downloadUrl = downloadUrl,
        highlight = code,
    )

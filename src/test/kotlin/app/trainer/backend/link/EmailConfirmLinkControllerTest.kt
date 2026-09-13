package app.trainer.backend.link

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private const val TOKEN = "0123456789012345678901234567890123456789012"
private const val CONFIRM_DOWNLOAD_URL = "https://example.org/app"
private const val CONFIRM_WEB_BASE_URL = "https://web.example.org"

class EmailConfirmLinkControllerTest {

    private fun controller() = EmailConfirmLinkController(
        InviteLinkProperties(
            androidPackage = "app.trainer.android",
            androidSha256 = "AA:BB:CC",
            appDownloadUrl = CONFIRM_DOWNLOAD_URL,
            webBaseUrl = CONFIRM_WEB_BASE_URL,
        )
    )

    @Test
    fun `the page leads to the web confirmation and keeps the app as a second way`() {
        val page = controller().confirmPage(TOKEN)

        assertTrue(page.contains("$CONFIRM_WEB_BASE_URL/c/$TOKEN"), "подтверждение в браузере")
        assertTrue(page.contains("trainer://confirm/$TOKEN"), "приложение остаётся вторым путём")
        assertTrue(page.contains(CONFIRM_DOWNLOAD_URL), "есть куда пойти без приложения")
    }

    @Test
    fun `nothing sends an iphone into a scheme it cannot handle`() {
        val page = controller().confirmPage(TOKEN)

        assertFalse(
            page.contains("""window.location.href = "trainer://"""),
            "слепой переход в приложение обрывал подтверждение почты на айфоне",
        )
    }

    @Test
    fun `a token of the wrong shape is not a page`() {
        val failure = assertFailsWith<ResponseStatusException> {
            controller().confirmPage("short")
        }

        assertEquals(HttpStatus.NOT_FOUND, failure.statusCode)
    }
}

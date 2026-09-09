package app.trainer.backend.link

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private const val TOKEN = "0123456789012345678901234567890123456789012"
private const val RESET_DOWNLOAD_URL = "https://example.org/app"
private const val RESET_WEB_BASE_URL = "https://web.example.org"

class PasswordResetLinkControllerTest {

    private fun controller() = PasswordResetLinkController(
        InviteLinkProperties(
            androidPackage = "app.trainer.android",
            androidSha256 = "AA:BB:CC",
            appDownloadUrl = RESET_DOWNLOAD_URL,
            webBaseUrl = RESET_WEB_BASE_URL,
        )
    )

    @Test
    fun `the page leads to the web form and keeps the app as a second way`() {
        val page = controller().resetPage(TOKEN)

        assertTrue(page.contains("$RESET_WEB_BASE_URL/r/$TOKEN"), "форма нового пароля в браузере")
        assertTrue(page.contains("trainer://reset/$TOKEN"), "приложение остаётся вторым путём")
        assertTrue(page.contains(RESET_DOWNLOAD_URL), "есть куда пойти без приложения")
    }

    @Test
    fun `nothing sends an iphone into a scheme it cannot handle`() {
        val page = controller().resetPage(TOKEN)

        assertFalse(
            page.contains("""window.location.href = "trainer://"""),
            "прежний слепой переход обрывал восстановление пароля на айфоне",
        )
    }

    @Test
    fun `a token of the wrong shape is not a page`() {
        val failure = assertFailsWith<ResponseStatusException> {
            controller().resetPage("short")
        }

        assertEquals(HttpStatus.NOT_FOUND, failure.statusCode)
    }
}

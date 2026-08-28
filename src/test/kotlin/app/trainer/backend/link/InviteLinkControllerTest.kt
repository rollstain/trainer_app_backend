package app.trainer.backend.link

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private const val CODE = "CVSKQJ"
private const val PACKAGE = "app.trainer.android"
private const val FINGERPRINT = "AA:BB:CC"
private const val DOWNLOAD_URL = "https://example.org/app"

class InviteLinkControllerTest {

    private fun controller(
        androidPackage: String = PACKAGE,
        androidSha256: String = FINGERPRINT,
    ) = InviteLinkController(
        InviteLinkProperties(
            androidPackage = androidPackage,
            androidSha256 = androidSha256,
            appDownloadUrl = DOWNLOAD_URL,
        )
    )

    @Test
    fun `the page offers the app and repeats the code`() {
        val page = controller().invitePage(CODE)

        assertTrue(page.contains(CODE), "код виден человеку")
        assertTrue(page.contains("trainer://invite/$CODE"), "ссылка открывает приложение")
        assertTrue(page.contains(DOWNLOAD_URL), "есть куда пойти без приложения")
    }

    @Test
    fun `a lowercase code from a messenger still works`() {
        val page = controller().invitePage(CODE.lowercase())

        assertTrue(page.contains(CODE))
    }

    @Test
    fun `anything that is not a code is not a page`() {
        val failure = assertFailsWith<ResponseStatusException> {
            controller().invitePage("not-a-code-at-all")
        }

        assertEquals(HttpStatus.NOT_FOUND, failure.statusCode)
    }

    @Test
    fun `a code with punctuation glued by a messenger is cleaned up`() {
        val page = controller().invitePage("$CODE.")

        assertTrue(page.contains(CODE))
    }

    @Test
    fun `the app claims the domain only when the fingerprint is known`() {
        val statements = controller().assetLinks().body

        assertEquals(PACKAGE, statements?.single()?.target?.packageName)
        assertEquals(listOf(FINGERPRINT), statements?.single()?.target?.sha256CertFingerprints)
    }

    @Test
    fun `without a fingerprint there is no claim at all`() {
        val response = controller(androidSha256 = "").assetLinks()

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode, "пустой файл сломал бы проверку ссылок")
    }
}

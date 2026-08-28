package app.trainer.backend.config

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

private val SOME_ID: UUID = UUID.fromString("40000000-0000-0000-0000-000000000001")

private fun base64(raw: String): String =
    java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())

class PagingTest {

    @Test
    fun `a cursor survives the round trip`() {
        val cursor = PageCursor(sortKey = "Приседания со штангой", id = SOME_ID)

        assertEquals(cursor, decodeCursor(encodeCursor(cursor)))
    }

    @Test
    fun `a sort key with the separator inside still decodes`() {
        val cursor = PageCursor(sortKey = "Жим | узким хватом", id = SOME_ID)

        assertEquals(cursor, decodeCursor(encodeCursor(cursor)))
    }

    @Test
    fun `garbage is not a cursor`() {
        assertNull(decodeCursor(null))
        assertNull(decodeCursor("   "))
        assertNull(decodeCursor("не-base64"))
        assertNull(decodeCursor(base64("без разделителя")))
        assertNull(decodeCursor(base64("ключ|не-uuid")))
    }

    @Test
    fun `page size is clamped to sane bounds`() {
        assertNull(pageSizeOf(null), "без limit пагинации нет — отдаём весь список")
        assertEquals(1, pageSizeOf(0))
        assertEquals(1, pageSizeOf(-10))
        assertEquals(20, pageSizeOf(20))
        assertEquals(MAX_PAGE_SIZE, pageSizeOf(MAX_PAGE_SIZE * 2))
    }

    @Test
    fun `a page keeps its cursor while its items are mapped`() {
        val page = Page(items = listOf(1, 2, 3), nextCursor = "cursor")

        val mapped = page.map { it * 2 }

        assertEquals(listOf(2, 4, 6), mapped.items)
        assertEquals("cursor", mapped.nextCursor)
    }
}

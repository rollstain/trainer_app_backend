package app.trainer.backend.config

import java.util.Base64
import java.util.UUID
import org.springframework.http.ResponseEntity

const val NEXT_CURSOR_HEADER = "X-Next-Cursor"
const val MAX_PAGE_SIZE = 200

const val EXTRA_ROW_TO_DETECT_NEXT_PAGE = 1

private const val CURSOR_SEPARATOR = '|'

data class PageCursor(val sortKey: String, val id: UUID)

data class Page<T>(val items: List<T>, val nextCursor: String?) {

    fun <R> map(transform: (T) -> R): Page<R> = Page(items = items.map(transform), nextCursor = nextCursor)
}

fun encodeCursor(cursor: PageCursor): String {
    val raw = "${cursor.sortKey}$CURSOR_SEPARATOR${cursor.id}"
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
}

fun decodeCursor(raw: String?): PageCursor? {
    val trimmed = raw?.takeIf { it.isNotBlank() } ?: return null
    val decoded = runCatching { String(Base64.getUrlDecoder().decode(trimmed)) }.getOrNull() ?: return null
    if (!decoded.contains(CURSOR_SEPARATOR)) return null
    val id = runCatching { UUID.fromString(decoded.substringAfterLast(CURSOR_SEPARATOR)) }.getOrNull() ?: return null
    return PageCursor(sortKey = decoded.substringBeforeLast(CURSOR_SEPARATOR), id = id)
}

fun pageSizeOf(limit: Int?): Int? = limit?.coerceIn(1, MAX_PAGE_SIZE)

fun <T> pageResponse(page: Page<T>): ResponseEntity<List<T>> {
    val builder = ResponseEntity.ok()
    page.nextCursor?.let { builder.header(NEXT_CURSOR_HEADER, it) }
    return builder.body(page.items)
}

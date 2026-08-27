package app.trainer.backend.push

import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource

private const val MESSAGES_BASENAME = "messages"
private const val MESSAGES_ENCODING = "UTF-8"
private val ENGLISH: Locale = Locale.forLanguageTag("en")

class PushTextsTest {

    private val pushTexts = PushTexts(
        ResourceBundleMessageSource().apply {
            setBasename(MESSAGES_BASENAME)
            setDefaultEncoding(MESSAGES_ENCODING)
            setFallbackToSystemLocale(false)
        }
    )

    @Test
    fun `every push has a text in both languages`() {
        PushText.entries.forEach { text ->
            val russian = pushTexts.render(text = text, locale = DEFAULT_PUSH_LOCALE)
            val english = pushTexts.render(text = text, locale = ENGLISH)

            assertTrue(russian.title.isNotBlank(), "нет русского заголовка для $text")
            assertTrue(russian.body.isNotBlank(), "нет русского текста для $text")
            assertTrue(english.title.isNotBlank(), "нет английского заголовка для $text")
            assertTrue(english.body.isNotBlank(), "нет английского текста для $text")
        }
    }

    @Test
    fun `a session reminder speaks the language of the device`() {
        assertEquals("Скоро тренировка", pushTexts.render(PushText.SESSION_SOON, DEFAULT_PUSH_LOCALE).title)
        assertEquals("Session coming up", pushTexts.render(PushText.SESSION_SOON, ENGLISH).title)
    }

    @Test
    fun `a device that never reported its language gets russian`() {
        assertEquals(DEFAULT_PUSH_LOCALE, localeOfToken(null))
        assertEquals(DEFAULT_PUSH_LOCALE, localeOfToken("   "))
        assertEquals(DEFAULT_PUSH_LOCALE, localeOfToken("###"))
    }

    @Test
    fun `a device language is understood with and without a region`() {
        assertEquals("en", localeOfToken("en").language)
        assertEquals("en", localeOfToken("en-US").language)
    }
}

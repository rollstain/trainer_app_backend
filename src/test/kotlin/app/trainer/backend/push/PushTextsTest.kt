package app.trainer.backend.push

import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource

private const val MESSAGES_BASENAME = "messages"
private const val MESSAGES_ENCODING = "UTF-8"
private val ENGLISH: Locale = Locale.forLanguageTag("en")
private val SAMPLE_ARGS = listOf("18:00", "Дмитрием Роговым")
private const val TWO = 2L
private const val ONE = 1L
private const val FIVE = 5L
private const val ELEVEN = 11L
private const val TWENTY_ONE = 21L
private const val TWENTY_TWO = 22L
private val SUMMARY_ARGS = listOf(summaryArgOf(SummaryPart.CHECK_INS, TWO))

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
            val args = if (text == PushText.MORNING_SUMMARY) SUMMARY_ARGS else SAMPLE_ARGS
            val russian = pushTexts.render(text = text, args = args, locale = DEFAULT_PUSH_LOCALE)
            val english = pushTexts.render(text = text, args = args, locale = ENGLISH)

            assertTrue(russian.title.isNotBlank(), "нет русского заголовка для $text")
            assertTrue(russian.body.isNotBlank(), "нет русского текста для $text")
            assertTrue(english.title.isNotBlank(), "нет английского заголовка для $text")
            assertTrue(english.body.isNotBlank(), "нет английского текста для $text")
        }
    }

    @Test
    fun `a session reminder speaks the language of the device and names the hour`() {
        val russian = pushTexts.render(PushText.SESSION_SOON, SAMPLE_ARGS, DEFAULT_PUSH_LOCALE)
        val english = pushTexts.render(PushText.SESSION_SOON, SAMPLE_ARGS, ENGLISH)

        assertEquals("Тренировка в 18:00", russian.title)
        assertEquals("Session at 18:00", english.title)
        assertTrue(russian.body.contains("Дмитрием"), "в тексте нет имени: ${'$'}{russian.body}")
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

    @Test
    fun `the morning summary counts the night in the language of the device`() {
        val night = listOf(
            summaryArgOf(SummaryPart.CHECK_INS, TWO),
            summaryArgOf(SummaryPart.FORM_CHECKS, ONE),
            summaryArgOf(SummaryPart.SLOT_BOOKINGS, FIVE),
        )

        val russian = pushTexts.render(PushText.MORNING_SUMMARY, night, DEFAULT_PUSH_LOCALE)
        val english = pushTexts.render(PushText.MORNING_SUMMARY, night, ENGLISH)

        assertEquals("Утренняя сводка", russian.title)
        assertEquals("За ночь: 2 чек-ина, 1 разбор техники, 5 записей на слоты", russian.body)
        assertEquals("Overnight: 2 check-ins, 1 form review, 5 slot bookings", english.body)
    }

    @Test
    fun `russian counts follow the last digits`() {
        val bodies = listOf(ONE, ELEVEN, TWENTY_ONE, TWENTY_TWO).map { count ->
            pushTexts.render(
                PushText.MORNING_SUMMARY,
                listOf(summaryArgOf(SummaryPart.CHECK_INS, count)),
                DEFAULT_PUSH_LOCALE,
            ).body
        }

        assertEquals(
            listOf(
                "За ночь: 1 чек-ин",
                "За ночь: 11 чек-инов",
                "За ночь: 21 чек-ин",
                "За ночь: 22 чек-ина",
            ),
            bodies,
        )
    }
}

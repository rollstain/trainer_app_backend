package app.trainer.backend.push

import java.util.Locale
import org.springframework.context.MessageSource
import org.springframework.stereotype.Component

val DEFAULT_PUSH_LOCALE: Locale = Locale.forLanguageTag("ru")

private const val ENGLISH_LANGUAGE = "en"
private const val SUMMARY_PART_SEPARATOR = "="
private const val SUMMARY_PARTS_JOINER = ", "
private const val SUMMARY_ARG_MAX_FIELDS = 3
private const val SUMMARY_NAME_FIELD = 2
private const val LAST_DIGIT_DIVISOR = 10L
private const val LAST_TWO_DIGITS_DIVISOR = 100L
private const val SINGLE = 1L
private const val RUSSIAN_ONE_EXCEPTION = 11L
private val RUSSIAN_FEW_LAST_DIGITS = 2L..4L
private val RUSSIAN_FEW_EXCEPTIONS = 12L..14L

enum class SummaryPart(val messageKey: String) {
    CHECK_INS("check-ins"),
    FORM_CHECKS("form-checks"),
    NEW_CLIENTS("new-clients"),
    CLIENTS_LEFT("clients-left"),
    SLOT_BOOKINGS("slot-bookings"),
    OTHER("other"),
}

fun summaryArgOf(part: SummaryPart, count: Long): String = "${part.name}$SUMMARY_PART_SEPARATOR$count"

fun namedSummaryArgOf(part: SummaryPart, name: String): String =
    "${summaryArgOf(part, SINGLE)}$SUMMARY_PART_SEPARATOR$name"

data class RenderedPush(val title: String, val body: String)

@Component
class PushTexts(private val messageSource: MessageSource) {

    fun render(text: PushText, args: List<String>, locale: Locale): RenderedPush {
        val values: Array<Any> = if (text == PushText.MORNING_SUMMARY) {
            arrayOf(summaryOf(args = args, locale = locale))
        } else {
            args.toTypedArray()
        }
        return RenderedPush(
            title = messageSource.getMessage(text.titleKey, values, locale),
            body = messageSource.getMessage(text.bodyKey, values, locale),
        )
    }

    private fun summaryOf(args: List<String>, locale: Locale): String {
        val parts = mutableListOf<String>()
        for (arg in args) {
            val fields = arg.split(SUMMARY_PART_SEPARATOR, limit = SUMMARY_ARG_MAX_FIELDS)
            val (partName, countText) = fields
            val part = SummaryPart.valueOf(partName)
            val name = fields.getOrNull(SUMMARY_NAME_FIELD)
            if (name != null) {
                parts.add(messageSource.getMessage("push.summary.${part.messageKey}.named", arrayOf(name), locale))
                continue
            }
            val form = pluralFormOf(count = countText.toLong(), locale = locale)
            parts.add(messageSource.getMessage("push.summary.${part.messageKey}.$form", arrayOf(countText), locale))
        }
        return parts.joinToString(SUMMARY_PARTS_JOINER)
    }

    private fun pluralFormOf(count: Long, locale: Locale): String {
        if (locale.language == ENGLISH_LANGUAGE) return if (count == SINGLE) "one" else "other"
        val lastDigit = count % LAST_DIGIT_DIVISOR
        val lastTwoDigits = count % LAST_TWO_DIGITS_DIVISOR
        return when {
            lastDigit == SINGLE && lastTwoDigits != RUSSIAN_ONE_EXCEPTION -> "one"
            lastDigit in RUSSIAN_FEW_LAST_DIGITS && lastTwoDigits !in RUSSIAN_FEW_EXCEPTIONS -> "few"
            else -> "many"
        }
    }
}

fun localeOfToken(storedLocale: String?): Locale {
    val languageTag = storedLocale?.takeIf { it.isNotBlank() } ?: return DEFAULT_PUSH_LOCALE
    val parsed = Locale.forLanguageTag(languageTag)
    return if (parsed.language.isEmpty()) DEFAULT_PUSH_LOCALE else parsed
}

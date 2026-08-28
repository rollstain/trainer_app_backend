package app.trainer.backend.push

import java.util.Locale
import org.springframework.context.MessageSource
import org.springframework.stereotype.Component

val DEFAULT_PUSH_LOCALE: Locale = Locale.forLanguageTag("ru")

data class RenderedPush(val title: String, val body: String)

@Component
class PushTexts(private val messageSource: MessageSource) {

    fun render(text: PushText, args: List<String>, locale: Locale): RenderedPush {
        val values = args.toTypedArray()
        return RenderedPush(
            title = messageSource.getMessage(text.titleKey, values, locale),
            body = messageSource.getMessage(text.bodyKey, values, locale),
        )
    }
}

fun localeOfToken(storedLocale: String?): Locale {
    val languageTag = storedLocale?.takeIf { it.isNotBlank() } ?: return DEFAULT_PUSH_LOCALE
    val parsed = Locale.forLanguageTag(languageTag)
    return if (parsed.language.isEmpty()) DEFAULT_PUSH_LOCALE else parsed
}

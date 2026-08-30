package app.trainer.backend.mail

import jakarta.mail.internet.InternetAddress
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

private const val ALTERNATIVE_PARTS = true
private const val RESET_SUBJECT = "Новый пароль в приложении тренера"
private const val CONFIRM_SUBJECT = "Подтвердите почту в приложении тренера"

@ConfigurationProperties(prefix = "trainer.mail")
data class MailProperties(
    val from: String,
    val fromName: String,
    val resetLinkPrefix: String,
    val confirmLinkPrefix: String,
)

@Service
class MailService(
    private val mailSender: ObjectProvider<JavaMailSender>,
    private val properties: MailProperties,
    @param:Value("\${spring.mail.host:}") private val smtpHost: String,
) {

    val isConfigured: Boolean
        get() = smtpHost.isNotBlank() && properties.from.isNotBlank()

    fun sendPasswordReset(recipient: String, link: String) {
        send(recipient = recipient, subject = RESET_SUBJECT, plain = resetPlainText(link), html = resetHtml(link))
    }

    fun sendEmailConfirmation(recipient: String, link: String) {
        send(recipient = recipient, subject = CONFIRM_SUBJECT, plain = confirmPlainText(link), html = confirmHtml(link))
    }

    fun resetLinkOf(token: String): String = properties.resetLinkPrefix + token

    fun confirmLinkOf(token: String): String = properties.confirmLinkPrefix + token

    private fun send(recipient: String, subject: String, plain: String, html: String) {
        val sender = configuredSender()
        val message = sender.createMimeMessage()
        val helper = MimeMessageHelper(message, ALTERNATIVE_PARTS, Charsets.UTF_8.name())
        helper.setFrom(InternetAddress(properties.from, properties.fromName, Charsets.UTF_8.name()))
        helper.setTo(recipient)
        helper.setSubject(subject)
        helper.setText(plain, html)
        sender.send(message)
    }

    private fun configuredSender(): JavaMailSender {
        if (!isConfigured) {
            throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Отправка почты не настроена")
        }
        return mailSender.ifAvailable
            ?: throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Отправка почты не настроена")
    }
}

private fun resetPlainText(link: String): String = """
Здравствуйте!

Вы попросили сменить пароль. Откройте ссылку — она приведёт в приложение, где можно задать новый:

$link

Ссылка работает час и только один раз. Если пароль менять вы не просили, ничего делать не нужно:
прежний остаётся в силе.
""".trimIndent()

private fun confirmPlainText(link: String): String = """
Здравствуйте!

Этот адрес указали в приложении тренера. Откройте ссылку, чтобы подтвердить, что почта ваша:

$link

Ссылка работает три дня и только один раз. Если это были не вы, ничего делать не нужно:
неподтверждённый адрес сможет забрать его настоящий владелец.
""".trimIndent()

private fun confirmHtml(link: String): String = """
<!doctype html>
<html lang="ru">
<body style="margin:0;padding:24px;background:#f5f5f4;font-family:-apple-system,system-ui,sans-serif;color:#1c1917;">
<div style="max-width:420px;margin:0 auto;">
<p style="font-size:16px;line-height:1.5;">Этот адрес указали в приложении тренера. Нажмите кнопку,
чтобы подтвердить, что почта ваша.</p>
<p style="margin:24px 0;">
<a href="$link" style="display:block;text-align:center;text-decoration:none;background:#2f4fea;color:#fff;border-radius:12px;padding:16px;font-weight:600;">Подтвердить почту</a>
</p>
<p style="font-size:14px;line-height:1.5;color:#57534e;">Ссылка работает три дня и только один раз.
Если это были не вы, ничего делать не нужно: неподтверждённый адрес сможет забрать его настоящий владелец.</p>
</div>
</body>
</html>
""".trimIndent()

private fun resetHtml(link: String): String = """
<!doctype html>
<html lang="ru">
<body style="margin:0;padding:24px;background:#f5f5f4;font-family:-apple-system,system-ui,sans-serif;color:#1c1917;">
<div style="max-width:420px;margin:0 auto;">
<p style="font-size:16px;line-height:1.5;">Вы попросили сменить пароль. Нажмите кнопку — откроется
приложение, где можно задать новый.</p>
<p style="margin:24px 0;">
<a href="$link" style="display:block;text-align:center;text-decoration:none;background:#2f4fea;color:#fff;border-radius:12px;padding:16px;font-weight:600;">Задать новый пароль</a>
</p>
<p style="font-size:14px;line-height:1.5;color:#57534e;">Ссылка работает час и только один раз.
Если пароль менять вы не просили, ничего делать не нужно: прежний остаётся в силе.</p>
</div>
</body>
</html>
""".trimIndent()

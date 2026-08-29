package app.trainer.backend.legal

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

private const val OPERATOR_PLACEHOLDER = "оператор сервиса"
private const val CONTACT_PLACEHOLDER = "контакт оператора не указан"

@ConfigurationProperties(prefix = "trainer.legal")
data class LegalProperties(
    val operatorName: String,
    val operatorContact: String,
    val updatedOn: String,
)

@RestController
class LegalController(private val properties: LegalProperties) {

    @GetMapping("/legal/privacy", produces = [MediaType.TEXT_HTML_VALUE])
    fun privacy(): String = page(title = "Политика обработки персональных данных", body = privacyBody())

    @GetMapping("/legal/terms", produces = [MediaType.TEXT_HTML_VALUE])
    fun terms(): String = page(title = "Пользовательское соглашение", body = termsBody())

    private fun operator(): String = properties.operatorName.ifBlank { OPERATOR_PLACEHOLDER }

    private fun contact(): String = properties.operatorContact.ifBlank { CONTACT_PLACEHOLDER }

    private fun privacyBody(): String = """
<h2>Кто обрабатывает данные</h2>
<p>Оператор — ${operator()}. Связаться можно по адресу ${contact()}.</p>

<h2>Какие данные мы собираем</h2>
<p>При регистрации — имя, которым вас будут звать, адрес почты и, если вы его завели, логин.
Телефон необязателен и запрашивается отдельно.</p>
<p>Во время работы с приложением сохраняется то, что вы вносите сами: записи о тренировках
(упражнения, подходы, вес), замеры тела, самочувствие в чек-инах, фотографии и видео, которые вы
загружаете, сообщения в переписке с тренером.</p>
<p>Тренер, к которому вы присоединились, может делать заметки о вас, в том числе о состоянии
здоровья и ограничениях к нагрузке. Такие заметки относятся к специальной категории персональных
данных и видны только этому тренеру.</p>
<p>Автоматически сохраняются технические данные: сведения об устройстве и сессиях входа, токен
для доставки уведомлений, сообщения об ошибках приложения.</p>

<h2>Зачем</h2>
<p>Чтобы приложение работало: связать вас с вашим тренером, вести дневник и расписание, показывать
динамику, доставлять уведомления и восстанавливать доступ к аккаунту.</p>

<h2>Кто ещё видит эти данные</h2>
<p>Ваши записи, замеры, фотографии и заметки видит <b>тот тренер, которого вы выбрали сами</b>,
введя его код или приняв приглашение. Другим подопечным они не показываются. Связь с тренером
можно прекратить.</p>
<p>Для работы сервиса привлекаются обработчики: хостинг и база данных, объектное хранилище файлов,
служба доставки уведомлений, почтовая служба для писем о смене пароля и Telegram — для входа
и подтверждений. Часть из них находится за пределами России, и передача данных им является
трансграничной.</p>

<h2>Сколько храним</h2>
<p>Пока существует аккаунт. После удаления аккаунта данные удаляются, кроме того, что мы обязаны
хранить по закону.</p>

<h2>Ваши права</h2>
<p>Вы можете запросить сведения об обработке, исправить неточности, потребовать удаления
и отозвать согласие — написав по адресу ${contact()}. Отзыв согласия означает прекращение работы
сервиса для вас.</p>
"""

    private fun termsBody(): String = """
<h2>О чём соглашение</h2>
<p>Приложение помогает тренеру и его подопечным работать вместе: вести дневник тренировок,
расписание, переписку и наблюдать за прогрессом. Пользуясь приложением, вы принимаете эти условия.</p>

<h2>Аккаунт</h2>
<p>Аккаунт создаётся по почте с паролем, по приглашению тренера или через Telegram. Отвечайте
за сохранность пароля: любой, кто его знает, получает доступ к вашим данным. Пароль можно сменить
в профиле, а забытый — восстановить письмом или через привязанный Telegram.</p>
<p>Любой пользователь вправе завести себе рабочее место тренера. Тренер получает доступ к данным
только тех людей, которые сами ввели его код или приняли приглашение.</p>

<h2>Что мы не делаем</h2>
<p>Приложение не оказывает медицинских услуг и не заменяет консультацию врача. Рекомендации,
которые даёт тренер, — его собственные; ответственность за них несёт он, а не сервис.</p>

<h2>Содержимое</h2>
<p>Записи, фотографии и сообщения остаются вашими. Мы храним и показываем их так, как описано
в политике обработки данных, и не используем для рекламы.</p>

<h2>Прекращение</h2>
<p>Вы можете перестать пользоваться приложением в любой момент и запросить удаление аккаунта
по адресу ${contact()}. Мы вправе ограничить доступ, если приложение используется для рассылки
спама, чужих данных или иных действий, нарушающих закон.</p>

<h2>Изменения</h2>
<p>Условия могут меняться; действующая редакция всегда доступна по этому адресу.</p>
"""

    private fun page(title: String, body: String): String = """
<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>$title</title>
<style>
body { font-family: -apple-system, system-ui, sans-serif; margin: 0; padding: 32px 20px;
       background: #f5f5f4; color: #1c1917; display: flex; justify-content: center; }
main { max-width: 640px; width: 100%; line-height: 1.6; }
h1 { font-size: 26px; margin: 0 0 8px; }
h2 { font-size: 18px; margin: 28px 0 8px; }
p { margin: 0 0 12px; }
.updated { color: #78716c; font-size: 14px; margin-bottom: 24px; }
</style>
</head>
<body>
<main>
<h1>$title</h1>
<p class="updated">Редакция от ${properties.updatedOn}</p>
$body
</main>
</body>
</html>
    """.trimIndent()
}

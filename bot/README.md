# Бот входа `@ml_auth_bot`

Подтверждает вход в приложение: превращает `/start <код>` в вызов `POST /auth/telegram/confirm`.

Второе дело — заявки на тренера. Каждый цикл опроса бот забирает у API новые заявки
(`GET /telegram/coach-requests/unannounced`), шлёт каждую владельцу с кнопками «Одобрить» и «Отклонить»
и отмечает отправленной. Нажатие кнопки уходит в `POST /telegram/coach-requests/{id}/decision`
с Telegram-номером нажавшего. API сам проверяет, что это владелец: решения других людей отклоняются,
даже если сообщение с кнопками попало к ним.

Нужна машина с исходящим доступом к `api.telegram.org` и к `https://api.lyashukfit.ru`. Прод-сервер
подходит: 16.09.2026 Bot API с него отвечал. Если доступ к Telegram оттуда пропадёт — бот переезжает
на машину вне РФ, API при этом не меняется. Требования минимальные: Python 3.9+, сторонних библиотек нет.

Одновременно работает только один экземпляр: второй, опрашивающий тот же токен, получит от Telegram
`409 Conflict`.

## Что знает бот

| Переменная | Значение |
|---|---|
| `TELEGRAM_BOT_TOKEN` | токен от @BotFather. Есть **только** на этой машине |
| `TELEGRAM_BOT_SECRET` | общий секрет с API, ровно та же строка, что в `/etc/trainer/trainer.env` |
| `TRAINER_API_URL` | `https://api.lyashukfit.ru` |
| `TELEGRAM_ADMIN_CHAT_ID` | числовой Telegram-номер владельца: туда приходят заявки на тренера. Владелец должен хоть раз нажать «Start» у бота |

API токен бота не использует и не читает: токен лежит только в `/etc/trainer-bot/bot.env` под отдельным
пользователем, даже когда бот живёт на прод-сервере.

## Установка

```
useradd --system --home /opt/trainer-bot --shell /usr/sbin/nologin trainerbot
mkdir -p /opt/trainer-bot /etc/trainer-bot
install -o trainerbot -g trainerbot -m 755 telegram_login_bot.py /opt/trainer-bot/
install -o root -g trainerbot -m 640 bot.env /etc/trainer-bot/bot.env
install -m 644 trainer-login-bot.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now trainer-login-bot
```

`bot.env` заполняется из `.env.example`, в git не попадает.

## Проверка

```
systemctl status trainer-login-bot
journalctl -u trainer-login-bot -f
```

В логе при старте — `бот запущен`, при успешном входе — `вход подтверждён, статус 200, вид LOGIN` (у привязки к аккаунту — `вид LINK`).

## Что включить на стороне API

После запуска бота на прод-сервере в `/etc/trainer/trainer.env`:

```
TELEGRAM_BOT_USERNAME=ml_auth_bot
TELEGRAM_BOT_SECRET=<та же строка, что у бота>
```

и `systemctl restart trainer`. Без `TELEGRAM_BOT_USERNAME` `POST /auth/telegram/start` отвечает 501
«Вход через Telegram не настроен».

## Откат

`systemctl stop trainer-login-bot` и убрать `TELEGRAM_BOT_USERNAME` из `trainer.env` с рестартом API —
начать вход через Telegram станет нельзя, вход по паролю и по коду тренера продолжит работать.

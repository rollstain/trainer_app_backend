# Бот входа `@ml_auth_bot`

Подтверждает вход в приложение: превращает `/start <код>` в вызов `POST /auth/telegram/confirm`.

Живёт **не на прод-сервере**: с машины в РФ `api.telegram.org` недоступен. Нужна отдельная машина
вне РФ с исходящим доступом к `api.telegram.org` и к `https://api.lyashukfit.ru`.
Требования минимальные: Python 3.9+, сторонних библиотек нет.

## Что знает бот

| Переменная | Значение |
|---|---|
| `TELEGRAM_BOT_TOKEN` | токен от @BotFather. Есть **только** на этой машине |
| `TELEGRAM_BOT_SECRET` | общий секрет с API, ровно та же строка, что в `/etc/trainer/trainer.env` |
| `TRAINER_API_URL` | `https://api.lyashukfit.ru` |

Токена бота на прод-сервере нет и быть не должно: API его не использует.

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

В логе при старте — `бот запущен`, при успешном входе — `вход подтверждён, статус 204`.

## Что включить на стороне API

После запуска бота на прод-сервере в `/etc/trainer/trainer.env`:

```
TELEGRAM_BOT_USERNAME=ml_auth_bot
TELEGRAM_BOT_SECRET=<та же строка, что у бота>
```

и `systemctl restart trainer`. Кнопка «Войти через Telegram» появится в приложении сама:
её показ управляется ответом `GET /auth/providers`, пересборка не нужна.

## Откат

`systemctl stop trainer-login-bot` и убрать `TELEGRAM_BOT_USERNAME` из `trainer.env` с рестартом API —
кнопка исчезнет, вход по коду продолжит работать.

#!/usr/bin/env python3
import json
import logging
import os
import sys
import time
import urllib.error
import urllib.request

TELEGRAM_API = "https://api.telegram.org"
POLL_TIMEOUT_SECONDS = 30
REQUEST_TIMEOUT_SECONDS = 45
RETRY_DELAY_SECONDS = 5
START_COMMAND = "/start"

REPLY_CONFIRMED = "Готово. Вернитесь в приложение — вход завершится сам."
REPLY_LINK_DEAD = "Ссылка устарела. Начните вход в приложении заново."
REPLY_FAILED = "Не получилось подтвердить вход. Попробуйте ещё раз через минуту."
REPLY_HELP = "Этот бот подтверждает вход в приложение. Нажмите «Войти через Telegram» в приложении."

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
    stream=sys.stdout,
)
logger = logging.getLogger("telegram-login-bot")


class Settings:

    def __init__(self):
        self.bot_token = require_env("TELEGRAM_BOT_TOKEN")
        self.bot_secret = require_env("TELEGRAM_BOT_SECRET")
        self.api_url = require_env("TRAINER_API_URL").rstrip("/")


def require_env(name):
    value = os.environ.get(name, "").strip()
    if not value:
        raise SystemExit(f"{name} не задан")
    return value


def post_json(url, payload, headers=None):
    request = urllib.request.Request(
        url=url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", **(headers or {})},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
        body = response.read().decode("utf-8")
        return response.status, json.loads(body) if body else None


def get_json(url):
    with urllib.request.urlopen(url, timeout=REQUEST_TIMEOUT_SECONDS) as response:
        return json.loads(response.read().decode("utf-8"))


class TelegramBot:

    def __init__(self, settings):
        self.settings = settings
        self.offset = None

    def poll(self):
        url = f"{TELEGRAM_API}/bot{self.settings.bot_token}/getUpdates?timeout={POLL_TIMEOUT_SECONDS}"
        if self.offset is not None:
            url = f"{url}&offset={self.offset}"
        answer = get_json(url)
        if not answer.get("ok"):
            raise RuntimeError(f"Telegram отказал: {answer.get('description')}")
        return answer.get("result", [])

    def reply(self, chat_id, text):
        post_json(
            url=f"{TELEGRAM_API}/bot{self.settings.bot_token}/sendMessage",
            payload={"chat_id": chat_id, "text": text},
        )

    def accept(self, update):
        self.offset = update["update_id"] + 1
        message = update.get("message")
        if not message:
            return
        chat_id = message["chat"]["id"]
        start_code = start_code_of(message.get("text", ""))
        if start_code is None:
            self.reply(chat_id, REPLY_HELP)
            return
        sender = message.get("from", {})
        self.reply(chat_id, self.confirm(start_code=start_code, sender=sender))

    def confirm(self, start_code, sender):
        payload = {
            "startCode": start_code,
            "telegramUserId": str(sender.get("id")),
            "telegramDisplayName": display_name_of(sender),
        }
        try:
            status, _ = post_json(
                url=f"{self.settings.api_url}/auth/telegram/confirm",
                payload=payload,
                headers={"X-Telegram-Bot-Secret": self.settings.bot_secret},
            )
            logger.info("вход подтверждён, статус %s", status)
            return REPLY_CONFIRMED
        except urllib.error.HTTPError as failure:
            logger.warning("сервер отказал: %s", failure.code)
            return REPLY_LINK_DEAD if failure.code == 410 else REPLY_FAILED
        except urllib.error.URLError as failure:
            logger.error("сервер недоступен: %s", failure.reason)
            return REPLY_FAILED


def start_code_of(text):
    parts = text.strip().split()
    if len(parts) != 2 or parts[0] != START_COMMAND:
        return None
    return parts[1]


def display_name_of(sender):
    parts = [sender.get("first_name"), sender.get("last_name")]
    return " ".join(part for part in parts if part) or None


def main():
    bot = TelegramBot(Settings())
    logger.info("бот запущен")
    while True:
        try:
            for update in bot.poll():
                bot.accept(update)
        except Exception as failure:
            logger.error("цикл опроса упал: %s", failure)
            time.sleep(RETRY_DELAY_SECONDS)


if __name__ == "__main__":
    main()

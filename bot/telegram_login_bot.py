#!/usr/bin/env python3
import json
import logging
import os
import socket
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
REPLY_LINKED = "Готово. Теперь входите в приложение кнопкой «Войти через Telegram»."
REPLY_LINK_DEAD = "Ссылка устарела. Начните вход в приложении заново."
REPLY_FAILED = "Не получилось подтвердить вход. Попробуйте ещё раз через минуту."
REPLY_HELP = "Этот бот подтверждает вход в приложение. Нажмите «Войти через Telegram» в приложении."
REPLY_WHO = "Кто вы?"
REPLY_CLIENT = (
    "Откройте приложение и нажмите «Войти через Telegram». "
    "Код от тренера введёте уже внутри."
)
REPLY_COACH_ASKED = (
    "Откройте приложение, войдите через Telegram и нажмите «Я тренер» — оттуда заявка уйдёт владельцу."
)
BUTTON_COACH = "Я тренер"
BUTTON_CLIENT = "Я подопечный"
CALLBACK_COACH = "coach"
CALLBACK_CLIENT = "client"

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

    def reply(self, chat_id, text, keyboard=None):
        payload = {"chat_id": chat_id, "text": text}
        if keyboard is not None:
            payload["reply_markup"] = {"inline_keyboard": keyboard}
        post_json(
            url=f"{TELEGRAM_API}/bot{self.settings.bot_token}/sendMessage",
            payload=payload,
        )

    def answer_callback(self, callback_id):
        post_json(
            url=f"{TELEGRAM_API}/bot{self.settings.bot_token}/answerCallbackQuery",
            payload={"callback_query_id": callback_id},
        )

    def accept(self, update):
        self.offset = update["update_id"] + 1
        callback = update.get("callback_query")
        if callback:
            self.accept_choice(callback)
            return
        message = update.get("message")
        if not message:
            return
        chat_id = message["chat"]["id"]
        text = message.get("text", "")
        start_code = start_code_of(text)
        if start_code is not None:
            self.reply(chat_id, self.confirm(start_code=start_code, sender=message.get("from", {})))
            return
        if text.strip() == START_COMMAND:
            self.reply(chat_id, REPLY_WHO, keyboard=who_keyboard())
            return
        self.reply(chat_id, REPLY_HELP)

    def accept_choice(self, callback):
        self.answer_callback(callback["id"])
        chat_id = callback["message"]["chat"]["id"]
        if callback.get("data") == CALLBACK_COACH:
            self.reply(chat_id, REPLY_COACH_ASKED)
            return
        self.reply(chat_id, REPLY_CLIENT)

def who_keyboard():
    return [
        [{"text": BUTTON_COACH, "callback_data": CALLBACK_COACH}],
        [{"text": BUTTON_CLIENT, "callback_data": CALLBACK_CLIENT}],
    ]


def is_poll_timeout(failure):
    if isinstance(failure, socket.timeout):
        return True
    return isinstance(failure, urllib.error.URLError) and isinstance(failure.reason, socket.timeout)


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
            if is_poll_timeout(failure):
                continue
            logger.error("цикл опроса упал: %s", failure)
            time.sleep(RETRY_DELAY_SECONDS)


if __name__ == "__main__":
    main()

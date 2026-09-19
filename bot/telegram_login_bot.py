#!/usr/bin/env python3
import json
import logging
import os
import socket
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime

TELEGRAM_API = "https://api.telegram.org"
POLL_TIMEOUT_SECONDS = 30
REQUEST_TIMEOUT_SECONDS = 45
RETRY_DELAY_SECONDS = 5
START_COMMAND = "/start"
HTTP_FORBIDDEN = 403
HTTP_CONFLICT = 409
HTTP_GONE = 410
KIND_LINK = "LINK"
BOT_SECRET_HEADER = "X-Telegram-Bot-Secret"
COACH_CALLBACK = "coach"
VERDICT_APPROVE = "approve"
VERDICT_DECLINE = "decline"
CALLBACK_PARTS = 3
ISO_DATE_LENGTH = 10

REPLY_CONFIRMED = "Готово. Вернитесь в приложение — вход завершится сам."
REPLY_LINKED = "Готово. Теперь входите в приложение кнопкой «Войти через Telegram»."
REPLY_LINK_DEAD = "Ссылка устарела. Начните вход в приложении заново."
REPLY_FAILED = "Не получилось подтвердить вход. Попробуйте ещё раз через минуту."
REPLY_HELP = "Этот бот подтверждает вход в приложение. Нажмите «Войти через Telegram» в приложении."

COACH_REQUEST_TITLE = "Заявка на тренера"
COACH_REQUEST_EMAIL = "Почта: {}"
COACH_REQUEST_LOGIN = "Логин: {}"
COACH_REQUEST_TELEGRAM = "Telegram: @{}"
COACH_REQUEST_SINCE = "В приложении с {}"
BUTTON_APPROVE = "Одобрить"
BUTTON_DECLINE = "Отклонить"
NOTE_APPROVED = "Одобрено — теперь это тренер."
NOTE_DECLINED = "Отклонено."
NOTE_ALREADY = "Заявка уже рассмотрена."
NOTE_NOT_OWNER = "Решать заявки может только владелец."
NOTE_FAILED = "Не получилось. Попробуйте ещё раз через минуту."
NOTE_UNKNOWN = "Эта кнопка больше не работает."
NOTES_THAT_CLOSE_REQUEST = (NOTE_APPROVED, NOTE_DECLINED, NOTE_ALREADY)
ISO_DATE_FORMAT = "%Y-%m-%d"
SHOWN_DATE_FORMAT = "%d.%m.%Y"

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
        self.admin_chat_id = require_env("TELEGRAM_ADMIN_CHAT_ID")


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


def get_json(url, headers=None):
    request = urllib.request.Request(url=url, headers=headers or {}, method="GET")
    with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
        return json.loads(response.read().decode("utf-8"))


class TelegramBot:

    def __init__(self, settings):
        self.settings = settings
        self.offset = None

    def telegram(self, method, payload):
        post_json(url=f"{TELEGRAM_API}/bot{self.settings.bot_token}/{method}", payload=payload)

    def api_headers(self):
        return {BOT_SECRET_HEADER: self.settings.bot_secret}

    def poll(self):
        url = f"{TELEGRAM_API}/bot{self.settings.bot_token}/getUpdates?timeout={POLL_TIMEOUT_SECONDS}"
        if self.offset is not None:
            url = f"{url}&offset={self.offset}"
        answer = get_json(url)
        if not answer.get("ok"):
            raise RuntimeError(f"Telegram отказал: {answer.get('description')}")
        return answer.get("result", [])

    def reply(self, chat_id, text):
        self.telegram("sendMessage", {"chat_id": chat_id, "text": text})

    def confirm(self, start_code, sender):
        payload = {
            "startCode": start_code,
            "telegramUserId": str(sender.get("id")),
            "telegramDisplayName": display_name_of(sender),
            "telegramUsername": sender.get("username"),
        }
        try:
            status, answer = post_json(
                url=f"{self.settings.api_url}/auth/telegram/confirm",
                payload=payload,
                headers=self.api_headers(),
            )
        except urllib.error.HTTPError as failure:
            logger.warning("сервер отказал: %s", failure.code)
            return REPLY_LINK_DEAD if failure.code == HTTP_GONE else REPLY_FAILED
        except urllib.error.URLError as failure:
            logger.error("сервер недоступен: %s", failure.reason)
            return REPLY_FAILED
        kind = (answer or {}).get("kind")
        logger.info("вход подтверждён, статус %s, вид %s", status, kind)
        return REPLY_LINKED if kind == KIND_LINK else REPLY_CONFIRMED

    def announce_coach_requests(self):
        requests = get_json(
            url=f"{self.settings.api_url}/telegram/coach-requests/unannounced",
            headers=self.api_headers(),
        )
        for request in requests:
            self.telegram("sendMessage", {
                "chat_id": self.settings.admin_chat_id,
                "text": coach_request_text(request),
                "reply_markup": {"inline_keyboard": [[
                    {"text": BUTTON_APPROVE, "callback_data": coach_callback(VERDICT_APPROVE, request["id"])},
                    {"text": BUTTON_DECLINE, "callback_data": coach_callback(VERDICT_DECLINE, request["id"])},
                ]]},
            })
            post_json(
                url=f"{self.settings.api_url}/telegram/coach-requests/{request['id']}/announced",
                payload={},
                headers=self.api_headers(),
            )
            logger.info("заявка на тренера отправлена владельцу")

    def decide(self, callback):
        parsed = parse_coach_callback(callback.get("data", ""))
        if parsed is None:
            self.telegram("answerCallbackQuery", {"callback_query_id": callback["id"], "text": NOTE_UNKNOWN})
            return
        approve, request_id = parsed
        note = self.send_decision(
            approve=approve,
            request_id=request_id,
            telegram_user_id=str(callback.get("from", {}).get("id")),
        )
        self.telegram("answerCallbackQuery", {"callback_query_id": callback["id"], "text": note})
        message = callback.get("message")
        if message and note in NOTES_THAT_CLOSE_REQUEST:
            self.telegram("editMessageText", {
                "chat_id": message["chat"]["id"],
                "message_id": message["message_id"],
                "text": f"{message.get('text', '')}\n\n{note}",
            })

    def send_decision(self, approve, request_id, telegram_user_id):
        try:
            post_json(
                url=f"{self.settings.api_url}/telegram/coach-requests/{request_id}/decision",
                payload={"approve": approve, "telegramUserId": telegram_user_id},
                headers=self.api_headers(),
            )
        except urllib.error.HTTPError as failure:
            logger.warning("решение по заявке не принято: %s", failure.code)
            if failure.code == HTTP_CONFLICT:
                return NOTE_ALREADY
            return NOTE_NOT_OWNER if failure.code == HTTP_FORBIDDEN else NOTE_FAILED
        except urllib.error.URLError as failure:
            logger.error("сервер недоступен: %s", failure.reason)
            return NOTE_FAILED
        logger.info("заявка на тренера решена")
        return NOTE_APPROVED if approve else NOTE_DECLINED

    def accept(self, update):
        self.offset = update["update_id"] + 1
        callback = update.get("callback_query")
        if callback:
            self.decide(callback)
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
        self.reply(chat_id, REPLY_HELP)


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


def coach_callback(verdict, request_id):
    return f"{COACH_CALLBACK}:{verdict}:{request_id}"


def parse_coach_callback(data):
    parts = data.split(":")
    if len(parts) != CALLBACK_PARTS or parts[0] != COACH_CALLBACK:
        return None
    if parts[1] not in (VERDICT_APPROVE, VERDICT_DECLINE):
        return None
    try:
        request_id = str(uuid.UUID(parts[2]))
    except ValueError:
        return None
    return parts[1] == VERDICT_APPROVE, request_id


def coach_request_text(request):
    lines = [COACH_REQUEST_TITLE, request["displayName"]]
    if request.get("email"):
        lines.append(COACH_REQUEST_EMAIL.format(request["email"]))
    if request.get("login"):
        lines.append(COACH_REQUEST_LOGIN.format(request["login"]))
    if request.get("telegramUsername"):
        lines.append(COACH_REQUEST_TELEGRAM.format(request["telegramUsername"]))
    registered = datetime.strptime(request["registeredAt"][:ISO_DATE_LENGTH], ISO_DATE_FORMAT)
    lines.append(COACH_REQUEST_SINCE.format(registered.strftime(SHOWN_DATE_FORMAT)))
    return "\n".join(lines)


def main():
    bot = TelegramBot(Settings())
    logger.info("бот запущен")
    while True:
        try:
            for update in bot.poll():
                bot.accept(update)
            bot.announce_coach_requests()
        except Exception as failure:
            if is_poll_timeout(failure):
                continue
            logger.error("цикл опроса упал: %s", failure)
            time.sleep(RETRY_DELAY_SECONDS)


if __name__ == "__main__":
    main()

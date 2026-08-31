#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=${LC_ALL:-C.UTF-8}

if [ $# -lt 1 ]; then
    echo "Использование: $0 <текст> [файл с дополнением]" >&2
    exit 2
fi

: "${TELEGRAM_BOT_TOKEN:?не задан TELEGRAM_BOT_TOKEN}"
: "${TELEGRAM_CHAT_ID:?не задан TELEGRAM_CHAT_ID}"

message_char_limit=3900
run_url="${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-}/actions/runs/${GITHUB_RUN_ID:-}"

message=$1
if [ $# -ge 2 ] && [ -s "$2" ]; then
    message=$(printf '%s\n\n%s' "$message" "$(cat "$2")")
fi

if [ "$(printf '%s' "$message" | wc -m)" -gt "$message_char_limit" ]; then
    trimmed=$(head -c "$message_char_limit" <<< "$message" | iconv -c -f utf-8 -t utf-8)
    message=$(printf '%s\n\n…текст обрезан, целиком — в прогоне: %s' "$trimmed" "$run_url")
fi

curl --silent --show-error --fail-with-body \
    --form "chat_id=${TELEGRAM_CHAT_ID}" \
    --form "text=${message}" \
    "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" > /dev/null

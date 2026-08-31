#!/usr/bin/env bash
set -euo pipefail

app_dir=/opt/trainer
backups_dir=$app_dir/backups
service=trainer
current_jar=$app_dir/trainer-backend.jar
previous_jar=$app_dir/trainer-backend.jar.bak
backup_script=$app_dir/backup.sh
health_url=${HEALTH_URL:-http://127.0.0.1:8080/actuator/health}
health_attempts=36
health_delay_seconds=5
minimal_jar_bytes=$((10 * 1024 * 1024))

incoming_jar=$(mktemp /tmp/trainer-backend-XXXXXX.jar)
trap 'rm -f "$incoming_jar"' EXIT

cat > "$incoming_jar"

if [ "$(stat -c %s "$incoming_jar")" -lt "$minimal_jar_bytes" ]; then
    echo "пришедший файл меньше $((minimal_jar_bytes / 1024 / 1024)) МБ — это не jar приложения" >&2
    exit 1
fi

if ! python3 -c 'import sys, zipfile; zipfile.ZipFile(sys.argv[1]).getinfo("META-INF/MANIFEST.MF")' "$incoming_jar"; then
    echo "пришедший файл не разбирается как jar с манифестом" >&2
    exit 1
fi

newest_backup() {
    find "$backups_dir" -maxdepth 1 -type f -printf '%T@ %f\n' 2>/dev/null | sort -rn | awk 'NR == 1 { print $2 }'
}

newest_backup_before=$(newest_backup)
"$backup_script"
newest_backup_after=$(newest_backup)

if [ "$newest_backup_after" = "$newest_backup_before" ]; then
    echo "дамп базы не появился в $backups_dir — выкатку не начинаю" >&2
    exit 1
fi
echo "дамп базы снят: $newest_backup_after"

cp "$current_jar" "$previous_jar"
install -o trainer -g trainer -m 644 "$incoming_jar" "$current_jar"
systemctl restart "$service"

attempt=1
while [ "$attempt" -le "$health_attempts" ]; do
    if curl --fail --silent --show-error --max-time 5 "$health_url" | grep -q '"status":"UP"'; then
        echo "сервис отвечает UP, попытка $attempt"
        systemctl is-active "$service" > /dev/null
        exit 0
    fi
    sleep "$health_delay_seconds"
    attempt=$((attempt + 1))
done

echo "за $((health_attempts * health_delay_seconds)) секунд сервис не ответил UP — возвращаю прежний jar" >&2
install -o trainer -g trainer -m 644 "$previous_jar" "$current_jar"
systemctl restart "$service"
journalctl -u "$service" --since '5 minutes ago' --no-pager | tail -60 >&2
echo "ВНИМАНИЕ: откат вернул код, но не схему базы. Применённые миграции остались." >&2
echo "Схема откатывается только из дампа $backups_dir/$newest_backup_after." >&2
exit 1

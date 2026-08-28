#!/usr/bin/env bash
# Ловит миграцию, которая создаёт объект с уже занятым именем: сегодня такая дважды уронила прод.
set -euo pipefail

MIGRATIONS_DIR="$(dirname "$0")/../src/main/resources/db/migration"
failures=0

names_created_by() {
    local file="$1"
    grep -ioE "create (unique )?index [a-z0-9_]+|create table [a-z0-9_]+" "$file" \
        | awk '{print tolower($NF)}' \
        | sort -u
}

for migration in "$MIGRATIONS_DIR"/V*.sql; do
    version="$(basename "$migration" | sed -E 's/^V([0-9]+)__.*/\1/')"
    while read -r name; do
        [ -z "$name" ] && continue
        for earlier in "$MIGRATIONS_DIR"/V*.sql; do
            earlier_version="$(basename "$earlier" | sed -E 's/^V([0-9]+)__.*/\1/')"
            [ "$earlier_version" -ge "$version" ] && continue
            if grep -iqE "create (unique )?index $name\b|create table $name\b" "$earlier"; then
                echo "V$version создаёт '$name', уже созданный в $(basename "$earlier")"
                failures=$((failures + 1))
            fi
        done
    done <<< "$(names_created_by "$migration")"
done

if [ "$failures" -gt 0 ]; then
    echo "Миграции переопределяют существующие объекты: $failures"
    exit 1
fi

echo "Имена объектов в миграциях не пересекаются"

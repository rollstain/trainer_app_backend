"""Ловит миграцию, которая создаёт объект с уже занятым именем: такая дважды уронила прод."""
import pathlib
import re
import sys

MIGRATIONS = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/db/migration"
CREATES = re.compile(r"create\s+(?:unique\s+)?index\s+([a-z0-9_]+)|create\s+table\s+([a-z0-9_]+)", re.I)
DROPS = re.compile(r"drop\s+(?:index|table)\s+(?:if\s+exists\s+)?([a-z0-9_]+)", re.I)
VERSION = re.compile(r"^V(\d+)__")


def names(pattern, text):
    return {group.lower() for match in pattern.finditer(text) for group in match.groups() if group}


def main():
    migrations = sorted(
        ((int(VERSION.match(path.name).group(1)), path) for path in MIGRATIONS.glob("V*.sql")),
        key=lambda pair: pair[0],
    )
    created_by = {}
    failures = []
    for version, path in migrations:
        text = path.read_text(encoding="utf-8")
        recreated = names(DROPS, text)
        for name in names(CREATES, text):
            owner = created_by.get(name)
            if owner and name not in recreated:
                failures.append(f"V{version} создаёт '{name}', уже созданный в {owner}")
            created_by[name] = path.name

    for failure in failures:
        print(failure)
    if failures:
        print(f"Миграции переопределяют существующие объекты: {len(failures)}")
        return 1
    print("Имена объектов в миграциях не пересекаются")
    return 0


if __name__ == "__main__":
    sys.exit(main())

"""Ловит миграцию, которая создаёт объект с уже занятым именем: такая дважды уронила прод."""
import pathlib
import re
import sys

MIGRATIONS = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/db/migration"
CREATE_INDEX = re.compile(r"create\s+(?:unique\s+)?index\s+([a-z0-9_]+)\s+on\s+([a-z0-9_]+)", re.I)
CREATE_TABLE = re.compile(r"create\s+table\s+([a-z0-9_]+)", re.I)
DROPS = re.compile(r"drop\s+(?:index|table)\s+(?:if\s+exists\s+)?([a-z0-9_]+)", re.I)
VERSION = re.compile(r"^V(\d+)__")


def main():
    migrations = sorted(
        ((int(VERSION.match(path.name).group(1)), path) for path in MIGRATIONS.glob("V*.sql")),
        key=lambda pair: pair[0],
    )
    created_by = {}
    table_of_index = {}
    failures = []
    for version, path in migrations:
        text = path.read_text(encoding="utf-8")
        dropped = {match.group(1).lower() for match in DROPS.finditer(text)}
        dropped |= {index for index, table in table_of_index.items() if table in dropped}

        created = [(match.group(1).lower(), match.group(2).lower()) for match in CREATE_INDEX.finditer(text)]
        created += [(match.group(1).lower(), None) for match in CREATE_TABLE.finditer(text)]
        for name, table in created:
            owner = created_by.get(name)
            if owner and name not in dropped:
                failures.append(f"V{version} создаёт '{name}', уже созданный в {owner}")
            created_by[name] = path.name
            if table is not None:
                table_of_index[name] = table

    for failure in failures:
        print(failure)
    if failures:
        print(f"Миграции переопределяют существующие объекты: {len(failures)}")
        return 1
    print("Имена объектов в миграциях не пересекаются")
    return 0


if __name__ == "__main__":
    sys.exit(main())

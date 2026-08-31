#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=${LC_ALL:-C.UTF-8}

notes_file=${1:?не указан файл для списка изменений}
run_number=${GITHUB_RUN_NUMBER:-1}
max_listed_commits=30

if [ "${GITHUB_REF_TYPE:-}" = "tag" ]; then
    tag=${GITHUB_REF_NAME}
    version=${tag#v}
    previous_tag=$(git describe --tags --abbrev=0 "${tag}^" 2>/dev/null || echo "")
    range_end=$tag
else
    tag=""
    previous_tag=$(git describe --tags --abbrev=0 2>/dev/null || echo "")
    base_version=${previous_tag:-v0.0.0}
    version="${base_version#v}-dev.${run_number}"
    range_end=HEAD
fi

if [ -n "$previous_tag" ]; then
    range="${previous_tag}..${range_end}"
    header="Изменения с ${previous_tag}:"
else
    range="$range_end"
    header="Изменения с начала истории:"
fi

total_commits=$(git log --no-merges --oneline "$range" | wc -l | tr -d ' ')

{
    echo "$header"
    git log --no-merges -n "$max_listed_commits" --pretty=format:'• %s' "$range"
    echo
    if [ "$total_commits" -gt "$max_listed_commits" ]; then
        echo "…и ещё $((total_commits - max_listed_commits)) коммитов"
    fi
} > "$notes_file"

{
    echo "version=${version}"
    echo "tag=${tag}"
    echo "previous_tag=${previous_tag}"
} >> "${GITHUB_OUTPUT:-/dev/stdout}"

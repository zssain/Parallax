#!/usr/bin/env bash
#
# Verifies that every repo-relative path mentioned in the skill files actually exists. It scans the three
# SKILL.md files for inline code spans (`like/this`), keeps the ones that look like a repo-relative path
# (contain a slash, no leading slash, no spaces — so API routes like /api/v1/... , URLs, emails and code
# identifiers are ignored), and fails if any such path is missing. Run from anywhere.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SKILLS=(
  skills/add-scorecard-attribute/SKILL.md
  skills/add-policy-rule/SKILL.md
  skills/run-strategy-replay/SKILL.md
)

missing=0
checked=0

for skill in "${SKILLS[@]}"; do
  if [[ ! -f "$skill" ]]; then
    echo "MISSING SKILL FILE: $skill"
    missing=$((missing + 1))
    continue
  fi
  # Drop fenced code blocks, then pull every inline code span, one per line.
  paths=$(awk '/^```/{f=!f; next} !f' "$skill" \
    | grep -oE '`[^`]+`' \
    | sed -E 's/^`|`$//g' \
    | grep -E '^[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+)+$' || true)
  while IFS= read -r p; do
    [[ -z "$p" ]] && continue
    checked=$((checked + 1))
    if [[ -e "$p" ]]; then
      echo "OK   $skill -> $p"
    else
      echo "FAIL $skill -> $p (does not exist)"
      missing=$((missing + 1))
    fi
  done <<< "$paths"
done

echo "---"
echo "checked $checked path(s), $missing missing"
if [[ "$missing" -gt 0 ]]; then
  exit 1
fi
echo "All skill paths exist."

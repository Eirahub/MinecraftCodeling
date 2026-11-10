#!/usr/bin/env bash
# Text checks that fail CI (issue #137). Run from anywhere in the repo: bash scripts/check-text.sh
set -u
cd "$(git rev-parse --show-toplevel)" || exit 2
fail=0

# 1. No em dash (U+2014) in any tracked text file. The pattern is built from its UTF-8 bytes,
# so this script does not contain the character. -I skips binary files.
# No tracked file had one on 2026-11-09, so nothing is excluded. If a vendored or generated file
# ever needs one, exclude its path here with a ':!path' pathspec and a comment saying why.
emdash=$(printf '\342\200\224')
if git grep -n -I -F -e "$emdash" -- .; then
    echo "check-text: em dash (U+2014) in the lines above. Rewrite the sentence." >&2
    fail=1
fi

# 2. These files must stay LF (issue #137). -U keeps the CR visible to grep on Windows (Git Bash).
for f in target/sim/api.ts target/libs/core/ns.ts target/libs/core/enums.d.ts; do
    if grep -U -q $'\r' "$f"; then
        echo "check-text: CRLF line endings in $f. Convert it to LF." >&2
        fail=1
    fi
done

[ "$fail" -eq 0 ] && echo "check-text: ok"
exit "$fail"

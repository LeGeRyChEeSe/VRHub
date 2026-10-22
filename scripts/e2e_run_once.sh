#!/usr/bin/env bash
# Single timed run for the E2E-10 stability check (3 consecutive clean runs).
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
n="$1"
s=$(date +%s)
bash "$ROOT/scripts/e2e.sh" > "$ROOT/e2e/artifacts/run$n.log" 2>&1
rc=$?
d=$(( $(date +%s) - s ))
echo "run $n: rc=$rc duration=${d}s"
tail -2 "$ROOT/e2e/artifacts/run$n.log"
exit $rc

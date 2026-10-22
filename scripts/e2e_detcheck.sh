#!/usr/bin/env bash
# E2E-7 check: hashes recorded in e2e/fixtures/e2e.env + e2e/run/e2e.gen.env
# must match the bytes on disk in e2e/run (what the server actually serves).
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# Windows tools (7z/python) must be reachable from PATH: re-exec under Git Bash otherwise.
. "$ROOT/scripts/e2e_lib.sh"
e2e_reexec_gitbash "$@"
e2e_python
"$PY" scripts/e2e_fixtures.py > /dev/null
"$PY" scripts/e2e_detcheck.py

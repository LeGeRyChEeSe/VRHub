#!/usr/bin/env bash
# Shared helpers for the E2E harness scripts (e2e.sh, e2e_detcheck.sh).
# Requires an MSYS/Git-Bash shell at runtime: Windows tools (netstat/taskkill/
# adb/emulator/7z) are resolved from PATH. Re-exec under Git Bash otherwise.

e2e_reexec_gitbash() {
  case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) return 0 ;;
  esac
  if [ -n "${E2E_REEXEC:-}" ]; then
    echo "E2E FAIL: Git Bash re-exec loop"; exit 1
  fi
  for gb in "${GIT_BASH:-}" "/mnt/c/Program Files/Git/bin/bash.exe" \
            "/mnt/c/Program Files (x86)/Git/bin/bash.exe"; do
    [ -n "$gb" ] && [ -x "$gb" ] || continue
    # Translate the WSL mount path to a Windows path Git Bash can open.
    wp="$0"
    case "$wp" in
      /mnt/*) wp="$(echo "$wp" | sed 's#^/mnt/\([a-z]\)/#\1:/#')" ;;
    esac
    export E2E_REEXEC=1
    exec "$gb" "$wp" "$@"
  done
  echo "E2E FAIL: run under Git Bash (MSYS); set GIT_BASH=/path/to/bash.exe if Git lives elsewhere"
  exit 1
}

# Load key=value env files (last one wins), stripping CR so values never carry
# a trailing carriage return into adb/am instrument args.
e2e_load_env() {
  for f in "$@"; do
    [ -f "$f" ] || continue
    while IFS='=' read -r k v; do
      [ -z "$k" ] && continue
      case "$k" in \#*) continue ;; esac
      k="${k%$'\r'}"
      v="${v%$'\r'}"
      export "$k=$v"
    done < <(grep -v '^#' "$f")
  done
}

e2e_python() {
  PY="${PY:-$(command -v python || command -v python3)}"
  [ -z "$PY" ] && { echo "E2E FAIL: python interpreter not found"; exit 1; }
}

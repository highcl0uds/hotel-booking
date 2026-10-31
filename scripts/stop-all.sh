#!/usr/bin/env bash
# Останавливает сервисы, запущенные scripts/start-all.sh.
cd "$(dirname "$0")/.."

for pid_file in logs/*.pid; do
  [[ -e "$pid_file" ]] || continue
  pid=$(cat "$pid_file")
  if kill "$pid" 2> /dev/null; then
    echo "stopped $(basename "$pid_file" .pid) (pid $pid)"
  fi
  rm -f "$pid_file"
done

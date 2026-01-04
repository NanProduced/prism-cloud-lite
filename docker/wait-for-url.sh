#!/usr/bin/env sh
set -eu

URL="${1:-}"
EXPECT_TEXT="${2:-}"
TIMEOUT_SECONDS="${3:-120}"
INTERVAL_SECONDS="${4:-2}"

if [ -z "$URL" ]; then
  echo "[wait-for-url] URL is required" >&2
  exit 2
fi

echo "[wait-for-url] Waiting for $URL (timeout=${TIMEOUT_SECONDS}s interval=${INTERVAL_SECONDS}s)"

start_epoch="$(date +%s)"
while :; do
  if [ -z "$EXPECT_TEXT" ]; then
    if curl -fsS "$URL" >/dev/null 2>&1; then
      echo "[wait-for-url] OK: $URL"
      exit 0
    fi
  else
    if curl -fsS "$URL" 2>/dev/null | grep -q "$EXPECT_TEXT"; then
      echo "[wait-for-url] OK: $URL"
      exit 0
    fi
  fi

  now_epoch="$(date +%s)"
  elapsed="$((now_epoch - start_epoch))"
  if [ "$elapsed" -ge "$TIMEOUT_SECONDS" ]; then
    echo "[wait-for-url] TIMEOUT after ${TIMEOUT_SECONDS}s: $URL" >&2
    exit 1
  fi
  sleep "$INTERVAL_SECONDS"
done

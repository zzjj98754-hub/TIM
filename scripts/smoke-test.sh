#!/usr/bin/env bash
set -euo pipefail
docker compose config >/dev/null
docker compose up -d --build
trap 'docker compose down' EXIT
for port in 8081 8082; do
  for i in $(seq 1 30); do
    if curl -fsS "http://localhost:${port}/actuator/health" >/dev/null; then break; fi
    if [ "$i" = 30 ]; then echo "TIM node health check failed on ${port}" >&2; exit 1; fi
    sleep 2
  done
done
echo "TIM node health checks passed"

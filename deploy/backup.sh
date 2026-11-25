#!/usr/bin/env bash
set -euo pipefail

# 每日凌晨 3 点自动备份建议添加到 root crontab（日志输出至 journald）：
# 0 3 * * * /opt/ghtrending/deploy/backup.sh 2>&1 | systemd-cat -t ghtrending-backup

BACKUP_DIR="/var/backups"
RETENTION_DAYS=7
COMPOSE_FILE="${COMPOSE_FILE:-/opt/ghtrending/deploy/docker-compose.yml}"
ENV_FILE="${ENV_FILE:-/opt/ghtrending/backend/.env}"
DB_USER="${POSTGRES_USER:-ghtrending}"
DB_NAME="${POSTGRES_DB:-ghtrending}"

mkdir -p "${BACKUP_DIR}"

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
TARGET_FILE="${BACKUP_DIR}/ghtrending-${TIMESTAMP}.sql.gz"

echo "{\"event\":\"backup_started\",\"target\":\"${TARGET_FILE}\"}"

docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" exec -T postgres \
  pg_dump -U "${DB_USER}" -d "${DB_NAME}" --clean --if-exists | gzip -9 > "${TARGET_FILE}"

# 仅保留最近 7 天的备份文件
find "${BACKUP_DIR}" -maxdepth 1 -name "ghtrending-*.sql.gz" -type f -mtime +"${RETENTION_DAYS}" -delete

echo "{\"event\":\"backup_finished\",\"target\":\"${TARGET_FILE}\"}"

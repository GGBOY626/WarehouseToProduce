#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="${PROJECT_DIR:-/opt/warehouse-system/compose}"
BACKUP_DIR="${BACKUP_DIR:-/opt/warehouse-system/data/backups}"
UPLOAD_DIR="${UPLOAD_HOST_DIR:-/opt/warehouse-system/data/uploads}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP_DIR"
cd "$PROJECT_DIR"
docker compose exec -T mysql sh -c 'exec mysqldump --single-transaction --routines --triggers -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' | gzip > "$BACKUP_DIR/mysql-$STAMP.sql.gz"
tar -C "$UPLOAD_DIR" -czf "$BACKUP_DIR/uploads-$STAMP.tar.gz" .
find "$BACKUP_DIR" -type f \( -name 'mysql-*.sql.gz' -o -name 'uploads-*.tar.gz' \) -mtime "+$RETENTION_DAYS" -delete
echo "Backup completed: $STAMP"

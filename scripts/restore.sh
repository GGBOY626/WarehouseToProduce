#!/usr/bin/env bash
set -euo pipefail
if [ "$#" -ne 2 ]; then echo "Usage: $0 mysql-backup.sql.gz uploads-backup.tar.gz"; exit 2; fi
PROJECT_DIR="${PROJECT_DIR:-/opt/warehouse-system/compose}"
UPLOAD_DIR="${UPLOAD_HOST_DIR:-/opt/warehouse-system/data/uploads}"
read -r -p "This overwrites the target database and upload directory. Type RESTORE: " answer
[ "$answer" = "RESTORE" ] || exit 1
cd "$PROJECT_DIR"
gzip -dc "$1" | docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
mkdir -p "$UPLOAD_DIR"
tar -C "$UPLOAD_DIR" -xzf "$2"
echo "Restore completed."

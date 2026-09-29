# 仓库 ↔ 生产车间物料流转记录系统

手机优先的内部物料交接系统。前端为 Vue 3，后端为 Java 21 / Spring Boot，数据库为 MySQL 8。

## 本地开发

1. 复制 `.env.example` 为 `.env`，仅本机保存真实密码。
2. 启动 MySQL：`docker compose -f docker-compose.dev.yml up -d`。
3. 后端：进入 `backend`，确认 Maven 使用 Java 21，然后运行 `mvn spring-boot:run`。
4. 前端：进入 `frontend`，运行 `npm install` 和 `npm run dev`。
5. 打开 `http://localhost:5173`。

Flyway 会执行 `V1__init_schema.sql`。正式环境使用 `ddl-auto=validate`，不要修改已经执行过的 Migration。

## 生产部署

宿主机准备：

```text
/opt/warehouse-system/compose
/opt/warehouse-system/data/uploads
/opt/warehouse-system/data/backups
```

将项目放入 compose 目录，创建 `.env`，然后运行 `docker compose up -d --build`。默认通过 `0.0.0.0:8088` 提供访问；如使用宿主机 nginx 提供 HTTPS，请将 `APP_BIND_ADDRESS` 改为 `127.0.0.1`。修改 `nginx/warehouse.conf` 中的域名和证书路径后启用配置。

PDF 依赖中文字体。后端镜像安装 Noto CJK，并通过 `PDF_FONT_PATH` 指定；首次部署后应实际导出一份中文 PDF 验证字体兼容。如果当前发行版字体文件路径不同，请将一个开源中文 TTF/TTC 文件挂载到容器并更新变量。

## 备份

脚本会分别备份 MySQL 和图片。建议每天凌晨执行：

```cron
20 2 * * * PROJECT_DIR=/opt/warehouse-system/compose /opt/warehouse-system/compose/scripts/backup.sh >> /var/log/warehouse-backup.log 2>&1
```

默认保留 30 天。上线前及之后定期在非生产环境执行 `scripts/restore.sh` 验证备份可恢复。

## 关键配置

所有敏感配置通过环境变量提供，参考 `.env.example`。不要提交 `.env`、数据库备份或上传图片。

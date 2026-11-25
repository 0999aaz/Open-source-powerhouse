# 部署配置与运维清单（deploy）

本目录包含面向生产的全部即用配置：`docker-compose.yml`、`Caddyfile`、三份 `systemd` 单元文件以及数据库定时备份脚本。

> **多架构构建策略说明**：自建 `api` 服务默认采用**目标服务器本地 `docker compose build`**，无需申请外部容器镜像仓库推送权限，且可在 Oracle Ampere A1（`linux/arm64`）或标准 `linux/amd64` 主机上直接原生构建。`postgres`、`redis`、`caddy` 均已锁定支持双架构的官方镜像标签，并在 `docker-compose.yml` 中附带 `platform` 兜底注释。

---

## 交给 Muse AI 逐步执行的部署步骤清单

### 第 1 步：准备服务器与目录

```bash
# 安装 Docker 与 Compose 插件后，将代码检出到 /opt/ghtrending
git clone <YOUR_REPO_URL> /opt/ghtrending
cd /opt/ghtrending
git checkout v0.1.0-code-complete
```

### 第 2 步：配置环境变量（全量 8 项）

```bash
cp backend/.env.example backend/.env
chmod 600 backend/.env
```

编辑 `backend/.env`，确保以下 **8 个变量**均已正确填写（`DATABASE_URL` 中的密码须与 `POSTGRES_PASSWORD` 保持一致）：

- `DATABASE_URL=postgresql+asyncpg://ghtrending:<STRONG_PASSWORD>@postgres:5432/ghtrending`
- `REDIS_URL=redis://redis:6379/0`
- `GITHUB_TOKEN=<YOUR_GITHUB_PAT>`
- `MIN_CLIENT_VERSION=1.0.0`
- `UPGRADE_URL=https://github.com/<owner>/<repo>/releases/latest`
- `DOMAIN=<your.domain.com>`
- `ACME_EMAIL=<ops@your.domain.com>`
- `POSTGRES_PASSWORD=<STRONG_PASSWORD>`

### 第 3 步：校验配置并在目标机本地构建启动

```bash
docker compose --env-file backend/.env -f deploy/docker-compose.yml config
docker compose --env-file backend/.env -f deploy/docker-compose.yml build
docker compose --env-file backend/.env -f deploy/docker-compose.yml up -d
```

> `api` 启动命令会自动执行 `alembic upgrade head` 后再拉起 `uvicorn`。

### 第 4 步：验证核心服务健康与首次采集

```bash
# 查看四服务均处于 healthy
docker compose -f deploy/docker-compose.yml ps

# 手动触发一次单语言采集验证入库，随后可跑一次全量（或交由 timer 自动跑）
docker compose --env-file backend/.env -f deploy/docker-compose.yml exec -T api \
  python collector/run.py --period daily --language kotlin
```

### 第 5 步：安装并启用 `systemd` 单元（含每 30 分钟采集）

```bash
sudo cp deploy/ghtrending.service deploy/ghtrending-collector.service deploy/ghtrending-collector.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now ghtrending.service
sudo systemctl enable --now ghtrending-collector.timer

# 查看定时器状态与日志
systemctl status ghtrending-collector.timer
journalctl -u ghtrending-collector.service -n 50 --no-pager
```

### 第 6 步：配置每日数据库备份

```bash
chmod +x /opt/ghtrending/deploy/backup.sh
# 添加至 root crontab（每日凌晨 3 点备份至 /var/backups 并保留 7 天）
(sudo crontab -l 2>/dev/null; echo "0 3 * * * /opt/ghtrending/deploy/backup.sh 2>&1 | systemd-cat -t ghtrending-backup") | sudo crontab -
```

### 第 7 步：公网 HTTPS 验收与交付回报

```bash
curl -i "https://${DOMAIN}/healthz"
curl -i -H "X-Client-Version: 1.0.0" "https://${DOMAIN}/v1/languages"
curl -s -H "X-Client-Version: 1.0.0" "https://${DOMAIN}/v1/trending?period=daily&language=kotlin&limit=5"
```

**交付要求**：部署并验收成功后，请在交付报告中明确返回 **`https://<domain>`**，以便客户端侧回填 `android/gradle.properties` 中的 `API_BASE_URL` 并重出验收 APK。

# GitHub Trending 开源趋势浏览平台

## 交接给部署 Agent（Muse AI）Checklist

> **交接里程碑**：`v0.1.0-code-complete`（代码与部署配置已就绪，等待生产部署与域名回填）

- [ ] 确认目标服务器为 Linux（`linux/arm64` 如 Oracle Ampere A1 或 `linux/amd64`），已安装 Docker Engine、Docker Compose v2 与 `systemd`
- [ ] 将域名 DNS（A / AAAA 记录）解析至服务器公网 IP，并放行防火墙 `80/tcp`、`443/tcp`、`443/udp`
- [ ] 按 [`deploy/README.md`](deploy/README.md) 逐步执行部署：克隆代码至 `/opt/ghtrending`，复制 `backend/.env.example` 为 `backend/.env` 并填齐全部 **8 个必填环境变量**
- [ ] 在目标服务器本地执行 `docker compose build` 与 `up -d`（无需外部容器仓库权限，天然支持 ARM64 / AMD64）
- [ ] 启用 `systemd` 定时器每 30 分钟运行采集器，并配置 `deploy/backup.sh` 每日备份
- [ ] 验收 `https://<domain>/healthz` 返回 `{"status":"ok"}` 且 `/v1/trending` 有数据
- [ ] **在交付报告中明确返回 `https://<domain>`**，以便客户端侧回填 `API_BASE_URL`、打正式 tag 并重出验收 APK

---

## 系统架构图

```text
┌───────────────────────────┐       HTTPS (仅访问我方域名)        ┌──────────────────────────────┐
│     Android 客户端         │ ◄─────────────────────────────────► │      Caddy 反向代理          │
│  Compose + Room + Paging3 │   X-Client-Version / ETag / 304     │  自动 TLS / 透传真实 IP       │
│  WebView (禁JS/禁文件访问) │                                     └──────────────┬───────────────┘
└─────────────▲─────────────┘                                                    │
              │ 部署后闭环：回填 https://<domain>                                  ▼
              │ 更新 gradle.properties → 正式 tag → 验收 APK      ┌──────────────────────────────┐
┌─────────────┴─────────────┐                                     │      FastAPI 核心服务         │
│  一次性采集脚本 (每30分)    │ ◄─────────────────────────────────► │  (本地 docker compose build) │
│  Trending HTML + Search   │       读缓存 / 写限流 / ETag         │  README 清洗 / 游标 / 426/429 │
└─────────────┬─────────────┘                                     └──────┬───────────────┬───────┘
              │                                                          │               │
              ▼                                                          ▼               ▼
┌───────────────────────────┐                                   ┌────────────────┐ ┌─────────────┐
│    GitHub 外部数据源       │                                   │ PostgreSQL 16  │ │   Redis 7   │
│  Trending HTML / API      │                                   │ repos/snapshots│ │ 限流/ETag/  │
│  (仅服务端持 Token)        │                                   │ NULL=全语言榜  │ │ README 缓存 │
└───────────────────────────┘                                   └────────────────┘ └─────────────┘
```

---

## 目录说明

- `backend/`：Python 3.12 + FastAPI + SQLAlchemy 2.0 + Alembic + 一次性采集脚本（详见 [`backend/README.md`](backend/README.md)）
- `android/`：Kotlin 2.x + Jetpack Compose + Material 3 + Room + Paging 3 + Hilt 客户端（详见 [`android/README.md`](android/README.md)）
- `deploy/`：Docker Compose、Caddyfile、systemd 单元、数据库备份脚本（详见 [`deploy/README.md`](deploy/README.md)）
- `docs/`：中文隐私政策草稿（[`docs/PRIVACY_POLICY.md`](docs/PRIVACY_POLICY.md)）与 Google Play 上架素材清单（[`docs/PLAY_STORE_CHECKLIST.md`](docs/PLAY_STORE_CHECKLIST.md)）

---

## 本地启动步骤

### 1. 启动 Postgres 16 与 Redis 7

确保本地 Postgres 16（库名 `ghtrending`、用户 `ghtrending`）与 Redis 7 已就绪，或通过 Docker 临时拉起依赖服务后进入 `backend/`：

```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# 按本地连接串修改 backend/.env 中的 DATABASE_URL 与 REDIS_URL
alembic upgrade head
```

### 2. 运行一次采集任务

```bash
python collector/run.py --period daily --language kotlin
```

### 3. 启动 API 服务与 `curl` 验收

```bash
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

在另一终端执行接口验收：

```bash
curl -i http://127.0.0.1:8000/healthz
curl -i -H "X-Client-Version: 1.0.0" http://127.0.0.1:8000/v1/languages
curl -s -H "X-Client-Version: 1.0.0" "http://127.0.0.1:8000/v1/trending?period=daily&language=kotlin&limit=5"
```

### 4. 本地联调 Android 客户端

修改 `android/gradle.properties` 中的 `API_BASE_URL=http://10.0.2.2:8000`（模拟器访问宿主机），在 `android/` 下编译运行：

```bash
cd android
./gradlew :app:assembleDebug
```

验收完成后请将 `API_BASE_URL` 恢复为 `https://REPLACE_ME`。

---

## 运维指南

### 如何修改采集选择器

若 GitHub Trending 页面 DOM 结构调整，只需修改服务端解析器 `backend/collector/parser.py` 中的 `parse_trending_html()`（默认选择器：`article.Box-row`、`h2 a`、`p.col-9`、`[itemprop=programmingLanguage]`、`a[href$="/stargazers"]`、`a[href$="/forks"]`、`span.d-inline-block.float-sm-right`），无需改动 Android 客户端。在主源解析返回 0 条时，采集器会自动降级至 GitHub Search API。

### 如何轮换 `GITHUB_TOKEN`

1. 在 GitHub 生成新的 Fine-grained Personal Access Token（仅需公共仓库只读权限，无敏感写权限）。
2. 编辑服务器 `/opt/ghtrending/backend/.env` 更新 `GITHUB_TOKEN=<new_token>`。
3. 重建重启 `api` 容器使内存配置生效：
   ```bash
   docker compose --env-file /opt/ghtrending/backend/.env -f /opt/ghtrending/deploy/docker-compose.yml up -d api
   ```
4. `ghtrending-collector.service` 为一次性任务，下一次 `systemd timer` 拉起时会自动读取最新 `.env`。

---

## 如何交由部署 Agent 上线

### 服务器前置条件

- 操作系统：Ubuntu 22.04+ / Debian 12+（支持 `linux/arm64` 或 `linux/amd64`）
- 软件环境：Docker Engine、Docker Compose Plugin (`docker compose`)、`systemd`、`bash`
- 网络与域名：已备案/可解析的公网域名（A/AAAA 指向服务器 IP），入站开放 `80/tcp`、`443/tcp`、`443/udp`

### 必填环境变量清单（`backend/.env` 全量 8 项）

| 变量名 | 说明 | 示例值 |
| --- | --- | --- |
| `DATABASE_URL` | 异步 PostgreSQL 连接串 | `postgresql+asyncpg://ghtrending:STRONG_PWD@postgres:5432/ghtrending` |
| `REDIS_URL` | Redis 连接串 | `redis://redis:6379/0` |
| `GITHUB_TOKEN` | 服务端调用 GitHub API 的凭证 | `github_pat_xxx` |
| `MIN_CLIENT_VERSION` | 最低允许客户端版本（低于触发 426） | `1.0.0` |
| `UPGRADE_URL` | 426 响应体中的更新下载地址 | `https://github.com/<owner>/<repo>/releases/latest` |
| `DOMAIN` | Caddy 自动签发 TLS 的域名 | `trending.example.com` |
| `ACME_EMAIL` | Let's Encrypt 证书通知邮箱 | `ops@example.com` |
| `POSTGRES_PASSWORD` | Postgres 容器初始化密码（需与 `DATABASE_URL` 一致） | `STRONG_PWD` |

---

## 部署后如何回填 `API_BASE_URL`（交付闭环）

1. **Muse AI 部署并交付**：Muse AI 按 [`deploy/README.md`](deploy/README.md) 部署并验收通过后，在交付报告中给出线上域名 `https://<domain>`。
2. **回填客户端配置**：在代码仓库中修改 `android/gradle.properties`：
   ```properties
   API_BASE_URL=https://<domain>
   ```
3. **打正式版本 tag 并重出验收 APK**：
   ```bash
   git add android/gradle.properties
   git commit -m "chore(android): backfill production API_BASE_URL"
   git tag -a v1.0.0 -m "Release v1.0.0 pointing to https://<domain>"
   cd android && ./gradlew :app:assembleRelease   # 或 assembleDebug 用于内部验收
   ```
4. **验收范围说明**：重新构建的 APK **仅供内部真机闭环验收使用，不对外分发**。

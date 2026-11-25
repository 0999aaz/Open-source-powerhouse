# 后端服务与采集器（backend）

基于 Python 3.12 + FastAPI + SQLAlchemy 2.0 (async) + Alembic + PostgreSQL 16 + Redis 7 构建的 GitHub Trending 采集与只读 API 服务。

## 目录结构

- `app/core/`：环境变量配置、异步数据库连接、30 种主流语言常量表、结构化 JSON 日志、统一中文异常处理
- `app/models/`：`Repo` 与 `Snapshot` ORM 模型（全语言总榜 `language` 统一存 `NULL`）
- `app/services/`：`ON CONFLICT (id) DO UPDATE` 幂等持久化、`(rank, id)` Base64 游标编解码、`nh3` README HTML 安全清洗
- `app/api/`：`/healthz` 与 `/v1/*` 路由、Redis 单 IP 限流（60 次/分）、`X-Client-Version` 426 校验、ETag/304 协商缓存
- `collector/`：一次性采集脚本（`run.py`）与 `selectolax` HTML 解析器（`parser.py`）
- `migrations/`：Alembic 数据库迁移脚本

## 本地开发与测试

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env

# 1. 数据库迁移
alembic upgrade head

# 2. 执行采集（单语言或省略参数跑全量）
python collector/run.py --period daily --language kotlin

# 3. 启动 API
uvicorn app.main:app --host 127.0.0.1 --port 8000

# 4. 运行测试
pytest -v
```

## 交给 Muse AI 部署的步骤清单

1. 确认 `backend/.env` 已由 `backend/.env.example` 复制创建，且 **8 个变量**全部填妥：`DATABASE_URL`、`REDIS_URL`、`GITHUB_TOKEN`、`MIN_CLIENT_VERSION`、`UPGRADE_URL`、`DOMAIN`、`ACME_EMAIL`、`POSTGRES_PASSWORD`。
2. 确认 `DATABASE_URL` 中的密码与 `POSTGRES_PASSWORD` 完全一致，且主机名为 Compose 内部服务名 `postgres`（Redis 主机名为 `redis`）。
3. 切勿将 `.env` 提交至 Git 仓库；所有容器日志直接输出至 `stdout`，由 Docker / journald 统一收集。
4. 实际容器编排、TLS 反代、定时采集与备份请严格按 [`../deploy/README.md`](../deploy/README.md) 执行。

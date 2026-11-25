import logging
from urllib.parse import quote

import httpx
from fastapi import APIRouter, Depends, Query, Request, Response
from redis.asyncio import Redis
from sqlalchemy import and_, func, or_, select, text
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.dependencies import (
    build_cacheable_json_response,
    enforce_client_version,
    enforce_rate_limit,
    get_redis_client,
)
from app.core.config import Settings, get_settings
from app.core.database import get_db
from app.core.exceptions import AppError
from app.core.languages import SUPPORTED_LANGUAGE_CODES, SUPPORTED_LANGUAGES, VALID_PERIODS
from app.models.repo import Repo
from app.models.snapshot import Snapshot
from app.schemas.api import (
    HealthResponse,
    LanguageOut,
    LanguagesResponse,
    RepoDetailResponse,
    RepoReadmeResponse,
    TrendingRepoItem,
    TrendingResponse,
)
from app.services.cursor import decode_cursor, encode_cursor
from app.services.html_sanitizer import sanitize_readme_html

logger = logging.getLogger("ghtrending.api")

README_CACHE_TTL_SECONDS = 6 * 3600

root_router = APIRouter()
v1_router = APIRouter(
    prefix="/v1",
    dependencies=[
        Depends(enforce_rate_limit),
        Depends(enforce_client_version),
    ],
)


@root_router.get("/healthz", response_model=HealthResponse)
async def healthz(
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> HealthResponse:
    try:
        await db.execute(text("SELECT 1"))
    except Exception as exc:
        logger.error(
            "Database health probe failed",
            extra={"event": "healthz_db_error", "error": str(exc)},
        )
        raise AppError(503, "DB_UNAVAILABLE", "数据库连接不可用") from exc

    redis_client = get_redis_client(request)
    if redis_client is None:
        raise AppError(503, "REDIS_UNAVAILABLE", "缓存服务未初始化")
    try:
        await redis_client.ping()
    except Exception as exc:
        logger.error(
            "Redis health probe failed",
            extra={"event": "healthz_redis_error", "error": str(exc)},
        )
        raise AppError(503, "REDIS_UNAVAILABLE", "缓存服务不可用") from exc

    return HealthResponse(status="ok")


@v1_router.get("/languages", response_model=LanguagesResponse)
async def list_languages(request: Request) -> Response:
    payload = LanguagesResponse(
        items=[LanguageOut(**item) for item in SUPPORTED_LANGUAGES]
    )
    return build_cacheable_json_response(request, payload)


@v1_router.get("/trending", response_model=TrendingResponse)
async def get_trending(
    request: Request,
    period: str = Query(default="daily"),
    language: str | None = Query(default=None),
    cursor: str | None = Query(default=None),
    limit: int = Query(default=20, ge=1, le=50),
    db: AsyncSession = Depends(get_db),
) -> Response:
    normalized_period = period.strip().lower()
    if normalized_period not in VALID_PERIODS:
        raise AppError(400, "INVALID_PERIOD", "不支持的时间周期，仅支持 daily、weekly、monthly")

    normalized_language: str | None = None
    if language is not None and language.strip() != "":
        candidate = language.strip().lower()
        if candidate not in SUPPORTED_LANGUAGE_CODES:
            raise AppError(400, "INVALID_LANGUAGE", "不支持的编程语言筛选条件")
        normalized_language = candidate

    cursor_rank: int | None = None
    cursor_id: int | None = None
    if cursor is not None and cursor.strip() != "":
        cursor_rank, cursor_id = decode_cursor(cursor.strip())

    # Latest snapshot is scoped per (period, language) group so languages never overwrite one another.
    if normalized_language is None:
        lang_filter = Snapshot.language.is_(None)
    else:
        lang_filter = Snapshot.language == normalized_language

    latest_captured_subq = (
        select(func.max(Snapshot.captured_at))
        .where(Snapshot.period == normalized_period, lang_filter)
        .scalar_subquery()
    )

    stmt = (
        select(
            Repo.id,
            Repo.full_name,
            Repo.description,
            Repo.language,
            Repo.stars,
            Snapshot.period_stars,
            Snapshot.rank,
        )
        .join(Repo, Snapshot.repo_id == Repo.id)
        .where(
            Snapshot.period == normalized_period,
            lang_filter,
            Snapshot.captured_at == latest_captured_subq,
        )
    )

    if cursor_rank is not None and cursor_id is not None:
        stmt = stmt.where(
            or_(
                Snapshot.rank > cursor_rank,
                and_(Snapshot.rank == cursor_rank, Repo.id > cursor_id),
            )
        )

    stmt = stmt.order_by(Snapshot.rank.asc(), Repo.id.asc()).limit(limit + 1)
    result = await db.execute(stmt)
    rows = result.all()

    has_more = len(rows) > limit
    page_rows = rows[:limit]
    items = [
        TrendingRepoItem(
            id=row.id,
            full_name=row.full_name,
            description=row.description,
            language=row.language,
            stars=row.stars,
            period_stars=row.period_stars,
            rank=row.rank,
        )
        for row in page_rows
    ]
    next_cursor = (
        encode_cursor(page_rows[-1].rank, page_rows[-1].id)
        if has_more and page_rows
        else None
    )

    payload = TrendingResponse(items=items, next_cursor=next_cursor)
    return build_cacheable_json_response(request, payload)


@v1_router.get("/repos/{repo_id}", response_model=RepoDetailResponse)
async def get_repo_detail(
    repo_id: int,
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> Response:
    repo = await db.get(Repo, repo_id)
    if repo is None:
        raise AppError(404, "REPO_NOT_FOUND", "未找到对应的仓库信息")

    payload = RepoDetailResponse(
        id=repo.id,
        full_name=repo.full_name,
        description=repo.description,
        language=repo.language,
        stars=repo.stars,
        forks=repo.forks,
        created_at=repo.created_at,
        pushed_at=repo.pushed_at,
        updated_at=repo.updated_at,
    )
    return build_cacheable_json_response(request, payload)


@v1_router.get("/repos/{repo_id}/readme", response_model=RepoReadmeResponse)
async def get_repo_readme(
    repo_id: int,
    request: Request,
    db: AsyncSession = Depends(get_db),
    redis_client: Redis | None = Depends(get_redis_client),
    settings: Settings = Depends(get_settings),
) -> Response:
    repo = await db.get(Repo, repo_id)
    if repo is None:
        raise AppError(404, "REPO_NOT_FOUND", "未找到对应的仓库信息")

    cache_key = f"readme:html:{repo_id}"
    if redis_client is not None:
        try:
            cached_html = await redis_client.get(cache_key)
            if cached_html is not None:
                return build_cacheable_json_response(
                    request,
                    RepoReadmeResponse(repo_id=repo_id, html=cached_html),
                )
        except Exception as exc:
            logger.warning(
                "Redis README cache read failed",
                extra={"event": "readme_cache_read_error", "error": str(exc)},
            )

    owner_repo_parts = repo.full_name.split("/")
    if len(owner_repo_parts) != 2:
        raise AppError(502, "README_FETCH_FAILED", "仓库名称格式异常，无法获取 README")
    owner, repo_name = owner_repo_parts
    url = (
        f"https://api.github.com/repos/{quote(owner, safe='')}/"
        f"{quote(repo_name, safe='')}/readme"
    )
    headers = {
        "User-Agent": "ghtrending-api/0.1",
        "Accept": "application/vnd.github.html+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if settings.github_token:
        headers["Authorization"] = f"Bearer {settings.github_token}"

    try:
        async with httpx.AsyncClient(follow_redirects=True) as client:
            resp = await client.get(url, headers=headers, timeout=15.0)
    except httpx.HTTPError as exc:
        logger.warning(
            "GitHub README network error",
            extra={
                "event": "readme_upstream_error",
                "repo_id": repo_id,
                "error": str(exc),
            },
        )
        raise AppError(
            502, "README_FETCH_FAILED", "获取 README 失败，请稍后重试"
        ) from exc

    if resp.status_code != 200:
        logger.warning(
            "GitHub README non-200 status",
            extra={
                "event": "readme_upstream_status",
                "repo_id": repo_id,
                "status_code": resp.status_code,
            },
        )
        raise AppError(502, "README_FETCH_FAILED", "获取 README 失败，上游服务暂不可用")

    sanitized_html = sanitize_readme_html(resp.text)

    if redis_client is not None:
        try:
            await redis_client.set(
                cache_key, sanitized_html, ex=README_CACHE_TTL_SECONDS
            )
        except Exception as exc:
            logger.warning(
                "Redis README cache write failed",
                extra={"event": "readme_cache_write_error", "error": str(exc)},
            )

    return build_cacheable_json_response(
        request,
        RepoReadmeResponse(repo_id=repo_id, html=sanitized_html),
    )

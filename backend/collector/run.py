from __future__ import annotations

import argparse
import asyncio
import logging
import random
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from urllib.parse import quote

import httpx
from redis.asyncio import Redis
from sqlalchemy import delete, select

# Allow invoking directly as `python collector/run.py` from backend/
BACKEND_ROOT = Path(__file__).resolve().parent.parent
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

from app.core.config import get_settings
from app.core.database import async_session_factory, engine
from app.core.languages import (
    PERIOD_DAYS,
    SUPPORTED_LANGUAGES,
    VALID_PERIODS,
)
from app.core.logging import configure_json_logging
from app.models.repo import Repo
from app.models.snapshot import Snapshot
from app.services.repo_persistence import insert_snapshots, upsert_repos
from collector.parser import ParsedTrendingRepo, parse_trending_html

logger = logging.getLogger("ghtrending.collector")

TRENDING_USER_AGENT = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
)
MAX_BACKOFF_SECONDS = 300
MAX_ATTEMPTS = 3
SNAPSHOT_RETENTION_DAYS = 30


def build_trending_url(language: str | None, period: str) -> str:
    if language:
        encoded_lang = quote(language, safe="")
        return f"https://github.com/trending/{encoded_lang}?since={period}"
    return f"https://github.com/trending?since={period}"


def build_search_query(language: str | None, period: str, now: datetime | None = None) -> str:
    days = PERIOD_DAYS[period]
    ref_time = now or datetime.now(timezone.utc)
    cutoff_date = (ref_time - timedelta(days=days)).strftime("%Y-%m-%d")
    if language:
        return f"created:>{cutoff_date} language:{language}"
    return f"created:>{cutoff_date}"


def _parse_iso_dt(value: str | None) -> datetime | None:
    if not value:
        return None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None


def _github_api_headers(token: str) -> dict[str, str]:
    headers = {
        "User-Agent": "ghtrending-collector/0.1",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers


def _compute_backoff_seconds(attempt: int, retry_after: str | None = None) -> float:
    if retry_after and retry_after.isdigit():
        return float(min(MAX_BACKOFF_SECONDS, max(1, int(retry_after))))
    base = 10.0 * (2 ** (attempt - 1))
    jitter = random.uniform(0.5, 3.0)
    return float(min(MAX_BACKOFF_SECONDS, base + jitter))


async def fetch_trending_html(
    client: httpx.AsyncClient,
    redis_client: Redis | None,
    period: str,
    language: str | None,
) -> tuple[str | None, str | None, bool]:
    """
    Fetch Trending HTML with Redis ETag support and 429 exponential backoff.
    Returns (html_text, new_etag, skipped_or_not_modified).
    """
    url = build_trending_url(language, period)
    etag_key = f"trending:etag:{period}:{language or 'all'}"
    cached_etag: str | None = None

    if redis_client is not None:
        try:
            raw_etag = await redis_client.get(etag_key)
            if isinstance(raw_etag, bytes):
                cached_etag = raw_etag.decode("utf-8")
            elif isinstance(raw_etag, str):
                cached_etag = raw_etag
        except Exception as exc:
            logger.warning(
                "Redis ETag read failed, continuing without If-None-Match",
                extra={"event": "redis_etag_read_error", "error": str(exc)},
            )

    headers: dict[str, str] = {
        "User-Agent": TRENDING_USER_AGENT,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.9",
    }
    if cached_etag:
        headers["If-None-Match"] = cached_etag

    for attempt in range(1, MAX_ATTEMPTS + 1):
        try:
            response = await client.get(url, headers=headers, timeout=20.0)
        except httpx.HTTPError as exc:
            wait_s = _compute_backoff_seconds(attempt)
            logger.warning(
                "Trending HTML network error",
                extra={
                    "event": "trending_fetch_error",
                    "period": period,
                    "language": language,
                    "attempt": attempt,
                    "backoff_seconds": round(wait_s, 2),
                    "error": str(exc),
                },
            )
            if attempt < MAX_ATTEMPTS:
                await asyncio.sleep(wait_s)
                continue
            logger.warning(
                "Skipping target after 3 consecutive network failures",
                extra={
                    "event": "trending_target_skipped",
                    "period": period,
                    "language": language,
                },
            )
            return None, None, True

        if response.status_code == 304:
            logger.info(
                "Trending HTML unchanged (304 Not Modified)",
                extra={
                    "event": "trending_not_modified",
                    "period": period,
                    "language": language,
                },
            )
            return None, cached_etag, True

        if response.status_code == 429 or response.status_code >= 500:
            wait_s = _compute_backoff_seconds(
                attempt, response.headers.get("Retry-After")
            )
            logger.warning(
                "Trending HTML throttled or upstream error",
                extra={
                    "event": "trending_rate_limited",
                    "period": period,
                    "language": language,
                    "status_code": response.status_code,
                    "attempt": attempt,
                    "backoff_seconds": round(wait_s, 2),
                },
            )
            if attempt < MAX_ATTEMPTS:
                await asyncio.sleep(wait_s)
                continue
            logger.warning(
                "Skipping target after 3 consecutive HTTP failures",
                extra={
                    "event": "trending_target_skipped",
                    "period": period,
                    "language": language,
                    "status_code": response.status_code,
                },
            )
            return None, None, True

        if response.status_code != 200:
            logger.warning(
                "Unexpected HTTP status from Trending HTML",
                extra={
                    "event": "trending_unexpected_status",
                    "period": period,
                    "language": language,
                    "status_code": response.status_code,
                },
            )
            return None, None, False

        return response.text, response.headers.get("ETag"), False

    return None, None, True


async def enrich_repos_metadata(
    client: httpx.AsyncClient,
    token: str,
    parsed_rows: list[ParsedTrendingRepo],
) -> dict[str, dict[str, Any]]:
    """
    Resolve GitHub numeric repo ID and missing metadata (forks, created_at, pushed_at, raw)
    by checking existing DB rows first and calling GitHub API only for missing records.
    """
    if not parsed_rows:
        return {}

    full_names = [row.full_name for row in parsed_rows]
    existing_map: dict[str, dict[str, Any]] = {}

    async with async_session_factory() as session:
        stmt = select(Repo).where(Repo.full_name.in_(full_names))
        result = await session.execute(stmt)
        for repo in result.scalars():
            existing_map[repo.full_name.lower()] = {
                "id": repo.id,
                "full_name": repo.full_name,
                "description": repo.description,
                "language": repo.language,
                "stars": repo.stars,
                "forks": repo.forks,
                "created_at": repo.created_at,
                "pushed_at": repo.pushed_at,
                "raw": repo.raw or {},
            }

    missing = [
        row for row in parsed_rows if row.full_name.lower() not in existing_map
    ]
    if not missing:
        return existing_map

    headers = _github_api_headers(token)
    # Batch via /search/repositories?q=repo:a/b+repo:c/d (up to 5 per batch to keep URL short)
    for i in range(0, len(missing), 5):
        batch = missing[i : i + 5]
        q_expr = " ".join(f"repo:{item.full_name}" for item in batch)
        try:
            resp = await client.get(
                "https://api.github.com/search/repositories",
                params={"q": q_expr, "per_page": len(batch)},
                headers=headers,
                timeout=15.0,
            )
            if resp.status_code == 200:
                items = resp.json().get("items", [])
                for gh_item in items:
                    fn = (gh_item.get("full_name") or "").lower()
                    if not fn:
                        continue
                    existing_map[fn] = {
                        "id": int(gh_item["id"]),
                        "full_name": gh_item["full_name"],
                        "description": gh_item.get("description"),
                        "language": gh_item.get("language"),
                        "stars": int(gh_item.get("stargazers_count", 0)),
                        "forks": int(gh_item.get("forks_count", 0)),
                        "created_at": _parse_iso_dt(gh_item.get("created_at")),
                        "pushed_at": _parse_iso_dt(gh_item.get("pushed_at")),
                        "raw": gh_item,
                    }
        except httpx.HTTPError as exc:
            logger.warning(
                "GitHub Search API enrichment batch failed",
                extra={"event": "enrichment_batch_error", "error": str(exc)},
            )

    # Individual fallback for any repo not matched by Search API (e.g. recently renamed)
    for row in missing:
        if row.full_name.lower() in existing_map:
            continue
        try:
            resp = await client.get(
                f"https://api.github.com/repos/{row.full_name}",
                headers=headers,
                timeout=15.0,
            )
            if resp.status_code == 200:
                gh_item = resp.json()
                existing_map[row.full_name.lower()] = {
                    "id": int(gh_item["id"]),
                    "full_name": gh_item["full_name"],
                    "description": gh_item.get("description") or row.description,
                    "language": gh_item.get("language") or row.language,
                    "stars": int(gh_item.get("stargazers_count", row.stars)),
                    "forks": int(gh_item.get("forks_count", row.forks)),
                    "created_at": _parse_iso_dt(gh_item.get("created_at")),
                    "pushed_at": _parse_iso_dt(gh_item.get("pushed_at")),
                    "raw": gh_item,
                }
        except httpx.HTTPError as exc:
            logger.warning(
                "GitHub single repo enrichment failed",
                extra={
                    "event": "enrichment_single_error",
                    "full_name": row.full_name,
                    "error": str(exc),
                },
            )

    return existing_map


async def fallback_search_repositories(
    client: httpx.AsyncClient,
    token: str,
    period: str,
    language: str | None,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    """
    Fallback to GitHub Search API when primary Trending HTML parses 0 items.
    """
    query = build_search_query(language, period)
    headers = _github_api_headers(token)
    now = datetime.now(timezone.utc)

    logger.warning(
        "Primary Trending HTML yielded 0 rows, falling back to Search API",
        extra={
            "event": "search_api_fallback",
            "period": period,
            "language": language,
            "query": query,
        },
    )

    try:
        resp = await client.get(
            "https://api.github.com/search/repositories",
            params={
                "q": query,
                "sort": "stars",
                "order": "desc",
                "per_page": 25,
            },
            headers=headers,
            timeout=20.0,
        )
        if resp.status_code != 200:
            logger.warning(
                "Search API fallback returned non-200 status",
                extra={
                    "event": "search_api_fallback_error",
                    "period": period,
                    "language": language,
                    "status_code": resp.status_code,
                },
            )
            return [], []

        items = resp.json().get("items", [])
    except httpx.HTTPError as exc:
        logger.warning(
            "Search API fallback request failed",
            extra={
                "event": "search_api_fallback_exception",
                "period": period,
                "language": language,
                "error": str(exc),
            },
        )
        return [], []

    repos_payload: list[dict[str, Any]] = []
    snapshots_payload: list[dict[str, Any]] = []
    for idx, gh_item in enumerate(items, start=1):
        repo_id = int(gh_item["id"])
        stars = int(gh_item.get("stargazers_count", 0))
        repos_payload.append(
            {
                "id": repo_id,
                "full_name": gh_item["full_name"],
                "description": gh_item.get("description"),
                "language": gh_item.get("language"),
                "stars": stars,
                "forks": int(gh_item.get("forks_count", 0)),
                "created_at": _parse_iso_dt(gh_item.get("created_at")),
                "pushed_at": _parse_iso_dt(gh_item.get("pushed_at")),
                "raw": gh_item,
                "updated_at": now,
            }
        )
        snapshots_payload.append(
            {
                "repo_id": repo_id,
                "period": period,
                "language": language,
                "rank": idx,
                "period_stars": stars,
                "captured_at": now,
            }
        )

    return repos_payload, snapshots_payload


async def collect_target(
    client: httpx.AsyncClient,
    redis_client: Redis | None,
    github_token: str,
    period: str,
    language: str | None,
) -> int:
    started = time.monotonic()
    html, new_etag, should_stop = await fetch_trending_html(
        client, redis_client, period, language
    )
    if should_stop:
        return 0

    parsed_rows = parse_trending_html(html) if html else []
    repos_payload: list[dict[str, Any]] = []
    snapshots_payload: list[dict[str, Any]] = []
    captured_at = datetime.now(timezone.utc)

    if parsed_rows:
        meta_map = await enrich_repos_metadata(client, github_token, parsed_rows)
        for row in parsed_rows:
            meta = meta_map.get(row.full_name.lower())
            if meta is None:
                logger.warning(
                    "Unable to resolve GitHub numeric id for repo, skipping row",
                    extra={
                        "event": "repo_id_missing",
                        "full_name": row.full_name,
                        "period": period,
                        "language": language,
                    },
                )
                continue

            repo_id = int(meta["id"])
            repos_payload.append(
                {
                    "id": repo_id,
                    "full_name": meta.get("full_name") or row.full_name,
                    "description": row.description or meta.get("description"),
                    "language": row.language or meta.get("language"),
                    "stars": row.stars if row.stars > 0 else int(meta.get("stars", 0)),
                    "forks": row.forks if row.forks > 0 else int(meta.get("forks", 0)),
                    "created_at": meta.get("created_at"),
                    "pushed_at": meta.get("pushed_at"),
                    "raw": meta.get("raw") or {},
                    "updated_at": captured_at,
                }
            )
            snapshots_payload.append(
                {
                    "repo_id": repo_id,
                    "period": period,
                    "language": language,
                    "rank": row.rank,
                    "period_stars": row.period_stars,
                    "captured_at": captured_at,
                }
            )
    else:
        repos_payload, snapshots_payload = await fallback_search_repositories(
            client, github_token, period, language
        )

    if not repos_payload or not snapshots_payload:
        logger.warning(
            "No records collected for target",
            extra={
                "event": "collect_empty",
                "period": period,
                "language": language,
            },
        )
        return 0

    async with async_session_factory() as session:
        async with session.begin():
            await upsert_repos(session, repos_payload)
            inserted_count = await insert_snapshots(session, snapshots_payload)

    if redis_client is not None and new_etag and parsed_rows:
        etag_key = f"trending:etag:{period}:{language or 'all'}"
        try:
            await redis_client.set(etag_key, new_etag, ex=86400)
        except Exception as exc:
            logger.warning(
                "Failed to persist ETag to Redis",
                extra={"event": "redis_etag_write_error", "error": str(exc)},
            )

    duration_ms = round((time.monotonic() - started) * 1000, 1)
    logger.info(
        "Collected trending snapshot",
        extra={
            "event": "collect_success",
            "period": period,
            "language": language,
            "count": inserted_count,
            "duration_ms": duration_ms,
        },
    )
    return inserted_count


async def cleanup_old_snapshots(days: int = SNAPSHOT_RETENTION_DAYS) -> int:
    cutoff = datetime.now(timezone.utc) - timedelta(days=days)
    async with async_session_factory() as session:
        async with session.begin():
            stmt = delete(Snapshot).where(Snapshot.captured_at < cutoff)
            result = await session.execute(stmt)
            deleted = result.rowcount or 0

    logger.info(
        "Cleaned up expired snapshots",
        extra={
            "event": "snapshots_cleanup",
            "retention_days": days,
            "deleted_count": deleted,
        },
    )
    return deleted


async def run_collection(
    periods: list[str],
    languages: list[str | None],
) -> None:
    settings = get_settings()
    redis_client: Redis | None = None
    try:
        redis_client = Redis.from_url(settings.redis_url, decode_responses=True)
        await redis_client.ping()
    except Exception as exc:
        logger.warning(
            "Redis unavailable, proceeding without ETag cache",
            extra={"event": "redis_unavailable", "error": str(exc)},
        )
        if redis_client is not None:
            await redis_client.aclose()
            redis_client = None

    total_targets = len(periods) * len(languages)
    total_rows = 0
    idx = 0

    async with httpx.AsyncClient(follow_redirects=True) as client:
        for period in periods:
            for language in languages:
                idx += 1
                total_rows += await collect_target(
                    client=client,
                    redis_client=redis_client,
                    github_token=settings.github_token,
                    period=period,
                    language=language,
                )
                if idx < total_targets:
                    await asyncio.sleep(random.uniform(1.0, 2.0))

    await cleanup_old_snapshots(SNAPSHOT_RETENTION_DAYS)

    if redis_client is not None:
        await redis_client.aclose()

    await engine.dispose()
    logger.info(
        "Collector run finished",
        extra={
            "event": "collector_finished",
            "targets": total_targets,
            "total_snapshots": total_rows,
        },
    )


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="One-shot GitHub Trending collector"
    )
    parser.add_argument(
        "--period",
        choices=VALID_PERIODS,
        default=None,
        help="Target period (daily, weekly, monthly). Omit to run all.",
    )
    parser.add_argument(
        "--language",
        default=None,
        help=(
            "Target language code (e.g. kotlin). "
            "Use 'all' or '' for all-languages board; omit to run all-languages + 30 languages."
        ),
    )
    return parser.parse_args(argv)


def resolve_targets(
    period_arg: str | None,
    language_arg: str | None,
) -> tuple[list[str], list[str | None]]:
    periods = [period_arg] if period_arg else list(VALID_PERIODS)
    if language_arg is None:
        languages: list[str | None] = [None] + [
            item["code"] for item in SUPPORTED_LANGUAGES
        ]
    elif language_arg == "" or language_arg.lower() == "all":
        languages = [None]
    else:
        languages = [language_arg.lower()]
    return periods, languages


def main() -> None:
    configure_json_logging()
    args = parse_args()
    periods, languages = resolve_targets(args.period, args.language)
    logger.info(
        "Starting collector run",
        extra={
            "event": "collector_started",
            "periods": periods,
            "language_count": len(languages),
        },
    )
    asyncio.run(run_collection(periods, languages))


if __name__ == "__main__":
    main()

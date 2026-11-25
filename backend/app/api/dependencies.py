import hashlib
import logging
import re

from fastapi import Depends, Header, Request, Response
from fastapi.encoders import jsonable_encoder
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from redis.asyncio import Redis

from app.core.config import Settings, get_settings
from app.core.exceptions import AppError

logger = logging.getLogger("ghtrending.api")

RATE_LIMIT_MAX_REQUESTS = 60
RATE_LIMIT_WINDOW_SECONDS = 60

_SEMVER_PART_RE = re.compile(r"^\d+")


def parse_semver(version: str) -> tuple[int, int, int] | None:
    cleaned = version.strip().lstrip("vV")
    if not cleaned:
        return None
    parts = cleaned.split(".")
    if len(parts) > 4:
        return None
    nums: list[int] = []
    for part in parts[:3]:
        match = _SEMVER_PART_RE.match(part)
        if not match:
            return None
        nums.append(int(match.group(0)))
    while len(nums) < 3:
        nums.append(0)
    return nums[0], nums[1], nums[2]


def is_version_supported(client_version: str | None, min_version: str) -> bool:
    if not client_version:
        return False
    parsed_client = parse_semver(client_version)
    parsed_min = parse_semver(min_version)
    if parsed_client is None or parsed_min is None:
        return False
    return parsed_client >= parsed_min


def extract_client_ip(request: Request) -> str:
    forwarded_for = request.headers.get("X-Forwarded-For")
    if forwarded_for:
        first_ip = forwarded_for.split(",")[0].strip()
        if first_ip:
            return first_ip
    if request.client and request.client.host:
        return request.client.host
    return "unknown"


def get_redis_client(request: Request) -> Redis | None:
    return getattr(request.app.state, "redis", None)


async def enforce_rate_limit(
    request: Request,
    redis_client: Redis | None = Depends(get_redis_client),
) -> None:
    if redis_client is None:
        return
    ip = extract_client_ip(request)
    key = f"rl:ip:{ip}"
    try:
        async with redis_client.pipeline(transaction=True) as pipe:
            pipe.incr(key)
            pipe.ttl(key)
            count, ttl = await pipe.execute()
        if ttl is None or ttl < 0:
            await redis_client.expire(key, RATE_LIMIT_WINDOW_SECONDS)
            ttl = RATE_LIMIT_WINDOW_SECONDS
        if int(count) > RATE_LIMIT_MAX_REQUESTS:
            retry_after = max(1, int(ttl))
            raise AppError(
                status_code=429,
                code="RATE_LIMITED",
                message="请求过于频繁，请稍后再试",
                headers={"Retry-After": str(retry_after)},
            )
    except AppError:
        raise
    except Exception as exc:
        logger.warning(
            "Redis rate-limit check bypassed due to Redis error",
            extra={"event": "rate_limit_redis_error", "error": str(exc)},
        )


async def enforce_client_version(
    x_client_version: str | None = Header(default=None, alias="X-Client-Version"),
    settings: Settings = Depends(get_settings),
) -> str:
    if not is_version_supported(x_client_version, settings.min_client_version):
        raise AppError(
            status_code=426,
            code="CLIENT_UPGRADE_REQUIRED",
            message="当前客户端版本过低，请更新至最新版本",
            extra={
                "min_version": settings.min_client_version,
                "download_url": settings.upgrade_url,
            },
        )
    assert x_client_version is not None
    return x_client_version


def _etag_matches(if_none_match: str | None, current_etag: str) -> bool:
    if not if_none_match:
        return False
    normalized_current = current_etag.removeprefix("W/").strip()
    for candidate in if_none_match.split(","):
        token = candidate.strip()
        if token == "*":
            return True
        if token.removeprefix("W/").strip() == normalized_current:
            return True
    return False


def build_cacheable_json_response(
    request: Request,
    payload: BaseModel | dict,
    max_age: int = 300,
) -> Response:
    encoded = jsonable_encoder(payload)
    import json

    raw_bytes = json.dumps(
        encoded, ensure_ascii=False, separators=(",", ":"), sort_keys=True
    ).encode("utf-8")
    digest = hashlib.sha256(raw_bytes).hexdigest()[:32]
    etag = f'"{digest}"'
    cache_headers = {
        "ETag": etag,
        "Cache-Control": f"public, max-age={max_age}",
    }

    if _etag_matches(request.headers.get("If-None-Match"), etag):
        return Response(status_code=304, headers=cache_headers)

    return JSONResponse(content=encoded, headers=cache_headers)

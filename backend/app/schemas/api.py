from datetime import datetime
from typing import Any

from pydantic import BaseModel


class ErrorDetail(BaseModel):
    code: str
    message: str
    min_version: str | None = None
    download_url: str | None = None


class ErrorResponse(BaseModel):
    error: ErrorDetail


class HealthResponse(BaseModel):
    status: str


class LanguageOut(BaseModel):
    code: str
    label: str


class LanguagesResponse(BaseModel):
    items: list[LanguageOut]


class TrendingRepoItem(BaseModel):
    id: int
    full_name: str
    description: str | None = None
    language: str | None = None
    stars: int
    period_stars: int
    rank: int


class TrendingResponse(BaseModel):
    items: list[TrendingRepoItem]
    next_cursor: str | None = None


class RepoDetailResponse(BaseModel):
    id: int
    full_name: str
    description: str | None = None
    language: str | None = None
    stars: int
    forks: int
    created_at: datetime | None = None
    pushed_at: datetime | None = None
    updated_at: datetime


class RepoReadmeResponse(BaseModel):
    repo_id: int
    html: str


def error_payload(
    code: str,
    message: str,
    **extra: Any,
) -> dict[str, Any]:
    body: dict[str, Any] = {"code": code, "message": message}
    for key, val in extra.items():
        if val is not None:
            body[key] = val
    return {"error": body}

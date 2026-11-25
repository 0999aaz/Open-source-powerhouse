from collections.abc import Sequence
from datetime import datetime, timezone
from typing import Any

from sqlalchemy import func
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.repo import Repo
from app.models.snapshot import Snapshot


async def upsert_repos(
    session: AsyncSession,
    records: Sequence[dict[str, Any]],
) -> int:
    """
    Insert new repositories or update ONLY stars, forks, and updated_at on id conflict.
    Guarantees repeated collection never creates duplicate rows.
    """
    if not records:
        return 0

    deduped_by_id: dict[int, dict[str, Any]] = {}
    now = datetime.now(timezone.utc)
    for item in records:
        repo_id = int(item["id"])
        deduped_by_id[repo_id] = {
            "id": repo_id,
            "full_name": item["full_name"],
            "description": item.get("description"),
            "language": item.get("language"),
            "stars": int(item.get("stars", 0)),
            "forks": int(item.get("forks", 0)),
            "created_at": item.get("created_at"),
            "pushed_at": item.get("pushed_at"),
            "raw": item.get("raw") or {},
            "updated_at": item.get("updated_at") or now,
        }

    values = list(deduped_by_id.values())
    stmt = pg_insert(Repo).values(values)
    stmt = stmt.on_conflict_do_update(
        index_elements=[Repo.id],
        set_={
            "stars": stmt.excluded.stars,
            "forks": stmt.excluded.forks,
            "updated_at": func.now(),
        },
    )
    await session.execute(stmt)
    return len(values)


async def insert_snapshots(
    session: AsyncSession,
    records: Sequence[dict[str, Any]],
) -> int:
    """Insert a batch of ranking snapshots (language=None represents all-languages board)."""
    if not records:
        return 0

    rows = [
        Snapshot(
            repo_id=int(item["repo_id"]),
            period=item["period"],
            language=item.get("language") or None,
            rank=int(item["rank"]),
            period_stars=int(item.get("period_stars", 0)),
            captured_at=item["captured_at"],
        )
        for item in records
    ]
    session.add_all(rows)
    return len(rows)

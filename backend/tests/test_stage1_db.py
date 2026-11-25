import asyncio
from datetime import datetime, timezone

import pytest
from sqlalchemy import func, select, text

from app.core.database import async_session_factory
from app.models import Repo, Snapshot
from app.services.repo_persistence import insert_snapshots, upsert_repos


@pytest.mark.asyncio
async def test_upsert_repo_only_updates_stars_forks_updated_at() -> None:
    test_repo_id = 999_999_001
    async with async_session_factory() as session:
        await session.execute(
            text("DELETE FROM repos WHERE id = :id"), {"id": test_repo_id}
        )
        await session.commit()

        await upsert_repos(
            session,
            [
                {
                    "id": test_repo_id,
                    "full_name": "example/stage1-verify",
                    "description": "initial description",
                    "language": "Kotlin",
                    "stars": 100,
                    "forks": 10,
                    "created_at": datetime(2025, 1, 1, tzinfo=timezone.utc),
                    "pushed_at": datetime(2026, 1, 1, tzinfo=timezone.utc),
                    "raw": {"source": "test"},
                }
            ],
        )
        await session.commit()

        # Re-insert with same ID, changed stars/forks and changed description;
        # ON CONFLICT (id) DO UPDATE must keep row count = 1 and preserve description.
        await upsert_repos(
            session,
            [
                {
                    "id": test_repo_id,
                    "full_name": "example/stage1-verify",
                    "description": "should not overwrite",
                    "language": "Rust",
                    "stars": 250,
                    "forks": 35,
                    "raw": {"source": "test2"},
                }
            ],
        )
        await insert_snapshots(
            session,
            [
                {
                    "repo_id": test_repo_id,
                    "period": "daily",
                    "language": None,
                    "rank": 1,
                    "period_stars": 50,
                    "captured_at": datetime.now(timezone.utc),
                }
            ],
        )
        await session.commit()

        count_stmt = select(func.count()).select_from(Repo).where(Repo.id == test_repo_id)
        row_count = await session.scalar(count_stmt)
        assert row_count == 1

        repo = await session.get(Repo, test_repo_id)
        assert repo is not None
        assert repo.stars == 250
        assert repo.forks == 35
        assert repo.description == "initial description"
        assert repo.language == "Kotlin"

        snap = await session.scalar(
            select(Snapshot).where(Snapshot.repo_id == test_repo_id)
        )
        assert snap is not None
        assert snap.language is None

        await session.execute(
            text("DELETE FROM repos WHERE id = :id"), {"id": test_repo_id}
        )
        await session.commit()


if __name__ == "__main__":
    asyncio.run(test_upsert_repo_only_updates_stars_forks_updated_at())
    print("Stage 1 idempotent upsert & NULL all-language snapshot check passed.")

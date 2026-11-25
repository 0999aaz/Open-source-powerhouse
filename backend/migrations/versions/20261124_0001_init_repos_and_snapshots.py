"""Initialize repos and snapshots tables

Revision ID: 20261124_0001
Revises:
Create Date: 2026-11-24 20:30:00

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision: str = "20261124_0001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "repos",
        sa.Column("id", sa.BigInteger(), autoincrement=False, nullable=False),
        sa.Column("full_name", sa.String(length=255), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("language", sa.String(length=64), nullable=True),
        sa.Column(
            "stars",
            sa.Integer(),
            server_default=sa.text("0"),
            nullable=False,
        ),
        sa.Column(
            "forks",
            sa.Integer(),
            server_default=sa.text("0"),
            nullable=False,
        ),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("pushed_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column(
            "raw",
            postgresql.JSONB(astext_type=sa.Text()),
            server_default=sa.text("'{}'::jsonb"),
            nullable=False,
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_repos_full_name", "repos", ["full_name"], unique=True)

    op.create_table(
        "snapshots",
        sa.Column("id", sa.BigInteger(), autoincrement=True, nullable=False),
        sa.Column("repo_id", sa.BigInteger(), nullable=False),
        sa.Column("period", sa.String(length=16), nullable=False),
        sa.Column("language", sa.String(length=64), nullable=True),
        sa.Column("rank", sa.Integer(), nullable=False),
        sa.Column(
            "period_stars",
            sa.Integer(),
            server_default=sa.text("0"),
            nullable=False,
        ),
        sa.Column(
            "captured_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.CheckConstraint(
            "period IN ('daily', 'weekly', 'monthly')",
            name="ck_snapshots_period",
        ),
        sa.ForeignKeyConstraint(["repo_id"], ["repos.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_snapshots_period_lang_captured_rank",
        "snapshots",
        ["period", "language", sa.text("captured_at DESC"), "rank"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index("ix_snapshots_period_lang_captured_rank", table_name="snapshots")
    op.drop_table("snapshots")
    op.drop_index("ix_repos_full_name", table_name="repos")
    op.drop_table("repos")

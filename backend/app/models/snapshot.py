from datetime import datetime

from sqlalchemy import (
    BigInteger,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    func,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base


class Snapshot(Base):
    __tablename__ = "snapshots"
    __table_args__ = (
        CheckConstraint(
            "period IN ('daily', 'weekly', 'monthly')",
            name="ck_snapshots_period",
        ),
        Index(
            "ix_snapshots_period_lang_captured_rank",
            "period",
            "language",
            text("captured_at DESC"),
            "rank",
        ),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    repo_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("repos.id", ondelete="CASCADE"),
        nullable=False,
    )
    period: Mapped[str] = mapped_column(String(16), nullable=False)
    # NULL represents the all-languages overall trending board
    language: Mapped[str | None] = mapped_column(String(64), nullable=True)
    rank: Mapped[int] = mapped_column(Integer, nullable=False)
    period_stars: Mapped[int] = mapped_column(
        Integer, nullable=False, default=0, server_default=text("0")
    )
    captured_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )

    repo = relationship("Repo", back_populates="snapshots")

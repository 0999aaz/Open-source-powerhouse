from functools import lru_cache
from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    database_url: str = (
        "postgresql+asyncpg://ghtrending:ghtrending@localhost:5432/ghtrending"
    )
    redis_url: str = "redis://localhost:6379/0"
    github_token: str = ""
    min_client_version: str = "1.0.0"
    upgrade_url: str = "https://github.com/example/ghtrending/releases/latest"

    @field_validator("database_url", mode="before")
    @classmethod
    def normalize_async_postgres_url(cls, value: str) -> str:
        if isinstance(value, str):
            if value.startswith("postgres://"):
                return value.replace("postgres://", "postgresql+asyncpg://", 1)
            if value.startswith("postgresql://"):
                return value.replace("postgresql://", "postgresql+asyncpg://", 1)
        return value


@lru_cache
def get_settings() -> Settings:
    return Settings()

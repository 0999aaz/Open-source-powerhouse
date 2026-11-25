from contextlib import asynccontextmanager
from collections.abc import AsyncIterator

from fastapi import FastAPI
from redis.asyncio import Redis

from app.api.routes import root_router, v1_router
from app.core.config import get_settings
from app.core.database import engine
from app.core.exceptions import register_exception_handlers
from app.core.logging import configure_json_logging


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings = get_settings()
    redis_client = Redis.from_url(settings.redis_url, decode_responses=True)
    app.state.redis = redis_client
    try:
        yield
    finally:
        await redis_client.aclose()
        await engine.dispose()


def create_app() -> FastAPI:
    configure_json_logging()
    app = FastAPI(
        title="GitHub Trending API",
        version="0.1.0",
        docs_url=None,
        redoc_url=None,
        lifespan=lifespan,
    )
    register_exception_handlers(app)
    app.include_router(root_router)
    app.include_router(v1_router)
    return app


app = create_app()

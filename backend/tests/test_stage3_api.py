import pytest
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient

from app.api.dependencies import is_version_supported, parse_semver
from app.api.routes import root_router, v1_router
from app.core.exceptions import register_exception_handlers
from app.services.cursor import decode_cursor, encode_cursor
from app.services.html_sanitizer import sanitize_readme_html


def test_cursor_roundtrip_and_validation() -> None:
    token = encode_cursor(rank=20, repo_id=123456)
    assert decode_cursor(token) == (20, 123456)


def test_semver_version_comparison() -> None:
    assert parse_semver("1.0.0") == (1, 0, 0)
    assert parse_semver("v1.2.3-beta") == (1, 2, 3)
    assert is_version_supported("1.0.0", "1.0.0") is True
    assert is_version_supported("1.2.0", "1.0.0") is True
    assert is_version_supported("0.9.9", "1.0.0") is False
    assert is_version_supported(None, "1.0.0") is False


def test_sanitize_readme_html_strips_xss() -> None:
    dirty = (
        '<h1>Title</h1>'
        '<script>alert(1)</script>'
        '<img src="https://example.com/a.png" onerror="alert(2)">'
        '<a href="javascript:alert(3)" onclick="steal()">Bad</a>'
        '<a href="https://github.com/a/b">Good</a>'
    )
    cleaned = sanitize_readme_html(dirty)
    assert "<script>" not in cleaned
    assert "onerror" not in cleaned
    assert "onclick" not in cleaned
    assert "javascript:" not in cleaned
    assert "https://github.com/a/b" in cleaned
    assert "https://example.com/a.png" in cleaned


@pytest.mark.asyncio
async def test_languages_etag_and_426_guard() -> None:
    app = FastAPI()
    register_exception_handlers(app)
    app.include_router(root_router)
    app.include_router(v1_router)
    app.state.redis = None

    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://testserver"
    ) as client:
        # Missing X-Client-Version -> 426 with Chinese error + download_url
        res_426 = await client.get("/v1/languages")
        assert res_426.status_code == 426
        body_426 = res_426.json()
        assert body_426["error"]["code"] == "CLIENT_UPGRADE_REQUIRED"
        assert "download_url" in body_426["error"]

        # Valid X-Client-Version -> 200 + ETag + Cache-Control
        res_200 = await client.get(
            "/v1/languages", headers={"X-Client-Version": "1.0.0"}
        )
        assert res_200.status_code == 200
        assert res_200.headers.get("Cache-Control") == "public, max-age=300"
        etag = res_200.headers.get("ETag")
        assert etag
        assert len(res_200.json()["items"]) == 30

        # If-None-Match -> 304
        res_304 = await client.get(
            "/v1/languages",
            headers={"X-Client-Version": "1.0.0", "If-None-Match": etag},
        )
        assert res_304.status_code == 304

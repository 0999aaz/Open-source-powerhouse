import json
import logging
from datetime import datetime, timezone

from app.core.languages import SUPPORTED_LANGUAGES
from app.core.logging import JsonFormatter
from collector.parser import parse_trending_html
from collector.run import build_search_query, build_trending_url, resolve_targets

SAMPLE_HTML = """
<html>
  <body>
    <article class="Box-row">
      <h2 class="h3 lh-condensed">
        <a href="/JetBrains/kotlin">JetBrains / kotlin</a>
      </h2>
      <p class="col-9 color-fg-muted my-1 pr-4">
        The Kotlin Programming Language.
      </p>
      <div class="f6 color-fg-muted mt-2">
        <span itemprop="programmingLanguage">Kotlin</span>
        <a href="/JetBrains/kotlin/stargazers">49,820</a>
        <a href="/JetBrains/kotlin/forks">5,810</a>
        <span class="d-inline-block float-sm-right">128 stars today</span>
      </div>
    </article>
    <article class="Box-row">
      <h2 class="h3 lh-condensed">
        <a href="/square/okhttp">square / okhttp</a>
      </h2>
      <p class="col-9 color-fg-muted my-1 pr-4"></p>
      <div class="f6 color-fg-muted mt-2">
        <a href="/square/okhttp/stargazers">45,900</a>
        <span class="d-inline-block float-sm-right">42 stars today</span>
      </div>
    </article>
  </body>
</html>
"""


def test_supported_languages_count() -> None:
    assert len(SUPPORTED_LANGUAGES) == 30


def test_parse_trending_html() -> None:
    rows = parse_trending_html(SAMPLE_HTML)
    assert len(rows) == 2

    first = rows[0]
    assert first.rank == 1
    assert first.full_name == "JetBrains/kotlin"
    assert first.description == "The Kotlin Programming Language."
    assert first.language == "Kotlin"
    assert first.stars == 49820
    assert first.forks == 5810
    assert first.period_stars == 128

    second = rows[1]
    assert second.rank == 2
    assert second.full_name == "square/okhttp"
    assert second.description is None
    assert second.language is None
    assert second.stars == 45900
    assert second.forks == 0
    assert second.period_stars == 42


def test_build_search_query_and_urls() -> None:
    ref = datetime(2026, 11, 24, tzinfo=timezone.utc)
    assert build_search_query("kotlin", "daily", ref) == "created:>2026-11-23 language:kotlin"
    assert build_search_query(None, "weekly", ref) == "created:>2026-11-17"
    assert build_search_query("rust", "monthly", ref) == "created:>2026-10-25 language:rust"

    assert build_trending_url(None, "daily") == "https://github.com/trending?since=daily"
    assert build_trending_url("c++", "weekly") == "https://github.com/trending/c%2B%2B?since=weekly"

    periods, langs = resolve_targets("daily", None)
    assert periods == ["daily"]
    assert langs[0] is None
    assert len(langs) == 31


def test_json_log_formatter_outputs_valid_json() -> None:
    formatter = JsonFormatter()
    record = logging.LogRecord(
        name="ghtrending.collector",
        level=logging.INFO,
        pathname=__file__,
        lineno=1,
        msg="Collected trending snapshot",
        args=(),
        exc_info=None,
    )
    record.period = "daily"
    record.language = "kotlin"
    parsed = json.loads(formatter.format(record))
    assert parsed["level"] == "INFO"
    assert parsed["period"] == "daily"
    assert parsed["language"] == "kotlin"


if __name__ == "__main__":
    test_supported_languages_count()
    test_parse_trending_html()
    test_build_search_query_and_urls()
    test_json_log_formatter_outputs_valid_json()
    print("Stage 2 collector unit checks passed.")

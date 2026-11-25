import re
from dataclasses import dataclass

from selectolax.parser import HTMLParser

_INT_RE = re.compile(r"[\d,]+")


@dataclass(slots=True)
class ParsedTrendingRepo:
    rank: int
    full_name: str
    description: str | None
    language: str | None
    stars: int
    forks: int
    period_stars: int


def _parse_int(text: str | None) -> int:
    if not text:
        return 0
    match = _INT_RE.search(text)
    if not match:
        return 0
    return int(match.group(0).replace(",", ""))


def parse_trending_html(html: str) -> list[ParsedTrendingRepo]:
    """
    Parse https://github.com/trending/{language}?since={period} HTML.
    Extracts article.Box-row items in display order.
    """
    tree = HTMLParser(html)
    rows = tree.css("article.Box-row")
    results: list[ParsedTrendingRepo] = []

    for idx, row in enumerate(rows, start=1):
        title_link = row.css_first("h2 a")
        if title_link is None:
            continue
        href = (title_link.attributes.get("href") or "").strip()
        full_name = href.strip("/")
        if "/" not in full_name or len(full_name.split("/")) != 2:
            continue

        desc_node = row.css_first("p.col-9")
        description = desc_node.text(strip=True) if desc_node else None
        if description == "":
            description = None

        lang_node = row.css_first("[itemprop=programmingLanguage]")
        language = lang_node.text(strip=True) if lang_node else None
        if language == "":
            language = None

        stars_node = row.css_first('a[href$="/stargazers"]')
        stars = _parse_int(stars_node.text(strip=True) if stars_node else None)

        forks_node = row.css_first('a[href$="/forks"]')
        forks = _parse_int(forks_node.text(strip=True) if forks_node else None)

        period_stars_node = row.css_first("span.d-inline-block.float-sm-right")
        period_stars = _parse_int(
            period_stars_node.text(strip=True) if period_stars_node else None
        )

        results.append(
            ParsedTrendingRepo(
                rank=idx,
                full_name=full_name,
                description=description,
                language=language,
                stars=stars,
                forks=forks,
                period_stars=period_stars,
            )
        )

    return results

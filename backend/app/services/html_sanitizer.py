import nh3

ALLOWED_TAGS: set[str] = {
    "a",
    "abbr",
    "b",
    "blockquote",
    "br",
    "code",
    "dd",
    "del",
    "details",
    "div",
    "dl",
    "dt",
    "em",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "hr",
    "i",
    "img",
    "ins",
    "kbd",
    "li",
    "ol",
    "p",
    "picture",
    "pre",
    "q",
    "rp",
    "rt",
    "ruby",
    "s",
    "samp",
    "section",
    "source",
    "span",
    "strong",
    "sub",
    "summary",
    "sup",
    "table",
    "tbody",
    "td",
    "tfoot",
    "th",
    "thead",
    "tr",
    "tt",
    "u",
    "ul",
    "var",
}

ALLOWED_ATTRIBUTES: dict[str, set[str]] = {
    "*": {"id", "class", "dir", "title"},
    "a": {"href", "name"},
    "img": {"src", "alt", "width", "height", "align"},
    "source": {"srcset", "media", "type"},
    "td": {"align", "colspan", "rowspan"},
    "th": {"align", "colspan", "rowspan"},
    "details": {"open"},
}

ALLOWED_URL_SCHEMES: set[str] = {"http", "https", "mailto"}


def sanitize_readme_html(raw_html: str) -> str:
    """
    Sanitize rendered GitHub README HTML on the server before caching/returning:
    - Strips <script>, <style>, <iframe>, <form>, and dangerous tags
    - Strips all inline on* event attributes (only explicitly allowlisted attrs survive)
    - Enforces URL scheme allowlist (http, https, mailto) on href/src/srcset
    """
    if not raw_html:
        return ""
    return nh3.clean(
        raw_html,
        tags=ALLOWED_TAGS,
        attributes=ALLOWED_ATTRIBUTES,
        url_schemes=ALLOWED_URL_SCHEMES,
        link_rel="noopener noreferrer",
        strip_comments=True,
    )

from typing import TypedDict


class LanguageItem(TypedDict):
    code: str
    label: str


# 30 mainstream programming languages supported by GitHub Trending & /v1/languages
SUPPORTED_LANGUAGES: tuple[LanguageItem, ...] = (
    {"code": "python", "label": "Python"},
    {"code": "typescript", "label": "TypeScript"},
    {"code": "javascript", "label": "JavaScript"},
    {"code": "kotlin", "label": "Kotlin"},
    {"code": "java", "label": "Java"},
    {"code": "go", "label": "Go"},
    {"code": "rust", "label": "Rust"},
    {"code": "c++", "label": "C++"},
    {"code": "c", "label": "C"},
    {"code": "c#", "label": "C#"},
    {"code": "swift", "label": "Swift"},
    {"code": "dart", "label": "Dart"},
    {"code": "php", "label": "PHP"},
    {"code": "ruby", "label": "Ruby"},
    {"code": "scala", "label": "Scala"},
    {"code": "shell", "label": "Shell"},
    {"code": "html", "label": "HTML"},
    {"code": "css", "label": "CSS"},
    {"code": "vue", "label": "Vue"},
    {"code": "svelte", "label": "Svelte"},
    {"code": "jupyter-notebook", "label": "Jupyter Notebook"},
    {"code": "zig", "label": "Zig"},
    {"code": "elixir", "label": "Elixir"},
    {"code": "haskell", "label": "Haskell"},
    {"code": "lua", "label": "Lua"},
    {"code": "r", "label": "R"},
    {"code": "objective-c", "label": "Objective-C"},
    {"code": "clojure", "label": "Clojure"},
    {"code": "dockerfile", "label": "Dockerfile"},
    {"code": "hcl", "label": "HCL"},
)

VALID_PERIODS: tuple[str, ...] = ("daily", "weekly", "monthly")
PERIOD_DAYS: dict[str, int] = {"daily": 1, "weekly": 7, "monthly": 30}
SUPPORTED_LANGUAGE_CODES: frozenset[str] = frozenset(
    item["code"] for item in SUPPORTED_LANGUAGES
)

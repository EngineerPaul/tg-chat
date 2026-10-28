"""Достать http(s) и www. из текста сообщения."""

from __future__ import annotations

import re

URL_RE = re.compile(r"\b(?:https?://|www\.)[^\s<>\"']+", re.IGNORECASE)


def _trim_url(raw: str) -> str:
    s = raw
    while s and s[-1] in ".,;:!?":
        s = s[:-1]
    while s.endswith(")") and s.count("(") < s.count(")"):
        s = s[:-1]
    return s


def _href_of(cut: str) -> str | None:
    t = cut.strip()
    if len(t) < 4:
        return None
    href = t if re.match(r"^https?://", t, re.I) else f"https://{t}"
    if not re.match(r"^https?://", href, re.I):
        return None
    return href


def extract_urls(text: str) -> list[tuple[str, str]]:
    """Пары (как в тексте, href)."""
    found: list[tuple[str, str]] = []
    for match in URL_RE.finditer(text or ""):
        cut = _trim_url(match.group(0))
        href = _href_of(cut)
        if href:
            found.append((cut, href))
    return found

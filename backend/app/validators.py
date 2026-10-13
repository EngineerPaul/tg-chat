"""Проверка логина и пароля по правилам ТЗ."""

import re

LOGIN_RE = re.compile(r"^[A-Za-zА-Яа-яЁё0-9]{3,30}$")
PASSWORD_RE = re.compile(r"^[A-Za-zА-Яа-яЁё0-9 ]{3,30}$")


def validate_login(login: str) -> str:
    """Проверить логин и вернуть его без пробелов по краям."""
    value = (login or "").strip()
    if not LOGIN_RE.fullmatch(value):
        raise ValueError("Логин: 3–30 букв или цифр, без пробелов и знаков")
    return value


def validate_password(password: str) -> str:
    """Проверить пароль по правилам ТЗ."""
    value = password or ""
    if not PASSWORD_RE.fullmatch(value):
        raise ValueError("Пароль: 3–30 букв, цифр и пробелов, без знаков")
    return value

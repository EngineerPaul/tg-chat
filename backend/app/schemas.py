"""Pydantic-схемы API."""

from datetime import datetime
from typing import Any

from pydantic import BaseModel, Field


class RegisterIn(BaseModel):
    """Тело регистрации."""

    login: str
    password: str


class LoginIn(BaseModel):
    """Тело входа."""

    login: str
    password: str


class RefreshIn(BaseModel):
    """Обновление access по refresh."""

    refresh_token: str


class PasswordChangeIn(BaseModel):
    """Новый пароль и текущий refresh, чтобы обновить эту сессию."""

    password: str
    refresh_token: str


class TokenOut(BaseModel):
    """Пара JWT после входа."""

    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    user: dict[str, Any]


class UserOut(BaseModel):
    """Публичные поля пользователя."""

    id: int
    login: str
    avatar_url: str | None = None
    last_seen: datetime | None = None
    online: bool = False
    nick: str | None = None


class AliasIn(BaseModel):
    """Ник собеседника."""

    nick: str = Field(max_length=60)


class DialogCreateIn(BaseModel):
    """Открыть диалог по логину."""

    login: str


class TextMessageIn(BaseModel):
    """Текстовое сообщение."""

    text: str = Field(min_length=1, max_length=8000)
    reply_to_id: int | None = None


class EditMessageIn(BaseModel):
    """Правка текста или подписи."""

    text: str = Field(min_length=1, max_length=8000)


class ReadIn(BaseModel):
    """Пометить прочитанным до указанного id."""

    up_to_id: int | None = None


class FcmIn(BaseModel):
    """FCM-токен устройства."""

    token: str
    refresh_token: str | None = None

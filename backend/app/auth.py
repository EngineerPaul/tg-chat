"""JWT access/refresh и хеширование паролей."""

from datetime import datetime, timedelta, timezone
from uuid import uuid4

from jose import JWTError, jwt
from passlib.context import CryptContext

from app.config import settings

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def hash_password(password: str) -> str:
    """Посчитать bcrypt-хеш пароля."""
    return pwd_context.hash(password)


def verify_password(password: str, password_hash: str) -> bool:
    """Сверить пароль с хешем."""
    return pwd_context.verify(password, password_hash)


def create_access_token(user_id: int) -> str:
    """Выпустить короткий access-токен."""
    exp = datetime.now(timezone.utc) + timedelta(seconds=settings.access_ttl_seconds)
    return jwt.encode(
        {"sub": str(user_id), "typ": "access", "exp": exp},
        settings.jwt_secret,
        algorithm=settings.jwt_algorithm,
    )


def create_refresh_token(user_id: int) -> tuple[str, str]:
    """Выпустить refresh без практического истечения. Возвращает токен и jti."""
    jti = uuid4().hex
    exp = datetime.now(timezone.utc) + timedelta(seconds=settings.refresh_ttl_seconds)
    token = jwt.encode(
        {"sub": str(user_id), "typ": "refresh", "jti": jti, "exp": exp},
        settings.jwt_secret,
        algorithm=settings.jwt_algorithm,
    )
    return token, jti


def decode_token(token: str, expected_type: str) -> dict:
    """Разобрать JWT и проверить тип. Бросает JWTError при ошибке."""
    payload = jwt.decode(token, settings.jwt_secret, algorithms=[settings.jwt_algorithm])
    if payload.get("typ") != expected_type:
        raise JWTError("Неверный тип токена")
    return payload

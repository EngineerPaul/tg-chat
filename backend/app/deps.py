"""Зависимости FastAPI: текущий пользователь по access JWT."""

from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jose import JWTError
from sqlalchemy.orm import Session

from app.auth import decode_token
from app.database import get_db
from app.models import User

bearer = HTTPBearer(auto_error=False)


def current_user(
    creds: HTTPAuthorizationCredentials | None = Depends(bearer),
    db: Session = Depends(get_db),
) -> User:
    """Извлечь пользователя из Bearer access-токена."""
    if creds is None:
        raise HTTPException(401, "Нужна авторизация")
    try:
        payload = decode_token(creds.credentials, "access")
        user_id = int(payload["sub"])
    except (JWTError, KeyError, ValueError) as exc:
        raise HTTPException(401, "Недействительный токен") from exc
    user = db.get(User, user_id)
    if user is None:
        raise HTTPException(401, "Пользователь не найден")
    return user

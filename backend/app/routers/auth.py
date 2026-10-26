"""Регистрация, вход, refresh и выход."""

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.auth import (
    create_access_token,
    create_refresh_token,
    decode_token,
    hash_password,
    verify_password,
)
from app.database import get_db
from app.deps import current_user
from app.models import Device, User
from app.schemas import LoginIn, PasswordChangeIn, RefreshIn, RegisterIn
from app.serialize import user_out
from app.validators import validate_login, validate_password

router = APIRouter(prefix="/auth", tags=["auth"])


def _issue(db: Session, user: User) -> dict:
    """Выпустить пару токенов и сохранить refresh в БД."""
    access = create_access_token(user.id)
    refresh, jti = create_refresh_token(user.id)
    db.add(Device(user_id=user.id, refresh_jti=jti))
    db.commit()
    return {
        "access_token": access,
        "refresh_token": refresh,
        "token_type": "bearer",
        "user": user_out(db, user),
    }


@router.post("/register")
def register(body: RegisterIn, db: Session = Depends(get_db)) -> dict:
    """Создать аккаунт с сайта и сразу войти."""
    try:
        login = validate_login(body.login)
        password = validate_password(body.password)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    if db.query(User).filter(User.login == login).first():
        raise HTTPException(409, "Логин уже занят")
    user = User(login=login, password_hash=hash_password(password))
    db.add(user)
    db.commit()
    db.refresh(user)
    return _issue(db, user)


@router.post("/login")
def login(body: LoginIn, db: Session = Depends(get_db)) -> dict:
    """Войти по логину и паролю."""
    try:
        login_value = validate_login(body.login)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    user = db.query(User).filter(User.login == login_value).first()
    if user is None or not verify_password(body.password, user.password_hash):
        raise HTTPException(401, "Неверный логин или пароль")
    return _issue(db, user)


@router.post("/refresh")
def refresh(body: RefreshIn, db: Session = Depends(get_db)) -> dict:
    """Выдать новый access, не трогая сессию устройства."""
    from jose import JWTError

    try:
        payload = decode_token(body.refresh_token, "refresh")
        jti = payload["jti"]
        user_id = int(payload["sub"])
    except (JWTError, KeyError, ValueError) as exc:
        raise HTTPException(401, "Недействительный refresh") from exc
    device = db.query(Device).filter(Device.refresh_jti == jti, Device.revoked.is_(False)).first()
    if device is None or device.user_id != user_id:
        raise HTTPException(401, "Сессия отозвана")
    user = db.get(User, user_id)
    if user is None:
        raise HTTPException(401, "Пользователь не найден")
    return {"access_token": create_access_token(user.id), "token_type": "bearer"}


@router.post("/password")
def change_password(
    body: PasswordChangeIn,
    db: Session = Depends(get_db),
    user: User = Depends(current_user),
) -> dict:
    """Сменить пароль без старого. Обновить хеш и токены этой сессии."""
    from jose import JWTError

    try:
        password = validate_password(body.password)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    try:
        payload = decode_token(body.refresh_token, "refresh")
        old_jti = payload["jti"]
        if int(payload["sub"]) != user.id:
            raise HTTPException(401, "Недействительный refresh")
    except (JWTError, KeyError, ValueError) as exc:
        raise HTTPException(401, "Недействительный refresh") from exc
    user.password_hash = hash_password(password)
    access = create_access_token(user.id)
    refresh, jti = create_refresh_token(user.id)
    device = db.query(Device).filter(
        Device.refresh_jti == old_jti,
        Device.user_id == user.id,
        Device.revoked.is_(False),
    ).first()
    if device:
        device.refresh_jti = jti
    else:
        db.add(Device(user_id=user.id, refresh_jti=jti))
    db.commit()
    return {
        "access_token": access,
        "refresh_token": refresh,
        "token_type": "bearer",
        "user": user_out(db, user),
    }


@router.post("/logout")
def logout(body: RefreshIn, db: Session = Depends(get_db), user: User = Depends(current_user)) -> dict:
    """Отозвать refresh и снять FCM этой сессии."""
    from jose import JWTError

    try:
        payload = decode_token(body.refresh_token, "refresh")
        jti = payload["jti"]
    except JWTError:
        return {"ok": True}
    device = db.query(Device).filter(Device.refresh_jti == jti, Device.user_id == user.id).first()
    if device:
        device.revoked = True
        device.fcm_token = None
        db.commit()
    return {"ok": True}

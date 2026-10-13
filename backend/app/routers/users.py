"""Профиль, поиск, аватар, FCM."""

from fastapi import APIRouter, Depends, File, HTTPException, Query, UploadFile
from sqlalchemy.orm import Session

from app.auth import decode_token
from app.database import get_db
from app.deps import current_user
from app.media import compress_photo_if_needed, read_upload, save_bytes
from app.models import Device, User
from app.schemas import AliasIn, FcmIn
from app.serialize import user_out
from app.validators import validate_login

router = APIRouter(tags=["users"])


@router.get("/users/me")
def me(db: Session = Depends(get_db), user: User = Depends(current_user)) -> dict:
    """Текущий пользователь."""
    return user_out(db, user)


@router.get("/users/search")
def search(
    login: str = Query(min_length=1),
    db: Session = Depends(get_db),
    user: User = Depends(current_user),
) -> dict:
    """Найти пользователя по точному логину."""
    try:
        value = validate_login(login) if len(login) >= 3 else login.strip()
    except ValueError:
        value = login.strip()
    found = db.query(User).filter(User.login == value).first()
    if found is None:
        raise HTTPException(404, "Пользователь не найден")
    if found.id == user.id:
        raise HTTPException(400, "Нельзя написать самому себе")
    return user_out(db, found, viewer_id=user.id)


@router.post("/users/me/avatar")
async def avatar(
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
    user: User = Depends(current_user),
) -> dict:
    """Загрузить аватар, сжать как фото."""
    data = await read_upload(file, 10 * 1024 * 1024)
    jpeg, _mime = compress_photo_if_needed(data, file.content_type or "image/jpeg")
    user.avatar_path = save_bytes(jpeg, ".jpg")
    db.commit()
    return user_out(db, user)


@router.post("/users/me/fcm")
def save_fcm(body: FcmIn, db: Session = Depends(get_db), user: User = Depends(current_user)) -> dict:
    """Привязать FCM-токен к текущей сессии устройства."""
    from jose import JWTError

    device = None
    if body.refresh_token:
        try:
            payload = decode_token(body.refresh_token, "refresh")
            device = (
                db.query(Device)
                .filter(Device.refresh_jti == payload["jti"], Device.user_id == user.id)
                .first()
            )
        except (JWTError, KeyError):
            device = None
    if device is None:
        device = (
            db.query(Device)
            .filter(Device.user_id == user.id, Device.revoked.is_(False))
            .order_by(Device.id.desc())
            .first()
        )
    if device is None:
        raise HTTPException(400, "Нет активной сессии")
    device.fcm_token = body.token
    db.commit()
    return {"ok": True}


@router.patch("/dialogs/{dialog_id}/alias")
def set_alias(
    dialog_id: int,
    body: AliasIn,
    db: Session = Depends(get_db),
    user: User = Depends(current_user),
) -> dict:
    """Поставить ник собеседнику."""
    from app.models import ContactAlias, Dialog
    from app.serialize import peer_of
    from app.services import user_in_dialog

    dialog = db.get(Dialog, dialog_id)
    if dialog is None or not user_in_dialog(dialog, user.id):
        raise HTTPException(404, "Диалог не найден")
    peer_id = peer_of(dialog, user.id)
    row = (
        db.query(ContactAlias)
        .filter(ContactAlias.owner_id == user.id, ContactAlias.peer_id == peer_id)
        .first()
    )
    nick = body.nick.strip()
    if row:
        row.nick = nick
    else:
        db.add(ContactAlias(owner_id=user.id, peer_id=peer_id, nick=nick))
    db.commit()
    peer = db.get(User, peer_id)
    return user_out(db, peer, viewer_id=user.id)

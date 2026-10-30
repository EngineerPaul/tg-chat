"""Список диалогов и история сообщений."""

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from app.database import get_db
from app.deps import current_user
from app.links import extract_urls
from app.models import Attachment, Dialog, Message, User
from app.schemas import DialogCreateIn, ReadIn
from app.serialize import attachment_out, last_message_preview, message_out, peer_of, unread_count, user_out
from app.services import get_or_create_dialog, mark_read, user_in_dialog
from app.validators import validate_login
from app.ws_manager import hub

router = APIRouter(tags=["dialogs"])


@router.get("/dialogs")
def list_dialogs(db: Session = Depends(get_db), user: User = Depends(current_user)) -> list[dict]:
    """Список чатов с превью и непрочитанными."""
    dialogs = (
        db.query(Dialog)
        .filter((Dialog.user_low == user.id) | (Dialog.user_high == user.id))
        .all()
    )
    result = []
    for dialog in dialogs:
        last = (
            db.query(Message)
            .filter(Message.dialog_id == dialog.id, Message.deleted_at.is_(None))
            .order_by(Message.id.desc())
            .first()
        )
        peer = db.get(User, peer_of(dialog, user.id))
        result.append(
            {
                "id": dialog.id,
                "peer": user_out(db, peer, viewer_id=user.id),
                "last_message": last_message_preview(last),
                "last_at": last.created_at.isoformat() + "Z" if last else None,
                "unread": unread_count(db, dialog.id, user.id),
                "typing": False,
            }
        )
    result.sort(key=lambda row: row["last_at"] or "", reverse=True)
    return result


@router.post("/dialogs")
def open_dialog(body: DialogCreateIn, db: Session = Depends(get_db), user: User = Depends(current_user)) -> dict:
    """Найти или создать диалог по логину."""
    try:
        login = validate_login(body.login)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    peer = db.query(User).filter(User.login == login).first()
    if peer is None:
        raise HTTPException(404, "Пользователь не найден")
    if peer.id == user.id:
        raise HTTPException(400, "Нельзя написать самому себе")
    dialog = get_or_create_dialog(db, user.id, peer.id)
    return {"id": dialog.id, "peer": user_out(db, peer, viewer_id=user.id)}


@router.get("/dialogs/{dialog_id}/messages")
def history(
    dialog_id: int,
    before: int | None = None,
    limit: int = Query(default=250, ge=1, le=250),
    db: Session = Depends(get_db),
    user: User = Depends(current_user),
) -> dict:
    """Хвост истории: без before — последние, с before — старше."""
    dialog = db.get(Dialog, dialog_id)
    if dialog is None or not user_in_dialog(dialog, user.id):
        raise HTTPException(404, "Диалог не найден")
    q = db.query(Message).filter(Message.dialog_id == dialog_id, Message.deleted_at.is_(None))
    if before:
        q = q.filter(Message.id < before)
    rows = q.order_by(Message.id.desc()).limit(limit).all()
    rows.reverse()
    return {"messages": [message_out(db, m, user.id) for m in rows], "has_more": len(rows) == limit}


def _need_dialog(dialog_id: int, db: Session, user: User) -> Dialog:
    dialog = db.get(Dialog, dialog_id)
    if dialog is None or not user_in_dialog(dialog, user.id):
        raise HTTPException(404, "Диалог не найден")
    return dialog


@router.get("/dialogs/{dialog_id}/shared/media")
def shared_media(
    dialog_id: int,
    before: int | None = None,
    limit: int = Query(default=50, ge=1, le=100),
    db: Session = Depends(get_db),
    user: User = Depends(current_user),
) -> dict:
    """Фото и видео диалога с сервера, новые сначала."""
    _need_dialog(dialog_id, db, user)
    return _shared_attachments(db, dialog_id, ("photo", "video"), before, limit)


@router.get("/dialogs/{dialog_id}/shared/files")
def shared_files(
    dialog_id: int,
    before: int | None = None,
    limit: int = Query(default=50, ge=1, le=100),
    db: Session = Depends(get_db),
    user: User = Depends(current_user),
) -> dict:
    """Файлы диалога с сервера, новые сначала."""
    _need_dialog(dialog_id, db, user)
    return _shared_attachments(db, dialog_id, ("file",), before, limit)


@router.get("/dialogs/{dialog_id}/shared/links")
def shared_links(
    dialog_id: int,
    before: int | None = None,
    limit: int = Query(default=50, ge=1, le=100),
    db: Session = Depends(get_db),
    user: User = Depends(current_user),
) -> dict:
    """Ссылки из текстов диалога, новые сначала."""
    _need_dialog(dialog_id, db, user)
    q = db.query(Message).filter(
        Message.dialog_id == dialog_id,
        Message.deleted_at.is_(None),
        Message.text.isnot(None),
        (Message.text.like("%http%") | Message.text.like("%www.")),
    )
    if before:
        q = q.filter(Message.id < before)
    rows = q.order_by(Message.id.desc()).limit(limit).all()
    items: list[dict] = []
    for msg in rows:
        for text, href in extract_urls(msg.text or ""):
            items.append(
                {
                    "message_id": msg.id,
                    "text": text,
                    "href": href,
                    "created_at": msg.created_at.isoformat() + "Z",
                }
            )
    return {"items": items, "has_more": len(rows) == limit}


def _shared_attachments(
    db: Session,
    dialog_id: int,
    kinds: tuple[str, ...],
    before: int | None,
    limit: int,
) -> dict:
    """Вложения диалога нужных kind."""
    q = (
        db.query(Attachment, Message)
        .join(Message, Message.id == Attachment.message_id)
        .filter(
            Message.dialog_id == dialog_id,
            Message.deleted_at.is_(None),
            Attachment.kind.in_(kinds),
        )
    )
    if before:
        q = q.filter(Attachment.id < before)
    rows = q.order_by(Attachment.id.desc()).limit(limit).all()
    items = []
    for att, msg in rows:
        item = attachment_out(att)
        item["message_id"] = msg.id
        item["created_at"] = msg.created_at.isoformat() + "Z"
        items.append(item)
    return {"items": items, "has_more": len(items) == limit}


@router.get("/dialogs/{dialog_id}/storage")
def dialog_storage(
    dialog_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(current_user),
) -> dict:
    """Id и размеры вложений диалога (orig_size) для подсчёта локального кэша."""
    _need_dialog(dialog_id, db, user)
    rows = (
        db.query(Attachment.id, Attachment.orig_size, Attachment.kind)
        .join(Message, Message.id == Attachment.message_id)
        .filter(Message.dialog_id == dialog_id, Message.deleted_at.is_(None))
        .all()
    )
    items = [
        {"id": att_id, "size": size if size and size > 0 else 102400, "kind": kind}
        for att_id, size, kind in rows
    ]
    return {"items": items}


@router.post("/dialogs/{dialog_id}/read")
async def read_dialog(
    dialog_id: int,
    body: ReadIn,
    db: Session = Depends(get_db),
    user: User = Depends(current_user),
) -> dict:
    """Пометить входящие прочитанными и сообщить автору синие галочки."""
    dialog = db.get(Dialog, dialog_id)
    if dialog is None or not user_in_dialog(dialog, user.id):
        raise HTTPException(404, "Диалог не найден")
    ids = mark_read(db, dialog_id, user.id, body.up_to_id)
    peer = peer_of(dialog, user.id)
    if ids:
        await hub.send_user(peer, {"event": "read", "dialog_id": dialog_id, "ids": ids})
    await hub.send_user(user.id, {"event": "caught_up", "dialog_id": dialog_id})
    return {"ok": True, "ids": ids}

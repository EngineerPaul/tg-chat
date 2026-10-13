"""Сборка JSON-ответов из моделей."""

from datetime import datetime, timezone

from sqlalchemy.orm import Session

from app.config import settings
from app.models import Attachment, ContactAlias, Dialog, Message, MessageReceipt, User
from app.ws_manager import hub


def media_url(stored_name: str | None) -> str | None:
    """Публичный URL вложения."""
    if not stored_name:
        return None
    return f"{settings.public_base}/api/media/{stored_name}"


def is_online(user: User) -> bool:
    """Пользователь в сети, если есть сокет или last_seen свежий."""
    if hub.is_online(user.id):
        return True
    delta = datetime.now(timezone.utc).replace(tzinfo=None) - user.last_seen
    return delta.total_seconds() < 45


def nick_for(db: Session, owner_id: int, peer_id: int) -> str | None:
    """Ник собеседника или None."""
    row = (
        db.query(ContactAlias)
        .filter(ContactAlias.owner_id == owner_id, ContactAlias.peer_id == peer_id)
        .first()
    )
    return row.nick if row else None


def user_out(db: Session, user: User, viewer_id: int | None = None) -> dict:
    """Словарь пользователя для API."""
    nick = nick_for(db, viewer_id, user.id) if viewer_id else None
    return {
        "id": user.id,
        "login": user.login,
        "avatar_url": media_url(user.avatar_path),
        "last_seen": user.last_seen.isoformat() + "Z",
        "online": is_online(user),
        "nick": nick,
        "display_name": nick or user.login,
    }


def attachment_out(att: Attachment) -> dict:
    """Словарь вложения."""
    return {
        "id": att.id,
        "kind": att.kind,
        "orig_name": att.orig_name,
        "mime": att.mime,
        "size": att.orig_size,
        "gzip": att.gzip,
        "url": media_url(att.stored_name),
        "preview_url": media_url(att.preview_name),
        "duration_ms": att.duration_ms,
        "album_order": att.album_order,
    }


def ticks(message: Message, viewer_id: int) -> str:
    """sent / delivered / read с точки зрения автора."""
    if message.author_id != viewer_id:
        return "none"
    receipts = [r for r in message.receipts if r.user_id != message.author_id]
    if any(r.read_at for r in receipts):
        return "read"
    if any(r.delivered_at for r in receipts):
        return "delivered"
    return "sent"


def reply_preview(db: Session, reply_to_id: int | None) -> dict | None:
    """Цитата для ответа, даже если оригинал вытеснен с клиента."""
    if not reply_to_id:
        return None
    src = db.get(Message, reply_to_id)
    if not src:
        return None
    text = None if src.deleted_at else src.text
    kind = src.type
    return {"id": src.id, "text": text, "type": kind, "author_id": src.author_id}


def message_out(db: Session, message: Message, viewer_id: int) -> dict:
    """Словарь сообщения. Удалённые не отдаём."""
    atts = sorted(message.attachments, key=lambda a: a.album_order)
    return {
        "id": message.id,
        "dialog_id": message.dialog_id,
        "author_id": message.author_id,
        "type": message.type,
        "text": message.text,
        "reply_to": reply_preview(db, message.reply_to_id),
        "edited_at": message.edited_at.isoformat() + "Z" if message.edited_at else None,
        "created_at": message.created_at.isoformat() + "Z",
        "ticks": ticks(message, viewer_id),
        "attachments": [attachment_out(a) for a in atts],
    }


def peer_of(dialog: Dialog, user_id: int) -> int:
    """Id собеседника в диалоге."""
    return dialog.user_high if dialog.user_low == user_id else dialog.user_low


def last_message_preview(msg: Message | None) -> str:
    """Превью для списка чатов."""
    if not msg or msg.deleted_at:
        return ""
    if msg.type == "photo":
        return "Фото"
    if msg.type == "video":
        return "Видео"
    if msg.type == "album":
        kinds = {a.kind for a in msg.attachments}
        if kinds <= {"photo"}:
            return "Фото"
        if kinds <= {"video"}:
            return "Видео"
        return "Альбом"
    if msg.type == "file":
        att = msg.attachments[0] if msg.attachments else None
        return att.orig_name if att else "Файл"
    return (msg.text or "")[:120]


def unread_count(db: Session, dialog_id: int, user_id: int) -> int:
    """Число непрочитанных входящих."""
    q = (
        db.query(Message)
        .join(MessageReceipt, MessageReceipt.message_id == Message.id)
        .filter(
            Message.dialog_id == dialog_id,
            Message.author_id != user_id,
            Message.deleted_at.is_(None),
            MessageReceipt.user_id == user_id,
            MessageReceipt.read_at.is_(None),
        )
    )
    return q.count()

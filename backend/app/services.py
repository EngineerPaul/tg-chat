"""Общая бизнес-логика диалогов и статусов."""

from sqlalchemy.orm import Session

from app.fcm import send_update_push
from app.models import Device, Dialog, Message, MessageReceipt, User, utcnow
from app.serialize import peer_of
from app.ws_manager import hub


def pair_ids(a: int, b: int) -> tuple[int, int]:
    """Упорядоченная пара id для уникального диалога."""
    return (a, b) if a < b else (b, a)


def get_or_create_dialog(db: Session, user_a: int, user_b: int) -> Dialog:
    """Найти или создать диалог 1-на-1."""
    low, high = pair_ids(user_a, user_b)
    dialog = db.query(Dialog).filter(Dialog.user_low == low, Dialog.user_high == high).first()
    if dialog:
        return dialog
    dialog = Dialog(user_low=low, user_high=high)
    db.add(dialog)
    db.commit()
    db.refresh(dialog)
    return dialog


def user_in_dialog(dialog: Dialog, user_id: int) -> bool:
    """Проверить, что пользователь — участник диалога."""
    return user_id in (dialog.user_low, dialog.user_high)


def add_receipts(db: Session, message: Message, dialog: Dialog) -> None:
    """Создать квитанции для обоих участников."""
    for uid in (dialog.user_low, dialog.user_high):
        rec = MessageReceipt(message_id=message.id, user_id=uid)
        if uid == message.author_id:
            rec.delivered_at = utcnow()
            rec.read_at = utcnow()
        db.add(rec)


def mark_delivered(db: Session, message: Message, user_id: int) -> None:
    """Пометить доставку, если ещё не стояла."""
    rec = (
        db.query(MessageReceipt)
        .filter(MessageReceipt.message_id == message.id, MessageReceipt.user_id == user_id)
        .first()
    )
    if rec and rec.delivered_at is None:
        rec.delivered_at = utcnow()


def mark_read(db: Session, dialog_id: int, user_id: int, up_to_id: int | None) -> list[int]:
    """Прочитать входящие. Вернуть id сообщений, у которых впервые появился read."""
    q = (
        db.query(MessageReceipt)
        .join(Message, Message.id == MessageReceipt.message_id)
        .filter(
            Message.dialog_id == dialog_id,
            Message.author_id != user_id,
            MessageReceipt.user_id == user_id,
            MessageReceipt.read_at.is_(None),
            Message.deleted_at.is_(None),
        )
    )
    if up_to_id:
        q = q.filter(Message.id <= up_to_id)
    changed: list[int] = []
    now = utcnow()
    for rec in q.all():
        rec.read_at = now
        if rec.delivered_at is None:
            rec.delivered_at = now
        changed.append(rec.message_id)
    db.commit()
    return changed


async def notify_new_message(db: Session, dialog: Dialog, payload: dict, author_id: int) -> None:
    """Разослать WS и при отсутствии сокета у собеседника — FCM."""
    peer = peer_of(dialog, author_id)
    sent = await hub.send_dialog([author_id, peer], payload)
    if sent.get(peer, 0) == 0:
        tokens = [
            d.fcm_token
            for d in db.query(Device).filter(
                Device.user_id == peer, Device.revoked.is_(False), Device.fcm_token.isnot(None)
            )
        ]
        send_update_push(tokens)


def touch_last_seen(db: Session, user: User) -> None:
    """Обновить last_seen."""
    user.last_seen = utcnow()
    db.commit()

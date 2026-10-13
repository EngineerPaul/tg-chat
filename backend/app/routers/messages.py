"""Отправка, правка, удаление сообщений и загрузка вложений."""

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from sqlalchemy.orm import Session

from app.database import get_db
from app.deps import current_user
from app.media import (
    PHOTO_MIME,
    VIDEO_MIME,
    compress_photo_if_needed,
    compress_video,
    make_video_preview,
    probe_duration_ms,
    read_upload,
    save_bytes,
    uploads_dir,
)
from app.models import Attachment, Dialog, Message, User, utcnow
from app.schemas import EditMessageIn, TextMessageIn
from app.serialize import message_out, peer_of
from app.services import add_receipts, mark_delivered, notify_new_message, user_in_dialog
from app.ws_manager import hub

router = APIRouter(tags=["messages"])


async def _publish(db: Session, dialog: Dialog, message: Message, user: User) -> dict:
    """Сохранить квитанции и разослать новое сообщение."""
    add_receipts(db, message, dialog)
    db.commit()
    db.refresh(message)
    payload = {"event": "message", "message": message_out(db, message, user.id)}
    await notify_new_message(db, dialog, payload, user.id)
    peer = peer_of(dialog, user.id)
    if hub.is_online(peer):
        mark_delivered(db, message, peer)
        db.commit()
        await hub.send_user(user.id, {"event": "delivered", "id": message.id})
    return message_out(db, message, user.id)


def _dialog(db: Session, dialog_id: int, user_id: int) -> Dialog:
    """Диалог с проверкой доступа."""
    dialog = db.get(Dialog, dialog_id)
    if dialog is None or not user_in_dialog(dialog, user_id):
        raise HTTPException(404, "Диалог не найден")
    return dialog


@router.post("/dialogs/{dialog_id}/messages")
async def send_text(
    dialog_id: int,
    body: TextMessageIn,
    db: Session = Depends(get_db),
    user: User = Depends(current_user),
) -> dict:
    """Отправить текст, опционально как ответ."""
    dialog = _dialog(db, dialog_id, user.id)
    msg = Message(
        dialog_id=dialog.id,
        author_id=user.id,
        type="text",
        text=body.text.strip(),
        reply_to_id=body.reply_to_id,
    )
    db.add(msg)
    db.commit()
    db.refresh(msg)
    return await _publish(db, dialog, msg, user)


@router.patch("/messages/{message_id}")
async def edit_message(
    message_id: int,
    body: EditMessageIn,
    db: Session = Depends(get_db),
    user: User = Depends(current_user),
) -> dict:
    """Править свой текст или подпись."""
    msg = db.get(Message, message_id)
    if msg is None or msg.deleted_at or msg.author_id != user.id:
        raise HTTPException(404, "Сообщение не найдено")
    msg.text = body.text.strip()
    msg.edited_at = utcnow()
    db.commit()
    dialog = db.get(Dialog, msg.dialog_id)
    payload = {"event": "edited", "message": message_out(db, msg, user.id)}
    await hub.send_dialog([dialog.user_low, dialog.user_high], payload)
    return message_out(db, msg, user.id)


@router.delete("/messages/{message_id}")
async def delete_message(
    message_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(current_user),
) -> dict:
    """Удалить сообщение у обоих."""
    msg = db.get(Message, message_id)
    if msg is None or msg.deleted_at:
        raise HTTPException(404, "Сообщение не найдено")
    dialog = db.get(Dialog, msg.dialog_id)
    if dialog is None or not user_in_dialog(dialog, user.id):
        raise HTTPException(404, "Сообщение не найдено")
    msg.deleted_at = utcnow()
    db.commit()
    await hub.send_dialog(
        [dialog.user_low, dialog.user_high],
        {"event": "deleted", "id": msg.id, "dialog_id": msg.dialog_id},
    )
    return {"ok": True}


@router.post("/dialogs/{dialog_id}/media")
async def send_media(
    dialog_id: int,
    files: list[UploadFile] = File(...),
    caption: str = Form(""),
    reply_to_id: int | None = Form(None),
    already_compressed: str = Form("0"),
    db: Session = Depends(get_db),
    user: User = Depends(current_user),
) -> dict:
    """Альбом фото/видео, 1–10 штук. Веб может отдать несжатое — тогда сожмёт сервер."""
    if not 1 <= len(files) <= 10:
        raise HTTPException(400, "От 1 до 10 вложений")
    dialog = _dialog(db, dialog_id, user.id)
    prepared: list[dict] = []
    for file in files:
        mime = file.content_type or "application/octet-stream"
        if mime in PHOTO_MIME or mime.startswith("image/"):
            data = await read_upload(file, 12 * 1024 * 1024)
            if already_compressed != "1":
                data, mime = compress_photo_if_needed(data, mime)
            prepared.append(
                {
                    "kind": "photo",
                    "mime": "image/jpeg",
                    "orig": file.filename or "photo.jpg",
                    "stored": save_bytes(data, ".jpg"),
                    "preview": None,
                    "duration": None,
                }
            )
        elif mime in VIDEO_MIME or mime.startswith("video/"):
            data = await read_upload(file, 110 * 1024 * 1024)
            tmp = uploads_dir() / save_bytes(data, ".bin")
            if already_compressed != "1":
                dest, duration = compress_video(tmp)
                tmp.unlink(missing_ok=True)
            else:
                dest, duration = tmp, probe_duration_ms(tmp)
            prepared.append(
                {
                    "kind": "video",
                    "mime": "video/mp4",
                    "orig": file.filename or "video.mp4",
                    "stored": dest.name,
                    "preview": make_video_preview(dest) or None,
                    "duration": duration,
                }
            )
        else:
            raise HTTPException(400, "Можно только фото и видео")

    msg_type = "album"
    if len(prepared) == 1:
        msg_type = prepared[0]["kind"]
    msg = Message(
        dialog_id=dialog.id,
        author_id=user.id,
        type=msg_type,
        text=caption.strip() or None,
        reply_to_id=reply_to_id,
    )
    db.add(msg)
    db.commit()
    db.refresh(msg)
    for index, item in enumerate(prepared):
        size = (uploads_dir() / item["stored"]).stat().st_size
        db.add(
            Attachment(
                message_id=msg.id,
                kind=item["kind"],
                orig_name=item["orig"],
                mime=item["mime"],
                size=size,
                orig_size=size,
                stored_name=item["stored"],
                preview_name=item["preview"],
                duration_ms=item["duration"],
                album_order=index,
            )
        )
    db.commit()
    db.refresh(msg)
    return await _publish(db, dialog, msg, user)


@router.post("/dialogs/{dialog_id}/file")
async def send_file(
    dialog_id: int,
    file: UploadFile = File(...),
    caption: str = Form(""),
    gzip_flag: str = Form("0"),
    orig_size: int = Form(0),
    reply_to_id: int | None = Form(None),
    db: Session = Depends(get_db),
    user: User = Depends(current_user),
) -> dict:
    """Произвольный файл до 50 МБ. gzip только если клиент уже сжал."""
    dialog = _dialog(db, dialog_id, user.id)
    data = await read_upload(file, 50 * 1024 * 1024)
    is_gzip = gzip_flag == "1"
    stored = save_bytes(data, ".bin")
    msg = Message(
        dialog_id=dialog.id,
        author_id=user.id,
        type="file",
        text=caption.strip() or None,
        reply_to_id=reply_to_id,
    )
    db.add(msg)
    db.commit()
    db.refresh(msg)
    db.add(
        Attachment(
            message_id=msg.id,
            kind="file",
            orig_name=file.filename or "file",
            mime=file.content_type or "application/octet-stream",
            size=len(data),
            orig_size=orig_size or len(data),
            stored_name=stored,
            gzip=is_gzip,
            album_order=0,
        )
    )
    db.commit()
    db.refresh(msg)
    return await _publish(db, dialog, msg, user)

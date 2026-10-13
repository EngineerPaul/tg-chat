"""Точка входа FastAPI: REST, медиа и WebSocket."""

import json
from pathlib import Path

from fastapi import Depends, FastAPI, HTTPException, Query, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from jose import JWTError
from sqlalchemy.orm import Session

from app.auth import decode_token
from app.config import settings
from app.database import Base, SessionLocal, engine, get_db
from app.media import uploads_dir
from app.models import Attachment, User, utcnow
from app.routers import auth, dialogs, messages, users
from app.services import touch_last_seen
from app.ws_manager import hub

app = FastAPI(title="Чат", docs_url=None, redoc_url=None)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origin_list(),
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

API = "/extra/chat/api"
app.include_router(auth.router, prefix=API)
app.include_router(users.router, prefix=API)
app.include_router(dialogs.router, prefix=API)
app.include_router(messages.router, prefix=API)


@app.on_event("startup")
def startup() -> None:
    """Создать таблицы SQLite при старте контейнера."""
    Path(settings.data_dir).mkdir(parents=True, exist_ok=True)
    Base.metadata.create_all(bind=engine)


@app.get(f"{API}/health")
def health() -> dict:
    """Проверка живости для healthcheck Docker."""
    return {"ok": True}


@app.get(f"{API}/media/{{stored_name}}")
def get_media(
    stored_name: str,
    download: int = 0,
    db: Session = Depends(get_db),
) -> FileResponse:
    """Отдать вложение по случайному имени. Листинга каталога нет."""
    if "/" in stored_name or "\\" in stored_name or ".." in stored_name:
        raise HTTPException(404)
    att = db.query(Attachment).filter(Attachment.stored_name == stored_name).first()
    path = uploads_dir() / stored_name
    if not path.exists():
        raise HTTPException(404)
    mime = att.mime if att else "application/octet-stream"
    filename = att.orig_name if att and download else stored_name
    headers = {}
    if download and att:
        headers["Content-Disposition"] = f'attachment; filename="{filename}"'
    return FileResponse(path, media_type=mime, filename=filename if download else None, headers=headers)


@app.websocket(f"{API}/ws")
async def websocket_endpoint(ws: WebSocket, token: str = Query(default="")) -> None:
    """Реалтайм: сообщения, typing, presence. Access JWT в query."""
    try:
        payload = decode_token(token, "access")
        user_id = int(payload["sub"])
    except (JWTError, KeyError, ValueError):
        await ws.close(code=4401)
        return
    db = SessionLocal()
    user = db.get(User, user_id)
    if user is None:
        db.close()
        await ws.close(code=4401)
        return
    await ws.accept()
    await hub.add(user_id, ws)
    touch_last_seen(db, user)
    peer_ids = _peer_ids(db, user_id)
    await hub.send_dialog(peer_ids, {"event": "presence", "user_id": user_id, "online": True})
    try:
        while True:
            raw = await ws.receive_text()
            try:
                data = json.loads(raw)
            except json.JSONDecodeError:
                continue
            await _handle_ws(db, user, data)
    except WebSocketDisconnect:
        pass
    finally:
        hub.remove(user_id, ws)
        user.last_seen = utcnow()
        db.commit()
        if not hub.is_online(user_id):
            await hub.send_dialog(
                _peer_ids(db, user_id),
                {"event": "presence", "user_id": user_id, "online": False, "last_seen": user.last_seen.isoformat() + "Z"},
            )
        db.close()


def _peer_ids(db: Session, user_id: int) -> list[int]:
    """Собеседники всех диалогов пользователя."""
    from app.models import Dialog

    ids: list[int] = []
    for dialog in db.query(Dialog).filter((Dialog.user_low == user_id) | (Dialog.user_high == user_id)):
        ids.append(dialog.user_high if dialog.user_low == user_id else dialog.user_low)
    return ids


async def _handle_ws(db: Session, user: User, data: dict) -> None:
    """Обработать кадр сокета: ping, typing, delivered."""
    event = data.get("event")
    if event == "ping":
        user.last_seen = utcnow()
        db.commit()
        return
    if event == "typing":
        dialog_id = data.get("dialog_id")
        from app.models import Dialog
        from app.serialize import peer_of
        from app.services import user_in_dialog

        dialog = db.get(Dialog, dialog_id)
        if dialog and user_in_dialog(dialog, user.id):
            await hub.send_user(
                peer_of(dialog, user.id),
                {"event": "typing", "dialog_id": dialog.id, "user_id": user.id},
            )
        return
    if event == "delivered":
        from app.models import Message
        from app.services import mark_delivered

        msg = db.get(Message, data.get("id"))
        if msg and msg.author_id != user.id:
            mark_delivered(db, msg, user.id)
            db.commit()
            await hub.send_user(msg.author_id, {"event": "delivered", "id": msg.id})

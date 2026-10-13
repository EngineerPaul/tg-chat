"""Логическая модель данных чата."""

from datetime import datetime, timezone

from sqlalchemy import (
    Boolean,
    DateTime,
    ForeignKey,
    Integer,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


def utcnow() -> datetime:
    """Текущее время UTC без tzinfo для SQLite."""
    return datetime.now(timezone.utc).replace(tzinfo=None)


class User(Base):
    """Учётная запись: логин, хеш пароля, аватар, last_seen."""

    __tablename__ = "users"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    login: Mapped[str] = mapped_column(String(30), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(255))
    avatar_path: Mapped[str | None] = mapped_column(String(255), nullable=True)
    last_seen: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)

    devices: Mapped[list["Device"]] = relationship(back_populates="user")


class Device(Base):
    """Сессия устройства: refresh-токен и FCM."""

    __tablename__ = "devices"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    refresh_jti: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    fcm_token: Mapped[str | None] = mapped_column(String(512), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    revoked: Mapped[bool] = mapped_column(Boolean, default=False)

    user: Mapped[User] = relationship(back_populates="devices")


class Dialog(Base):
    """Личный диалог двух пользователей, уникальная пара."""

    __tablename__ = "dialogs"
    __table_args__ = (UniqueConstraint("user_low", "user_high", name="uq_dialog_pair"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_low: Mapped[int] = mapped_column(Integer, index=True)
    user_high: Mapped[int] = mapped_column(Integer, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)

    messages: Mapped[list["Message"]] = relationship(back_populates="dialog")


class ContactAlias(Base):
    """Ник, который владелец поставил собеседнику."""

    __tablename__ = "contact_aliases"
    __table_args__ = (UniqueConstraint("owner_id", "peer_id", name="uq_alias"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    owner_id: Mapped[int] = mapped_column(Integer, index=True)
    peer_id: Mapped[int] = mapped_column(Integer, index=True)
    nick: Mapped[str] = mapped_column(String(60))


class Message(Base):
    """Сообщение диалога."""

    __tablename__ = "messages"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    dialog_id: Mapped[int] = mapped_column(ForeignKey("dialogs.id"), index=True)
    author_id: Mapped[int] = mapped_column(Integer, index=True)
    type: Mapped[str] = mapped_column(String(16), default="text")
    text: Mapped[str | None] = mapped_column(Text, nullable=True)
    reply_to_id: Mapped[int | None] = mapped_column(Integer, nullable=True)
    edited_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, index=True)

    dialog: Mapped[Dialog] = relationship(back_populates="messages")
    attachments: Mapped[list["Attachment"]] = relationship(back_populates="message")
    receipts: Mapped[list["MessageReceipt"]] = relationship(back_populates="message")


class Attachment(Base):
    """Вложение: фото, видео или произвольный файл."""

    __tablename__ = "attachments"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    message_id: Mapped[int] = mapped_column(ForeignKey("messages.id"), index=True)
    kind: Mapped[str] = mapped_column(String(16))
    orig_name: Mapped[str] = mapped_column(String(255))
    mime: Mapped[str] = mapped_column(String(120))
    size: Mapped[int] = mapped_column(Integer)
    orig_size: Mapped[int] = mapped_column(Integer)
    stored_name: Mapped[str] = mapped_column(String(80))
    gzip: Mapped[bool] = mapped_column(Boolean, default=False)
    preview_name: Mapped[str | None] = mapped_column(String(80), nullable=True)
    duration_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    album_order: Mapped[int] = mapped_column(Integer, default=0)

    message: Mapped[Message] = relationship(back_populates="attachments")


class MessageReceipt(Base):
    """Доставка и прочтение сообщения участником."""

    __tablename__ = "message_receipts"
    __table_args__ = (UniqueConstraint("message_id", "user_id", name="uq_receipt"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    message_id: Mapped[int] = mapped_column(ForeignKey("messages.id"), index=True)
    user_id: Mapped[int] = mapped_column(Integer, index=True)
    delivered_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    read_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)

    message: Mapped[Message] = relationship(back_populates="receipts")

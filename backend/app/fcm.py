"""Отправка маскировочных push через FCM."""

from __future__ import annotations

import logging
from pathlib import Path

from app.config import settings

log = logging.getLogger("chat.fcm")
_app = None
_tried = False


def _firebase():
    """Инициализировать firebase-admin один раз, если есть ключ."""
    global _app, _tried
    if _tried:
        return _app
    _tried = True
    cred_path = settings.firebase_credentials
    if not cred_path or not Path(cred_path).exists():
        log.info("FCM отключён: нет FIREBASE_CREDENTIALS")
        return None
    try:
        import firebase_admin
        from firebase_admin import credentials

        _app = firebase_admin.initialize_app(credentials.Certificate(cred_path))
    except Exception as exc:  # noqa: BLE001
        log.warning("FCM не запущен: %s", exc)
        _app = None
    return _app


def send_update_push(tokens: list[str]) -> None:
    """Отправить «Доступно новое обновление» без текста переписки."""
    tokens = [t for t in tokens if t]
    if not tokens or _firebase() is None:
        return
    from firebase_admin import messaging

    for token in tokens:
        try:
            messaging.send(
                messaging.Message(
                    token=token,
                    android=messaging.AndroidConfig(priority="high"),
                    data={"kind": "update"},
                )
            )
        except Exception as exc:  # noqa: BLE001
            log.warning("FCM ошибка: %s", exc)

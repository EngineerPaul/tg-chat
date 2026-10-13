"""Диспетчер WebSocket-соединений по user_id."""

from __future__ import annotations

import json
from collections import defaultdict

from fastapi import WebSocket


class Hub:
    """Живые сокеты пользователей для реалтайма."""

    def __init__(self) -> None:
        """Создать пустой реестр соединений."""
        self.sockets: dict[int, list[WebSocket]] = defaultdict(list)

    async def add(self, user_id: int, ws: WebSocket) -> None:
        """Зарегистрировать открытый сокет."""
        self.sockets[user_id].append(ws)

    def remove(self, user_id: int, ws: WebSocket) -> None:
        """Убрать сокет при закрытии."""
        conns = self.sockets.get(user_id, [])
        if ws in conns:
            conns.remove(ws)
        if not conns:
            self.sockets.pop(user_id, None)

    def is_online(self, user_id: int) -> bool:
        """Есть ли хотя бы одно живое соединение."""
        return bool(self.sockets.get(user_id))

    async def send_user(self, user_id: int, payload: dict) -> int:
        """Отправить событие всем устройствам пользователя. Вернуть число успешных."""
        dead: list[WebSocket] = []
        sent = 0
        for ws in list(self.sockets.get(user_id, [])):
            try:
                await ws.send_text(json.dumps(payload, default=str))
                sent += 1
            except Exception:  # noqa: BLE001
                dead.append(ws)
        for ws in dead:
            self.remove(user_id, ws)
        return sent

    async def send_dialog(self, user_ids: list[int], payload: dict) -> dict[int, int]:
        """Разослать событие участникам диалога."""
        result: dict[int, int] = {}
        for uid in user_ids:
            result[uid] = await self.send_user(uid, payload)
        return result


hub = Hub()

# Чат

Личный мессенджер 1-на-1. На телефоне снаружи **Калькулятор**, внутри чат. Веб не маскируется.

Задание: `TZ 2.md`  
Как поднять сервер, зарегистрировать людей и поставить APK: **`INSTALL.md`**

## Состав

- `backend/` — FastAPI, SQLite WAL, WebSocket, ffmpeg
- `web/` — Next.js (`basePath` `/extra/chat`)
- `android/` — Kotlin, модуль `calculator` отдельно от чата
- `deploy/` — nginx проекта, snippet хоста, каталог APK
- `.github/` — CI/CD как KeepCopy

## Быстрый старт на сервере

```bash
docker network create extra_services 2>/dev/null || true
cp .env.example .env   # задайте JWT_SECRET
docker compose up -d --build
```

Сайт: `https://daystream.ru/extra/chat`  
Создать пользователя: `docker compose exec backend python scripts/create_user.py логин пароль`

Пакет Android: `ru.daystream.calc`. APK класть в `deploy/apk/chat.apk`.

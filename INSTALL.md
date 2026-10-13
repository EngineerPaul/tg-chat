# Как поднять чат и поставить на телефон

Полное задание — `TZ 2.md`. Здесь только запуск.

## 1. Что должно быть на сервере

- Docker и Docker Compose
- Сеть `extra_services` (как у KeepCopy). Если её нет:

```bash
docker network create extra_services
```

- Хостовый nginx с HTTPS (сертификаты уже на `daystream.ru`)
- Клон репозитория, например `/var/www/Diary-project/TG-chat`

## 2. Секреты GitHub (CI/CD)

В репозитории GitHub → Settings → Secrets:

| Secret | Смысл |
|---|---|
| `SSH_HOST` | сервер |
| `SSH_USER` | пользователь SSH |
| `SSH_PORT` | обычно 22 |
| `SSH_PRIVATE_KEY` | ключ CI |
| `DEPLOY_PATH` | каталог на сервере, по умолчанию `/var/www/Diary-project/TG-chat` |
| `DEPLOY_BRANCH` | необязательно, `master` |

На сервере до первого pipeline: клон в `DEPLOY_PATH`, `git fetch` без пароля, сеть `extra_services`, кусок хостового nginx (ниже).

Push в `master` собирает образы и делает `docker compose up --build -d`.

## 3. Хостовый nginx

Вставить из `deploy/host-nginx-snippet.conf`:

```
location /extra/chat/ {
    proxy_pass http://chat:80/extra/chat/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    client_max_body_size 64m;
}

location = /extra/chat {
    return 301 /extra/chat/;
}
```

Хостовый nginx должен быть в той же Docker-сети `extra_services`, чтобы резолвить имя `chat`. HTTPS не трогаем — он уже на хосте.

Перезагрузить nginx хоста.

## 4. Первый запуск вручную (без CI)

На сервере:

```bash
cd /var/www/Diary-project/TG-chat
cp .env.example .env
# в .env задайте JWT_SECRET — длинная случайная строка
docker network create extra_services 2>/dev/null || true
docker compose up -d --build
docker compose ps
```

Проверка:

- сайт: `https://daystream.ru/extra/chat`
- API: `https://daystream.ru/extra/chat/api/health`

SQLite и загрузки лежат в volume `chat_data`. Пересборка контейнера их не стирает.

## 5. Регистрация людей

На сайте: `https://daystream.ru/extra/chat/register`

Или руками в контейнере:

```bash
docker compose exec backend python scripts/create_user.py Логин "пароль 1"
```

Правила: логин 3–30 букв/цифр без пробелов; пароль 3–30 букв, цифр и пробелов, без знаков.

В Android регистрации нет.

## 6. Сборка APK и установка на телефон

На компьютере с Android Studio:

1. Открыть каталог `android/`.
2. Дождаться Gradle. Если нет wrapper — `gradle wrapper` из этого каталога или «Open» через Studio (он сам подтянет Gradle 8.9).
3. Для push положить `google-services.json` в `android/app/` и в корневом `android/app/build.gradle.kts` раскомментировать/добавить `id("com.google.gms.google-services")`, если подключите плагин. Без файла чат собирается, уведомлений не будет.
4. Build → Build Bundle(s) / APK(s) → Build APK(s). Подпись debug для первой версии уже привязана в `build.gradle.kts` (и release использует тот же debug-keystore, чтобы обновления вставали поверх, пока не заведёте свой ключ).
5. Скопировать APK на сервер:

```bash
cp app/build/outputs/apk/debug/app-debug.apk /var/www/Diary-project/TG-chat/deploy/apk/chat.apk
```

Имя файла на сервере должно быть `deploy/apk/chat.apk`. Ссылка: `https://daystream.ru/extra/chat/chat.apk`.

На телефоне:

1. Открыть `https://daystream.ru/extra/chat/install` (можно переслать).
2. Скачать APK, разрешить неизвестный источник.
3. На экране появится **Калькулятор**, не «Чат».
4. «Ввести ключ» или тусклая кнопка (log/sin) → страница активации.
5. Первый раз: логин + пароль аккаунта. Дальше только ключ.

Новый APK с тем же `applicationId` `ru.daystream.calc` и той же подписью встаёт поверх, данные остаются.

## 7. Firebase (push)

- Создать проект Firebase, Android-приложение с пакетом `ru.daystream.calc`.
- `google-services.json` — в `android/app/` при сборке APK.
- Серверный ключ (JSON сервисного аккаунта) положить на сервер и в `.env`:

```
FIREBASE_CREDENTIALS=/data/firebase.json
```

Файл ключа смонтировать в volume или скопировать в данные контейнера. Без этого push с сервера не уходит, остальной чат работает.

Текст уведомления всегда: «Доступно новое обновление».

## 8. Полезные адреса

| Что | URL |
|---|---|
| Веб-чат | https://daystream.ru/extra/chat |
| Установка | https://daystream.ru/extra/chat/install |
| Справка | https://daystream.ru/extra/chat/help |
| APK | https://daystream.ru/extra/chat/chat.apk |
| API | https://daystream.ru/extra/chat/api |
| WebSocket | wss://daystream.ru/extra/chat/api/ws |

## 9. Как пользоваться снаружи

- Телефон: калькулятор. Ключ = пароль. Неверный ключ — «Неверный ключ».
- Стрелка в чате: короткое нажатие — предыдущее приложение; удержание — список чатов.
- Свернули чат: 5 минут можно вернуться в него; потом снова калькулятор.
- Уведомление: в эти 5 минут открывает чат, иначе страницу активации.
- «Выйти» в настройках чата — сессия и push гаснут.

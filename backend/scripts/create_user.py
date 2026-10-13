"""Создать пользователя вручную в БД контейнера backend.

Пример:
  docker compose exec backend python scripts/create_user.py alice "пароль 1"
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.auth import hash_password  # noqa: E402
from app.database import SessionLocal, engine  # noqa: E402
from app.database import Base  # noqa: E402
from app.models import User  # noqa: E402
from app.validators import validate_login, validate_password  # noqa: E402


def main() -> None:
    """Прочитать логин и пароль из argv и записать пользователя."""
    if len(sys.argv) != 3:
        print("Использование: python scripts/create_user.py <логин> <пароль>")
        sys.exit(1)
    login = validate_login(sys.argv[1])
    password = validate_password(sys.argv[2])
    Base.metadata.create_all(bind=engine)
    db = SessionLocal()
    try:
        if db.query(User).filter(User.login == login).first():
            print("Логин уже есть")
            sys.exit(2)
        db.add(User(login=login, password_hash=hash_password(password)))
        db.commit()
        print(f"Пользователь {login} создан")
    finally:
        db.close()


if __name__ == "__main__":
    main()

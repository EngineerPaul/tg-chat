"""Подключение SQLAlchemy к SQLite в WAL."""

from pathlib import Path

from sqlalchemy import create_engine, event
from sqlalchemy.orm import DeclarativeBase, sessionmaker

from app.config import settings


class Base(DeclarativeBase):
    """Базовый класс моделей. Схема через SQLAlchemy, чтобы позже сменить SQLite."""

    pass


def _sqlite_path() -> Path:
    """Путь к файлу базы в каталоге данных."""
    root = Path(settings.data_dir)
    root.mkdir(parents=True, exist_ok=True)
    (root / "uploads").mkdir(exist_ok=True)
    return root / "chat.db"


def make_engine():
    """Создать движок SQLite с WAL."""
    path = _sqlite_path()
    engine = create_engine(
        f"sqlite:///{path}",
        connect_args={"check_same_thread": False},
        pool_pre_ping=True,
    )

    @event.listens_for(engine, "connect")
    def _enable_wal(dbapi_connection, _connection_record):
        """Включить WAL и внешние ключи на каждом соединении."""
        cursor = dbapi_connection.cursor()
        cursor.execute("PRAGMA journal_mode=WAL")
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.close()

    return engine


engine = make_engine()
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False)


def get_db():
    """Сессия БД для FastAPI Depends."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

"""Настройки сервера из переменных окружения."""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Конфигурация бэкенда: секреты, пути и публичные адреса."""

    jwt_secret: str = "change-me-in-production"
    jwt_algorithm: str = "HS256"
    access_ttl_seconds: int = 20 * 60
    refresh_ttl_seconds: int = 10 * 365 * 24 * 3600
    cors_origins: str = "https://daystream.ru"
    public_base: str = "https://daystream.ru/extra/chat"
    data_dir: str = "/data"
    firebase_credentials: str = ""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    def cors_origin_list(self) -> list[str]:
        """Вернуть список разрешённых origin для CORS."""
        return [item.strip() for item in self.cors_origins.split(",") if item.strip()]


settings = Settings()

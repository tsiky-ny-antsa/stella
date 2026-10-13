"""Configuration de l'application (variables d'environnement / fichier .env)."""
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict

PLACEHOLDER_SECRET = "replace-with-a-long-random-secret-before-running"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    APP_NAME: str = "Stella API"
    APP_ENV: str = "development"
    API_V1_PREFIX: str = "/api/v1"
    DATABASE_URL: str = "sqlite:///./stella_dev.db"
    JWT_SECRET_KEY: str = PLACEHOLDER_SECRET
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 720
    CORS_ORIGINS: str = "http://localhost:8080"

    # Simulateur (mock de la partie électronique) : développement uniquement.
    SIMULATOR_ENABLED: bool = True
    SIMULATOR_DEVICE_ID: str = "ge-ctrl-01"

    # Appareil / commandes
    DEVICE_HEARTBEAT_INTERVAL_SECONDS: int = 30
    DEVICE_OFFLINE_AFTER_SECONDS: int = 90
    DEVICE_COMMAND_POLL_SECONDS: int = 4
    COMMAND_DEFAULT_TTL_SECONDS: int = 60
    COMMAND_LEASE_SECONDS: int = 15
    COMMANDS_REQUIRE_ONLINE: bool = True

    # Données de démonstration (développement uniquement, jamais en production)
    SEED_DEV_DATA: bool = True
    SEED_ADMIN_PASSWORD: str = "admin-dev-only"
    SEED_OPERATEUR_PASSWORD: str = "operateur-dev-only"
    SEED_LECTURE_PASSWORD: str = "lecture-dev-only"
    SEED_DEVICE_KEY: str = "dev-device-key-ge-ctrl-01"

    @property
    def is_dev(self) -> bool:
        return self.APP_ENV.lower() in {"development", "dev", "test"}

    @property
    def cors_origins_list(self) -> list[str]:
        return [o.strip() for o in self.CORS_ORIGINS.split(",") if o.strip()]

    def validate_for_runtime(self) -> None:
        """Refuse de démarrer en production avec une configuration dangereuse."""
        if not self.is_dev:
            if self.JWT_SECRET_KEY == PLACEHOLDER_SECRET or len(self.JWT_SECRET_KEY) < 32:
                raise RuntimeError("JWT_SECRET_KEY doit être défini (>= 32 caractères) hors développement.")
            if self.SIMULATOR_ENABLED:
                raise RuntimeError("SIMULATOR_ENABLED doit être false hors développement.")
            if self.SEED_DEV_DATA:
                raise RuntimeError("SEED_DEV_DATA doit être false hors développement.")


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()

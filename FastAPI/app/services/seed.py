"""Données de démonstration — DÉVELOPPEMENT UNIQUEMENT (jamais exécuté hors APP_ENV=development)."""
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.security import hash_device_key, hash_password
from app.db.models import Device, DeviceCredential, User
from app.services.devices import create_default_config


def seed_dev_data(db: Session) -> None:
    if not (settings.is_dev and settings.SEED_DEV_DATA):
        return
    for username, display, role, pwd in (
        ("admin", "Administrateur", "ADMIN", settings.SEED_ADMIN_PASSWORD),
        ("operateur", "Opérateur", "OPERATEUR", settings.SEED_OPERATEUR_PASSWORD),
        ("lecture", "Lecture seule", "LECTURE", settings.SEED_LECTURE_PASSWORD),
    ):
        if not db.scalar(select(User).where(User.username == username)):
            db.add(User(username=username, password_hash=hash_password(pwd), display_name=display, role=role))
    did = settings.SIMULATOR_DEVICE_ID
    if not db.scalar(select(Device).where(Device.device_id == did)):
        db.add(Device(device_id=did, name="Contrôleur GE (simulé)", location="Maquette", is_simulated=True))
        db.flush()
        db.add(DeviceCredential(device_id=did, key_hash=hash_device_key(settings.SEED_DEVICE_KEY)))
        create_default_config(db, did)
    db.commit()

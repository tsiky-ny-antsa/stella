import os

# Base de test SÉPARÉE (SQLite en mémoire) : ne jamais pointer vers la base du projet réel.
os.environ["DATABASE_URL"] = "sqlite://"
os.environ["APP_ENV"] = "test"
os.environ["SIMULATOR_ENABLED"] = "true"
os.environ["SEED_DEV_DATA"] = "true"
os.environ["PASSWORD_HASH_ITERATIONS"] = "1000"

import pytest
from fastapi.testclient import TestClient

from app.core.config import settings
from app.db import models  # noqa: F401
from app.db.session import Base, SessionLocal, engine
from app.main import app
from app.services import simulator as sim
from app.services.seed import seed_dev_data

API = settings.API_V1_PREFIX
DEVICE_ID = settings.SIMULATOR_DEVICE_ID


@pytest.fixture()
def db():
    Base.metadata.drop_all(engine)
    Base.metadata.create_all(engine)
    sim.clear_registry()
    s = SessionLocal()
    seed_dev_data(s)
    yield s
    s.close()
    sim.clear_registry()


@pytest.fixture()
def client(db):
    with TestClient(app) as c:
        yield c


def _login(client, username, password):
    r = client.post(f"{API}/auth/login", json={"username": username, "password": password})
    assert r.status_code == 200, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


@pytest.fixture()
def admin(client):
    return _login(client, "admin", settings.SEED_ADMIN_PASSWORD)


@pytest.fixture()
def operateur(client):
    return _login(client, "operateur", settings.SEED_OPERATEUR_PASSWORD)


@pytest.fixture()
def lecture(client):
    return _login(client, "lecture", settings.SEED_LECTURE_PASSWORD)


@pytest.fixture()
def dev_key():
    return {"X-Device-Key": settings.SEED_DEVICE_KEY}


def heartbeat_body(**over):
    body = {
        "captured_at": "2026-10-12T16:20:00Z", "uptime_ms": 1000, "firmware_version": "0.1.0",
        "state": "NORMAL", "control_mode": "AUTO",
        "inputs": {"jirama_present": True, "ge_present": False, "ups_low_battery": None,
                   "ats_on_jirama": True, "ats_on_ge": False, "ge_fault": False,
                   "emergency_stop": False, "manual_selector": False},
        "outputs": {}, "ge": {"detected": False, "stable": False, "running": False, "start_attempt": 0},
        "fault": {"code": "NONE", "message": None},
    }
    body.update(over)
    return body


@pytest.fixture()
def online(client, dev_key):
    """Appareil considéré en ligne (heartbeat reçu)."""
    r = client.post(f"{API}/devices/{DEVICE_ID}/heartbeat", json=heartbeat_body(), headers=dev_key)
    assert r.status_code == 200
    return r.json()

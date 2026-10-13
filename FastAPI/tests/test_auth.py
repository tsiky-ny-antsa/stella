from datetime import datetime, timedelta, timezone

import jwt

from app.core.config import settings
from tests.conftest import API, DEVICE_ID


def test_login_ok_and_me(client):
    r = client.post(f"{API}/auth/login", json={"username": "operateur", "password": settings.SEED_OPERATEUR_PASSWORD})
    assert r.status_code == 200
    body = r.json()
    assert body["token_type"] == "Bearer" and body["expires_in"] == settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60
    assert body["user"]["role"] == "OPERATEUR"
    me = client.get(f"{API}/auth/me", headers={"Authorization": f"Bearer {body['access_token']}"})
    assert me.status_code == 200 and me.json()["username"] == "operateur"


def test_login_wrong_password_and_unknown_user(client):
    for u, p in (("operateur", "mauvais"), ("inconnu", "x")):
        r = client.post(f"{API}/auth/login", json={"username": u, "password": p})
        assert r.status_code == 401
        err = r.json()["error"]
        assert err["code"] == "INVALID_CREDENTIALS" and err["request_id"]


def test_login_validation_error_format(client):
    r = client.post(f"{API}/auth/login", json={"username": ""})
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "VALIDATION_ERROR"


def test_no_token_and_garbage_token(client):
    assert client.get(f"{API}/auth/me").status_code == 401
    r = client.get(f"{API}/auth/me", headers={"Authorization": "Bearer abc.def.ghi"})
    assert r.status_code == 401


def test_expired_token_rejected(client, db):
    from sqlalchemy import select
    from app.db.models import User
    uid = db.scalar(select(User.id).where(User.username == "admin"))
    old = datetime.now(timezone.utc) - timedelta(hours=2)
    tok = jwt.encode({"sub": uid, "role": "ADMIN", "iat": old, "exp": old + timedelta(minutes=1)},
                     settings.JWT_SECRET_KEY, algorithm="HS256")
    r = client.get(f"{API}/auth/me", headers={"Authorization": f"Bearer {tok}"})
    assert r.status_code == 401 and r.json()["error"]["code"] == "TOKEN_INVALID_OR_EXPIRED"


def test_passwords_are_hashed(db):
    from sqlalchemy import select
    from app.db.models import User
    for u in db.scalars(select(User)).all():
        assert u.password_hash.startswith("pbkdf2_sha256$") and settings.SEED_ADMIN_PASSWORD not in u.password_hash


def test_roles_enforced_in_backend(client, admin, operateur, lecture, online):
    cmd = {"type": "START_GE", "request_id": "req-role-0001"}
    assert client.post(f"{API}/devices/{DEVICE_ID}/commands", json=cmd, headers=lecture).status_code == 403
    assert client.get(f"{API}/devices/{DEVICE_ID}/status", headers=lecture).status_code == 200  # lecture OK
    assert client.get(f"{API}/users", headers=operateur).status_code == 403
    assert client.get(f"{API}/audit", headers=operateur).status_code == 403
    assert client.get(f"{API}/users", headers=admin).status_code == 200
    assert client.patch(f"{API}/devices/{DEVICE_ID}/config", json={"cooldown_seconds": 30},
                        headers=operateur).status_code == 403
    assert client.post(f"{API}/devices/{DEVICE_ID}/commands", json=cmd, headers=operateur).status_code == 202


def test_user_management_admin(client, admin):
    r = client.post(f"{API}/users", headers=admin, json={"username": "nouveau", "password": "motdepasse1",
                                                          "display_name": "Nouveau", "role": "LECTURE"})
    assert r.status_code == 201 and "password" not in r.text
    dup = client.post(f"{API}/users", headers=admin, json={"username": "nouveau", "password": "motdepasse1",
                                                           "display_name": "N", "role": "LECTURE"})
    assert dup.status_code == 409
    uid = r.json()["id"]
    assert client.patch(f"{API}/users/{uid}", headers=admin, json={"is_active": False}).status_code == 200
    bad = client.post(f"{API}/auth/login", json={"username": "nouveau", "password": "motdepasse1"})
    assert bad.status_code == 401  # compte désactivé
    me = client.get(f"{API}/auth/me", headers=admin).json()
    assert client.patch(f"{API}/users/{me['id']}", headers=admin, json={"role": "LECTURE"}).status_code == 409


def test_health_is_public_and_minimal(client):
    r = client.get("/health")
    assert r.status_code == 200 and r.json() == {"status": "ok"}


def test_production_refuses_unsafe_config():
    import pytest
    from app.core.config import Settings
    with pytest.raises(RuntimeError):
        Settings(APP_ENV="production", SIMULATOR_ENABLED=False, SEED_DEV_DATA=False).validate_for_runtime()
    with pytest.raises(RuntimeError):
        Settings(APP_ENV="production", JWT_SECRET_KEY="x" * 40, SIMULATOR_ENABLED=True,
                 SEED_DEV_DATA=False).validate_for_runtime()
    Settings(APP_ENV="production", JWT_SECRET_KEY="x" * 40, SIMULATOR_ENABLED=False,
             SEED_DEV_DATA=False).validate_for_runtime()

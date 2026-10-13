"""Hash de mots de passe, clés d'appareil et jetons JWT (sans dépendance native)."""
import base64
import hashlib
import hmac
import secrets
from datetime import datetime, timedelta, timezone

import jwt

from app.core.config import settings

import os

_ITERATIONS = int(os.getenv("PASSWORD_HASH_ITERATIONS", "200000"))
_ALGO = "HS256"


def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    dk = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, _ITERATIONS)
    return f"pbkdf2_sha256${_ITERATIONS}${base64.b64encode(salt).decode()}${base64.b64encode(dk).decode()}"


def verify_password(password: str, stored: str) -> bool:
    try:
        _, iters, salt_b64, dk_b64 = stored.split("$")
        salt, expected = base64.b64decode(salt_b64), base64.b64decode(dk_b64)
        dk = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, int(iters))
        return hmac.compare_digest(dk, expected)
    except Exception:
        return False


def generate_device_key() -> str:
    return secrets.token_urlsafe(32)


def hash_device_key(key: str) -> str:
    """La clé d'appareil est aléatoire à forte entropie : SHA-256 suffit (jamais stockée en clair)."""
    return hashlib.sha256(key.encode()).hexdigest()


def verify_device_key(key: str, key_hash: str) -> bool:
    return hmac.compare_digest(hash_device_key(key), key_hash)


def create_access_token(user_id: str, role: str) -> tuple[str, int]:
    expires_in = settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60
    now = datetime.now(timezone.utc)
    payload = {"sub": user_id, "role": role, "iat": now, "exp": now + timedelta(seconds=expires_in)}
    return jwt.encode(payload, settings.JWT_SECRET_KEY, algorithm=_ALGO), expires_in


def decode_access_token(token: str) -> dict | None:
    try:
        return jwt.decode(token, settings.JWT_SECRET_KEY, algorithms=[_ALGO])
    except jwt.PyJWTError:
        return None

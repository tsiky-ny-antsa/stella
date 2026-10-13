import base64
from datetime import datetime, timezone


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def iso(dt: datetime | None) -> str | None:
    return dt.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ") if dt else None


def encode_cursor(dt: datetime, id_: str) -> str:
    return base64.urlsafe_b64encode(f"{dt.isoformat()}|{id_}".encode()).decode()


def decode_cursor(cursor: str | None) -> tuple[datetime, str] | None:
    if not cursor:
        return None
    try:
        raw = base64.urlsafe_b64decode(cursor.encode()).decode()
        ts, id_ = raw.split("|", 1)
        return datetime.fromisoformat(ts), id_
    except Exception:
        from app.core.errors import ApiError
        raise ApiError(422, "VALIDATION_ERROR", "Curseur de pagination invalide.")

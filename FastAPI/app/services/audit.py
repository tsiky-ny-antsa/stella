from sqlalchemy.orm import Session

from app.db.models import AuditLog
from app.services.common import iso


def log(db: Session, action: str, user_id: str | None = None, device_id: str | None = None,
        details: dict | None = None) -> None:
    db.add(AuditLog(action=action, user_id=user_id, device_id=device_id, details_json=details or {}))


def to_dict(a: AuditLog) -> dict:
    return {"id": a.id, "user_id": a.user_id, "device_id": a.device_id, "action": a.action,
            "details": a.details_json, "created_at": iso(a.created_at)}

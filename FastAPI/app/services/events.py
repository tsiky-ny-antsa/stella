from sqlalchemy import and_, or_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.errors import ApiError
from app.db.models import Alert, Device, DeviceEvent, User
from app.schemas.common import EVENT_TYPES
from app.schemas.device import EventIn
from app.services import audit
from app.services.common import decode_cursor, encode_cursor, iso, utcnow

ALERT_TITLES = {
    "JIRAMA_OFF": "Coupure JIRAMA", "UPS_LOW_BATTERY": "Batterie UPS faible", "START_FAILED": "Échec de démarrage du GE",
    "STOP_FAILED": "Échec d'arrêt du GE", "FAULT": "Défaut du contrôleur", "EMERGENCY_STOP": "Arrêt d'urgence",
    "WAIT_REMINDER": "Rappel : JIRAMA toujours absente", "MODE_CHANGED": "Changement de mode",
}


def event_to_dict(e: DeviceEvent) -> dict:
    return {"event_id": e.event_id, "device_id": e.device_id, "seq": e.seq, "type": e.type, "severity": e.severity,
            "state": e.state, "message": e.message, "data": e.data_json, "captured_at": iso(e.captured_at),
            "received_at": iso(e.received_at)}


def ingest_event(db: Session, device: Device, body: EventIn) -> tuple[dict, bool]:
    """Idempotent sur event_id : (event, created). created=False -> déjà reçu (HTTP 200)."""
    if body.type not in EVENT_TYPES:
        raise ApiError(422, "VALIDATION_ERROR", "Type d'événement inconnu.", {"type": body.type})
    existing = db.scalar(select(DeviceEvent).where(DeviceEvent.event_id == body.event_id))
    if existing:
        if existing.device_id != device.device_id:
            raise ApiError(409, "EVENT_ID_CONFLICT", "event_id déjà utilisé par un autre appareil.")
        return event_to_dict(existing), False
    ev = DeviceEvent(event_id=body.event_id, device_id=device.device_id, seq=body.seq, type=body.type,
                     severity=body.severity, state=body.state, message=body.message, data_json=body.data,
                     captured_at=body.captured_at, received_at=utcnow())
    db.add(ev)
    # Alerte pour tout événement WARNING/CRITICAL (sauf simple changement de mode / rappel redondant)
    if body.severity in ("WARNING", "CRITICAL") and body.type != "MODE_CHANGED":
        db.add(Alert(device_id=device.device_id, event_id=body.event_id, kind=body.type, severity=body.severity,
                     title=ALERT_TITLES.get(body.type, body.type), message=body.message))
    try:
        db.commit()
    except IntegrityError:  # course entre deux retransmissions
        db.rollback()
        existing = db.scalar(select(DeviceEvent).where(DeviceEvent.event_id == body.event_id))
        return event_to_dict(existing), False
    return event_to_dict(ev), True


def list_events(db: Session, device_id: str, limit: int, before: str | None) -> dict:
    q = select(DeviceEvent).where(DeviceEvent.device_id == device_id)
    cur = decode_cursor(before)
    if cur:
        ts, id_ = cur
        q = q.where(or_(DeviceEvent.received_at < ts, and_(DeviceEvent.received_at == ts, DeviceEvent.id < id_)))
    rows = db.scalars(q.order_by(DeviceEvent.received_at.desc(), DeviceEvent.id.desc()).limit(limit + 1)).all()
    page = rows[:limit]
    nxt = encode_cursor(page[-1].received_at, page[-1].id) if len(rows) > limit else None
    return {"items": [event_to_dict(e) for e in page], "next_cursor": nxt}


# ------------------------------------------------------------------ alertes
def alert_to_dict(a: Alert) -> dict:
    return {"alert_id": a.id, "device_id": a.device_id, "event_id": a.event_id, "kind": a.kind,
            "severity": a.severity, "title": a.title, "message": a.message, "status": a.status,
            "created_at": iso(a.created_at), "acknowledged_by": a.acknowledged_by,
            "acknowledged_at": iso(a.acknowledged_at)}


def list_alerts(db: Session, status: str | None, limit: int) -> list[dict]:
    q = select(Alert)
    if status:
        q = q.where(Alert.status == status)
    return [alert_to_dict(a) for a in db.scalars(q.order_by(Alert.created_at.desc()).limit(limit)).all()]


def ack_alert(db: Session, user: User, alert_id: str) -> dict:
    a = db.get(Alert, alert_id)
    if not a:
        raise ApiError(404, "ALERT_NOT_FOUND", "Alerte introuvable.")
    if a.status != "ACKNOWLEDGED":
        a.status, a.acknowledged_by, a.acknowledged_at = "ACKNOWLEDGED", user.id, utcnow()
        # N'acquitte QUE l'alerte : ne réinitialise aucun défaut physique.
        audit.log(db, "ALERT_ACKED", user.id, a.device_id, {"alert_id": a.id, "kind": a.kind})
        db.commit()
    return alert_to_dict(a)

"""File de commandes : QUEUED -> DELIVERED (bail) -> ACCEPTED/REJECTED -> RUNNING -> SUCCEEDED/FAILED | EXPIRED.

Règle d'or : SUCCEEDED ne vient JAMAIS de l'écriture en base mais d'un accusé du contrôleur/simulateur.
"""
from datetime import timedelta

from sqlalchemy import and_, or_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.errors import ApiError
from app.db.models import Command, Device, DeviceStatus, User
from app.schemas.device import AckIn, CommandCreate
from app.services import audit
from app.services.common import decode_cursor, encode_cursor, iso, utcnow
from app.services.devices import connection_info

ACTIVE = ("QUEUED", "DELIVERED", "ACCEPTED", "RUNNING")
TERMINAL = ("SUCCEEDED", "REJECTED", "FAILED", "EXPIRED", "CANCELLED")
ACK_TRANSITIONS = {
    "QUEUED": {"ACCEPTED", "REJECTED"},
    "DELIVERED": {"ACCEPTED", "REJECTED"},
    "ACCEPTED": {"RUNNING", "SUCCEEDED", "FAILED"},
    "RUNNING": {"SUCCEEDED", "FAILED"},
}
DISPLAY_MESSAGES = {
    "QUEUED": "Demande enregistrée ; exécution non confirmée.",
    "DELIVERED": "Transmise au contrôleur ; exécution non confirmée.",
    "ACCEPTED": "Acceptée par le contrôleur ; exécution en cours de vérification.",
    "RUNNING": "En cours d'exécution.",
    "SUCCEEDED": "Résultat confirmé par le contrôleur.",
    "REJECTED": "Refusée par le contrôleur.",
    "FAILED": "Échec rapporté par le contrôleur.",
    "EXPIRED": "Expirée avant prise en charge.",
    "CANCELLED": "Annulée.",
}


def command_to_dict(c: Command) -> dict:
    return {"command_id": c.id, "request_id": c.request_id, "device_id": c.device_id, "type": c.type,
            "status": c.status, "parameters": c.parameters_json, "reason": c.reason, "attempt": c.attempt,
            "created_at": iso(c.created_at), "expires_at": iso(c.expires_at), "updated_at": iso(c.updated_at),
            "result": c.result_json, "message": DISPLAY_MESSAGES[c.status]}


def expire_due(db: Session, device_id: str | None = None) -> int:
    """Expire les commandes jamais prises en charge (QUEUED/DELIVERED) dont le TTL est dépassé."""
    now = utcnow()
    q = select(Command).where(Command.status.in_(("QUEUED", "DELIVERED")), Command.expires_at < now)
    if device_id:
        q = q.where(Command.device_id == device_id)
    n = 0
    for c in db.scalars(q).all():
        c.status, c.updated_at = "EXPIRED", now
        n += 1
    if n:
        db.commit()
    return n


# ------------------------------------------------------------------ côté mobile
def create_command(db: Session, user: User, device: Device, body: CommandCreate) -> tuple[dict, bool]:
    """Retourne (commande, créée). Idempotent sur (device_id, request_id)."""
    params = dict(body.parameters)
    if body.type == "SET_MODE" and params.get("mode") not in ("AUTO", "REMOTE"):
        raise ApiError(422, "VALIDATION_ERROR", "SET_MODE exige parameters.mode = AUTO ou REMOTE.")

    existing = db.scalar(select(Command).where(Command.device_id == device.device_id,
                                               Command.request_id == body.request_id))
    if existing:
        if existing.type != body.type or existing.parameters_json != params:
            raise ApiError(409, "REQUEST_ID_CONFLICT", "request_id déjà utilisé avec une commande différente.")
        return command_to_dict(existing), False

    expire_due(db, device.device_id)
    status_row = db.get(DeviceStatus, device.device_id)
    if settings.COMMANDS_REQUIRE_ONLINE and connection_info(status_row)["status"] != "ONLINE":
        raise ApiError(503, "DEVICE_OFFLINE", "Équipement hors ligne : commande non enregistrée.",
                       {"device_id": device.device_id})

    # Refus anticipé selon l'état CONNU (le contrôleur revérifie de toute façon avec ses entrées réelles).
    if status_row:
        s = status_row.status_json
        inputs = s.get("inputs") or {}
        reason = None
        if inputs.get("manual_selector") is True or s.get("control_mode") == "MANUAL":
            reason = "MANUAL_MODE_ACTIVE"
        elif inputs.get("emergency_stop") is True:
            reason = "EMERGENCY_STOP_ACTIVE"
        elif s["state"] == "FAULT" and body.type != "RESET_FAULT":
            reason = "FAULT_ACTIVE"
        elif body.type == "RESET_FAULT" and s["state"] != "FAULT":
            reason = "NO_FAULT"
        if reason:
            raise ApiError(409, "COMMAND_NOT_ALLOWED", "La commande n'est pas autorisée dans l'état actuel.",
                           {"state": s["state"], "reason": reason})

    dup = db.scalar(select(Command).where(Command.device_id == device.device_id, Command.type == body.type,
                                          Command.status.in_(ACTIVE)))
    if dup:
        raise ApiError(409, "COMMAND_NOT_ALLOWED", "Une commande identique est déjà en cours.",
                       {"reason": "DUPLICATE_ACTIVE_COMMAND", "command_id": dup.id})

    now = utcnow()
    cmd = Command(request_id=body.request_id, device_id=device.device_id, requested_by=user.id, type=body.type,
                  parameters_json=params, reason=body.reason, status="QUEUED", created_at=now, updated_at=now,
                  expires_at=now + timedelta(seconds=settings.COMMAND_DEFAULT_TTL_SECONDS))
    db.add(cmd)
    audit.log(db, "COMMAND_CREATED", user.id, device.device_id,
              {"type": body.type, "request_id": body.request_id, "reason": body.reason})
    try:
        db.commit()
    except IntegrityError:  # double soumission concurrente
        db.rollback()
        existing = db.scalar(select(Command).where(Command.device_id == device.device_id,
                                                   Command.request_id == body.request_id))
        return command_to_dict(existing), False
    return command_to_dict(cmd), True


def get_command(db: Session, command_id: str) -> Command:
    c = db.get(Command, command_id)
    if not c:
        raise ApiError(404, "COMMAND_NOT_FOUND", "Commande introuvable.")
    return c


def list_commands(db: Session, device_id: str, limit: int, before: str | None) -> dict:
    expire_due(db, device_id)
    q = select(Command).where(Command.device_id == device_id)
    cur = decode_cursor(before)
    if cur:
        ts, id_ = cur
        q = q.where(or_(Command.created_at < ts, and_(Command.created_at == ts, Command.id < id_)))
    rows = db.scalars(q.order_by(Command.created_at.desc(), Command.id.desc()).limit(limit + 1)).all()
    page = rows[:limit]
    nxt = encode_cursor(page[-1].created_at, page[-1].id) if len(rows) > limit else None
    return {"items": [command_to_dict(c) for c in page], "next_cursor": nxt}


# ------------------------------------------------------------------ côté contrôleur
def next_for_device(db: Session, device: Device) -> dict | None:
    """Livre la plus ancienne commande disponible avec un bail. Livraison != exécution."""
    now = utcnow()
    expire_due(db, device.device_id)
    c = db.scalar(select(Command).where(
        Command.device_id == device.device_id,
        or_(Command.status == "QUEUED",
            and_(Command.status == "DELIVERED", Command.lease_expires_at < now)),
        Command.expires_at >= now,
    ).order_by(Command.created_at.asc()).limit(1))
    if not c:
        return None
    c.status, c.attempt = "DELIVERED", c.attempt + 1
    c.lease_expires_at = now + timedelta(seconds=settings.COMMAND_LEASE_SECONDS)
    c.updated_at = now
    db.commit()
    return {"command_id": c.id, "type": c.type, "parameters": c.parameters_json, "created_at": iso(c.created_at),
            "expires_at": iso(c.expires_at), "attempt": c.attempt}


def ack(db: Session, device: Device, command_id: str, body: AckIn) -> dict:
    c = db.get(Command, command_id)
    if not c or c.device_id != device.device_id:
        raise ApiError(404, "COMMAND_NOT_FOUND", "Commande introuvable pour cet appareil.")
    expire_due(db, device.device_id)
    db.refresh(c)
    if c.status == body.stage:       # accusé rejoué : idempotent
        return command_to_dict(c)
    if c.status == "EXPIRED":
        raise ApiError(409, "COMMAND_EXPIRED", "Commande expirée avant prise en charge.")
    if body.stage not in ACK_TRANSITIONS.get(c.status, set()):
        raise ApiError(409, "INVALID_TRANSITION", f"Transition {c.status} -> {body.stage} interdite.",
                       {"status": c.status, "stage": body.stage})
    now = utcnow()
    entry = {"stage": body.stage, "state": body.state, "error_code": body.error_code, "message": body.message,
             "captured_at": iso(body.captured_at), "received_at": iso(now)}
    history = list((c.result_json or {}).get("history", [])) + [entry]
    c.result_json = {**entry, "history": history}
    c.status, c.updated_at, c.lease_expires_at = body.stage, now, None
    db.commit()
    return command_to_dict(c)


def in_progress_count(db: Session) -> int:
    return len(db.scalars(select(Command.id).where(Command.status.in_(ACTIVE))).all())

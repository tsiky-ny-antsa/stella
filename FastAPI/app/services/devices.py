from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.errors import ApiError
from app.core.security import verify_device_key
from app.db.models import Alert, Device, DeviceConfig, DeviceCredential, DeviceStatus
from app.schemas.device import ConfigPatch, HeartbeatIn
from app.services import audit
from app.services.common import iso, utcnow
from app.services.virtual_device import DEFAULT_CONFIG


def get_device(db: Session, device_id: str) -> Device:
    dev = db.scalar(select(Device).where(Device.device_id == device_id, Device.is_active.is_(True)))
    if not dev:
        raise ApiError(404, "DEVICE_NOT_FOUND", "Équipement introuvable.", {"device_id": device_id})
    return dev


def authenticate_device(db: Session, device_id: str, key: str | None) -> Device:
    """Clé distincte par appareil. Clé invalide / révoquée / appareil désactivé -> 401."""
    err = ApiError(401, "INVALID_DEVICE_KEY", "Clé d'appareil invalide.")
    if not key:
        raise err
    dev = db.scalar(select(Device).where(Device.device_id == device_id, Device.is_active.is_(True)))
    if not dev:
        raise err
    creds = db.scalars(select(DeviceCredential).where(DeviceCredential.device_id == device_id,
                                                      DeviceCredential.revoked_at.is_(None))).all()
    if not any(verify_device_key(key, c.key_hash) for c in creds):
        raise err
    return dev


# ------------------------------------------------------------------ configuration versionnée
def current_config(db: Session, device_id: str) -> DeviceConfig | None:
    return db.scalar(select(DeviceConfig).where(DeviceConfig.device_id == device_id)
                     .order_by(DeviceConfig.version.desc()).limit(1))


def config_to_dict(c: DeviceConfig) -> dict:
    return {**c.config_json, "version": c.version}


def create_default_config(db: Session, device_id: str) -> DeviceConfig:
    cfg = {k: v for k, v in DEFAULT_CONFIG.items() if k != "version"}
    row = DeviceConfig(device_id=device_id, version=1, config_json=cfg)
    db.add(row)
    return row


def get_config(db: Session, device_id: str) -> dict:
    c = current_config(db, device_id)
    if not c:
        c = create_default_config(db, device_id)
        db.commit()
    return config_to_dict(c)


def update_config(db: Session, device_id: str, patch: ConfigPatch, user_id: str) -> dict:
    changes = patch.model_dump(exclude_unset=True)
    cur = current_config(db, device_id) or create_default_config(db, device_id)
    if not changes:
        raise ApiError(422, "VALIDATION_ERROR", "Aucun champ à modifier.")
    new = {**cur.config_json, **changes}
    row = DeviceConfig(device_id=device_id, version=cur.version + 1, config_json=new, updated_by=user_id)
    db.add(row)
    audit.log(db, "CONFIG_UPDATED", user_id, device_id, {"changes": changes, "version": row.version})
    db.commit()
    return config_to_dict(row)


# ------------------------------------------------------------------ heartbeat / état
def _ats_position(on_jirama, on_ge):
    if on_jirama is None or on_ge is None:
        return None  # inconnu
    if on_jirama and not on_ge:
        return "JIRAMA"
    if on_ge and not on_jirama:
        return "GE"
    return "INCONSISTENT" if (on_jirama and on_ge) else "TRANSIT"


def ingest_heartbeat(db: Session, device: Device, hb: HeartbeatIn) -> dict:
    now = utcnow()
    i = hb.inputs
    status = {
        "state": hb.state,
        "control_mode": hb.control_mode,
        "inputs": {
            "jirama_present": i.jirama_present, "ge_present": i.ge_present,
            "ups_low_battery": i.ups_low_battery,
            "ats_position": _ats_position(i.ats_on_jirama, i.ats_on_ge),
            "ge_fault": i.ge_fault, "emergency_stop": i.emergency_stop, "manual_selector": i.manual_selector,
        },
        "outputs": hb.outputs.model_dump(),
        "ge": hb.ge.model_dump(),
        "waiting": {"remaining_seconds": hb.waiting.remaining_seconds if hb.waiting else None},
        "fault": hb.fault.model_dump(),
        "firmware_version": hb.firmware_version,
        "uptime_ms": hb.uptime_ms,
    }
    row = db.get(DeviceStatus, device.device_id)
    if row:
        row.status_json, row.captured_at, row.received_at = status, hb.captured_at, now
    else:
        db.add(DeviceStatus(device_id=device.device_id, status_json=status, captured_at=hb.captured_at,
                            received_at=now))
    if hb.firmware_version:
        device.firmware_version = hb.firmware_version
    db.commit()
    return {"received_at": iso(now), "offline_after_seconds": settings.DEVICE_OFFLINE_AFTER_SECONDS}


def connection_info(row: DeviceStatus | None) -> dict:
    if not row:
        return {"status": "NEVER_SEEN", "last_seen_at": None, "stale": True}
    age = (utcnow() - row.received_at).total_seconds()
    offline = age > settings.DEVICE_OFFLINE_AFTER_SECONDS
    return {"status": "OFFLINE" if offline else "ONLINE", "last_seen_at": iso(row.received_at), "stale": offline}


def build_status(db: Session, device: Device) -> dict:
    row = db.get(DeviceStatus, device.device_id)
    base = {"device_id": device.device_id, "connection": connection_info(row)}
    if not row:
        return {**base, "state": "UNKNOWN", "control_mode": None, "inputs": None, "ge": None,
                "waiting": None, "fault": None, "captured_at": None}
    s = row.status_json
    return {**base, "state": s["state"], "control_mode": s["control_mode"], "inputs": s["inputs"],
            "outputs": s["outputs"], "ge": s["ge"], "waiting": s["waiting"], "fault": s["fault"],
            "firmware_version": s.get("firmware_version"), "captured_at": iso(row.captured_at)}


def device_to_dict(db: Session, dev: Device) -> dict:
    return {"device_id": dev.device_id, "name": dev.name, "location": dev.location,
            "firmware_version": dev.firmware_version, "is_simulated": dev.is_simulated,
            "connection": connection_info(db.get(DeviceStatus, dev.device_id))}


def list_devices(db: Session) -> list[dict]:
    devs = db.scalars(select(Device).where(Device.is_active.is_(True)).order_by(Device.device_id)).all()
    return [device_to_dict(db, d) for d in devs]


def watchdog_offline_alerts(db: Session) -> int:
    """Crée (une seule fois) une alerte DEVICE_OFFLINE pour chaque appareil sans heartbeat récent."""
    created = 0
    for dev in db.scalars(select(Device).where(Device.is_active.is_(True))).all():
        row = db.get(DeviceStatus, dev.device_id)
        if not row or connection_info(row)["status"] != "OFFLINE":
            continue
        exists = db.scalar(select(Alert).where(Alert.device_id == dev.device_id, Alert.kind == "DEVICE_OFFLINE",
                                               Alert.status == "OPEN"))
        if not exists:
            db.add(Alert(device_id=dev.device_id, kind="DEVICE_OFFLINE", severity="CRITICAL",
                         title="Équipement hors ligne",
                         message=f"Aucun heartbeat depuis plus de {settings.DEVICE_OFFLINE_AFTER_SECONDS} s."))
            created += 1
    db.commit()
    return created

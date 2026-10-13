from fastapi import APIRouter, Depends, Request
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.deps import admin_only, any_user, device_auth, get_current_user, known_device
from app.db.models import Alert, Device, User
from app.db.session import get_db
from app.schemas.device import ConfigPatch, HeartbeatIn
from app.services import commands as commands_svc
from app.services import devices as devices_svc

router = APIRouter(tags=["devices"])


# ---------------------------------------------------------------- côté mobile (JWT)
@router.get("/dashboard/summary")
def dashboard_summary(db: Session = Depends(get_db), _: User = Depends(any_user)):
    from app.services.common import iso, utcnow
    devices = devices_svc.list_devices(db)
    alerts = db.scalars(select(Alert).where(Alert.status == "OPEN")).all()
    by_sev = {"INFO": 0, "WARNING": 0, "CRITICAL": 0}
    for a in alerts:
        by_sev[a.severity] += 1
    items = []
    for d in devices:
        st = devices_svc.build_status(db, db.scalar(select(Device).where(Device.device_id == d["device_id"])))
        items.append({"device_id": d["device_id"], "name": d["name"], "connection": st["connection"],
                      "state": st["state"], "control_mode": st["control_mode"]})
    if not items or any(i["connection"]["status"] != "ONLINE" for i in items):
        global_state = "UNKNOWN"
    elif by_sev["CRITICAL"] or any(i["state"] == "FAULT" for i in items):
        global_state = "CRITICAL"
    elif by_sev["WARNING"] or any(i["state"] != "NORMAL" for i in items):
        global_state = "WARNING"
    else:
        global_state = "OK"
    return {"generated_at": iso(utcnow()), "global_state": global_state, "devices": items,
            "open_alerts": len(alerts), "open_alerts_by_severity": by_sev,
            "commands_in_progress": commands_svc.in_progress_count(db)}


@router.get("/devices")
def list_devices(db: Session = Depends(get_db), _: User = Depends(any_user)):
    return {"items": devices_svc.list_devices(db)}


@router.get("/devices/{device_id}")
def get_device(device: Device = Depends(known_device), db: Session = Depends(get_db), _: User = Depends(any_user)):
    return devices_svc.device_to_dict(db, device)


@router.get("/devices/{device_id}/status")
def get_status(device: Device = Depends(known_device), db: Session = Depends(get_db), _: User = Depends(any_user)):
    return devices_svc.build_status(db, device)


# ---------------------------------------------------------------- configuration (mobile JWT OU appareil X-Device-Key)
@router.get("/devices/{device_id}/config")
def get_config(device_id: str, request: Request, db: Session = Depends(get_db)):
    if request.headers.get("X-Device-Key"):
        devices_svc.authenticate_device(db, device_id, request.headers.get("X-Device-Key"))
    else:
        get_current_user(request, db)
        devices_svc.get_device(db, device_id)
    return devices_svc.get_config(db, device_id)


@router.patch("/devices/{device_id}/config")
def patch_config(body: ConfigPatch, device: Device = Depends(known_device), db: Session = Depends(get_db),
                 admin: User = Depends(admin_only)):
    return devices_svc.update_config(db, device.device_id, body, admin.id)


# ---------------------------------------------------------------- côté contrôleur (X-Device-Key)
@router.post("/devices/{device_id}/heartbeat")
def heartbeat(body: HeartbeatIn, device: Device = Depends(device_auth), db: Session = Depends(get_db)):
    return devices_svc.ingest_heartbeat(db, device, body)

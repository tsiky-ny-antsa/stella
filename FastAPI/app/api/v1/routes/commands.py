from fastapi import APIRouter, Depends, Query, Response
from sqlalchemy.orm import Session

from app.api.deps import any_user, device_auth, known_device, operator_or_admin
from app.db.models import Device, User
from app.db.session import get_db
from app.schemas.device import AckIn, CommandCreate
from app.services import commands as cmd_svc

router = APIRouter(tags=["commands"])


# ---------------------------------------------------------------- côté mobile (JWT)
@router.post("/devices/{device_id}/commands", status_code=202)
def create_command(body: CommandCreate, device: Device = Depends(known_device), db: Session = Depends(get_db),
                   user: User = Depends(operator_or_admin)):
    """202 = demande ENREGISTRÉE. Jamais « GE démarré » ni « ATS basculé »."""
    cmd, _created = cmd_svc.create_command(db, user, device, body)
    return cmd


@router.get("/devices/{device_id}/commands")
def list_commands(limit: int = Query(50, ge=1, le=200), before: str | None = None,
                  device: Device = Depends(known_device), db: Session = Depends(get_db),
                  _: User = Depends(any_user)):
    return cmd_svc.list_commands(db, device.device_id, limit, before)


@router.get("/commands/{command_id}")
def get_command(command_id: str, db: Session = Depends(get_db), _: User = Depends(any_user)):
    cmd_svc.expire_due(db)
    return cmd_svc.command_to_dict(cmd_svc.get_command(db, command_id))


# ---------------------------------------------------------------- côté contrôleur (X-Device-Key)
@router.get("/devices/{device_id}/commands/next")
def next_command(device: Device = Depends(device_auth), db: Session = Depends(get_db)):
    cmd = cmd_svc.next_for_device(db, device)
    if cmd is None:
        return Response(status_code=204)
    return cmd


@router.post("/devices/{device_id}/commands/{command_id}/ack")
def ack_command(command_id: str, body: AckIn, device: Device = Depends(device_auth),
                db: Session = Depends(get_db)):
    return cmd_svc.ack(db, device, command_id, body)

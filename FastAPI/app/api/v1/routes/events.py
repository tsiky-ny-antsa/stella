from fastapi import APIRouter, Depends, Query, Response
from sqlalchemy.orm import Session

from app.api.deps import any_user, device_auth, known_device
from app.db.models import Device, User
from app.db.session import get_db
from app.schemas.device import EventIn
from app.services import events as events_svc

router = APIRouter(tags=["events"])


@router.get("/devices/{device_id}/events")
def list_events(limit: int = Query(50, ge=1, le=200), before: str | None = None,
                device: Device = Depends(known_device), db: Session = Depends(get_db), _: User = Depends(any_user)):
    return events_svc.list_events(db, device.device_id, limit, before)


@router.post("/devices/{device_id}/events", status_code=201)
def post_event(body: EventIn, response: Response, device: Device = Depends(device_auth),
               db: Session = Depends(get_db)):
    event, created = events_svc.ingest_event(db, device, body)
    response.status_code = 201 if created else 200   # 200 = déjà reçu (retransmission)
    return event

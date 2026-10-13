from typing import Literal

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.api.deps import any_user, operator_or_admin
from app.db.models import User
from app.db.session import get_db
from app.services import events as events_svc

router = APIRouter(tags=["alerts"])


@router.get("/alerts")
def list_alerts(status: Literal["OPEN", "ACKNOWLEDGED"] | None = None, limit: int = Query(50, ge=1, le=200),
                db: Session = Depends(get_db), _: User = Depends(any_user)):
    return {"items": events_svc.list_alerts(db, status, limit)}


@router.post("/alerts/{alert_id}/ack")
def ack_alert(alert_id: str, db: Session = Depends(get_db), user: User = Depends(operator_or_admin)):
    return events_svc.ack_alert(db, user, alert_id)

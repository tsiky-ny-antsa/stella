from fastapi import APIRouter, Depends, Query
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.deps import admin_only
from app.db.models import AuditLog, User
from app.db.session import get_db
from app.schemas.auth import UserCreate, UserOut, UserPatch
from app.services import audit as audit_svc
from app.services import users as users_svc

router = APIRouter(tags=["users"])


@router.get("/users")
def list_users(db: Session = Depends(get_db), _: User = Depends(admin_only)):
    return {"items": [users_svc.user_to_dict(u) for u in db.scalars(select(User).order_by(User.username)).all()]}


@router.post("/users", response_model=UserOut, status_code=201)
def create_user(body: UserCreate, db: Session = Depends(get_db), admin: User = Depends(admin_only)):
    return users_svc.user_to_dict(users_svc.create_user(db, admin, **body.model_dump()))


@router.patch("/users/{user_id}", response_model=UserOut)
def patch_user(user_id: str, body: UserPatch, db: Session = Depends(get_db), admin: User = Depends(admin_only)):
    return users_svc.user_to_dict(users_svc.patch_user(db, admin, user_id, body.model_dump(exclude_unset=True)))


@router.get("/audit")
def audit_log(limit: int = Query(50, ge=1, le=200), db: Session = Depends(get_db), _: User = Depends(admin_only)):
    rows = db.scalars(select(AuditLog).order_by(AuditLog.created_at.desc()).limit(limit)).all()
    return {"items": [audit_svc.to_dict(a) for a in rows]}

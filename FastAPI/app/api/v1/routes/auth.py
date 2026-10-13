from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.api.deps import any_user
from app.core.security import create_access_token
from app.db.models import User
from app.db.session import get_db
from app.schemas.auth import LoginRequest, LoginResponse, UserOut
from app.services import users as users_svc

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/login", response_model=LoginResponse)
def login(body: LoginRequest, db: Session = Depends(get_db)):
    user = users_svc.authenticate(db, body.username, body.password)
    token, expires_in = create_access_token(user.id, user.role)
    return {"access_token": token, "token_type": "Bearer", "expires_in": expires_in,
            "user": users_svc.user_to_dict(user)}


@router.get("/me", response_model=UserOut)
def me(user: User = Depends(any_user)):
    return users_svc.user_to_dict(user)

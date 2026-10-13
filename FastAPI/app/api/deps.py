from fastapi import Depends, Header, Path, Request
from sqlalchemy.orm import Session

from app.core.errors import ApiError
from app.core.security import decode_access_token
from app.db.models import Device, User
from app.db.session import get_db
from app.services import devices as devices_svc


def get_current_user(request: Request, db: Session = Depends(get_db)) -> User:
    header = request.headers.get("Authorization", "")
    scheme, _, token = header.partition(" ")
    if scheme.lower() != "bearer" or not token:
        raise ApiError(401, "UNAUTHENTICATED", "Authentification requise.")
    payload = decode_access_token(token)
    if not payload:
        raise ApiError(401, "TOKEN_INVALID_OR_EXPIRED", "Session invalide ou expirée.")
    user = db.get(User, payload.get("sub"))
    if not user or not user.is_active:
        raise ApiError(401, "UNAUTHENTICATED", "Compte inconnu ou désactivé.")
    return user


def require_roles(*roles: str):
    """Contrôle des droits CÔTÉ BACKEND (l'interface mobile ne fait que masquer)."""
    def checker(user: User = Depends(get_current_user)) -> User:
        if user.role not in roles:
            raise ApiError(403, "FORBIDDEN", "Droits insuffisants pour cette action.", {"required": list(roles)})
        return user
    return checker


any_user = get_current_user
operator_or_admin = require_roles("ADMIN", "OPERATEUR")
admin_only = require_roles("ADMIN")


def device_auth(device_id: str = Path(...), x_device_key: str | None = Header(default=None),
                db: Session = Depends(get_db)) -> Device:
    return devices_svc.authenticate_device(db, device_id, x_device_key)


def known_device(device_id: str = Path(...), db: Session = Depends(get_db)) -> Device:
    return devices_svc.get_device(db, device_id)

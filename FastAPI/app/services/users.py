from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.errors import ApiError
from app.core.security import hash_password, verify_password
from app.db.models import User
from app.services import audit

# Hash factice : égalise le temps de réponse quand l'utilisateur n'existe pas.
_DUMMY_HASH = hash_password("dummy-password-for-timing")


def user_to_dict(u: User) -> dict:
    return {"id": u.id, "username": u.username, "display_name": u.display_name, "role": u.role,
            "is_active": u.is_active}


def authenticate(db: Session, username: str, password: str) -> User:
    user = db.scalar(select(User).where(User.username == username))
    ok = verify_password(password, user.password_hash if user else _DUMMY_HASH)
    if not user or not ok or not user.is_active:
        raise ApiError(401, "INVALID_CREDENTIALS", "Identifiant ou mot de passe incorrect.")
    return user


def create_user(db: Session, actor: User | None, username: str, password: str, display_name: str, role: str) -> User:
    if db.scalar(select(User).where(User.username == username)):
        raise ApiError(409, "USERNAME_TAKEN", "Cet identifiant existe déjà.")
    user = User(username=username, password_hash=hash_password(password), display_name=display_name, role=role)
    db.add(user)
    db.flush()
    audit.log(db, "USER_CREATED", actor.id if actor else None, details={"user": username, "role": role})
    db.commit()
    return user


def patch_user(db: Session, actor: User, user_id: str, changes: dict) -> User:
    user = db.get(User, user_id)
    if not user:
        raise ApiError(404, "USER_NOT_FOUND", "Utilisateur introuvable.")
    if user.id == actor.id and (changes.get("is_active") is False or
                                (changes.get("role") and changes["role"] != "ADMIN")):
        raise ApiError(409, "CANNOT_DEMOTE_SELF", "Un administrateur ne peut pas se rétrograder ni se désactiver.")
    applied = {}
    if changes.get("password"):
        user.password_hash = hash_password(changes["password"])
        applied["password"] = "changed"
    for k in ("display_name", "role", "is_active"):
        if changes.get(k) is not None:
            setattr(user, k, changes[k])
            applied[k] = changes[k]
    audit.log(db, "USER_UPDATED", actor.id, details={"user": user.username, **applied})
    db.commit()
    return user

"""Routes du simulateur (mock de la partie électronique). Montées UNIQUEMENT en développement."""
from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from app.api.deps import admin_only
from app.core.config import settings
from app.core.errors import ApiError
from app.core.security import generate_device_key, hash_device_key
from app.db.models import Device, DeviceCredential, User
from app.db.session import get_db
from app.services import devices as devices_svc
from app.services import simulator as sim

router = APIRouter(prefix="/simulator", tags=["simulator"])


class WorldIn(BaseModel):
    """Entrées physiques simulées. Champ omis = inchangé ; ups_low_battery=null = signal indisponible."""
    jirama_present: bool | None = None
    ups_low_battery: bool | None = None
    emergency_stop: bool | None = None
    manual_selector: bool | None = None
    ge_fault: bool | None = None
    ge_start_will_succeed: bool | None = None
    ge_start_delay: float | None = Field(default=None, ge=0, le=300)
    ge_unstable: bool | None = None
    ge_stop_fails: bool | None = None
    ats_fails: bool | None = None
    ats_delay: float | None = Field(default=None, ge=0, le=60)


class StepIn(BaseModel):
    seconds: int = Field(default=10, ge=1, le=3600)


class RunIn(BaseModel):
    speed: float = Field(default=1.0, ge=0.1, le=60)


class SimDeviceIn(BaseModel):
    device_id: str = Field(min_length=3, max_length=64, pattern=r"^[A-Za-z0-9_.-]+$")
    name: str = Field(default="Contrôleur simulé", max_length=128)


def _runner(db: Session, device_id: str) -> sim.SimRunner:
    devices_svc.get_device(db, device_id)
    return sim.get_runner(device_id)


@router.get("/scenarios")
def scenarios(_: User = Depends(admin_only)):
    return {"items": [{"name": k, "description": v["description"]} for k, v in sim.SCENARIOS.items()]}


@router.post("/devices", status_code=201)
def create_simulated_device(body: SimDeviceIn, db: Session = Depends(get_db), _: User = Depends(admin_only)):
    """Crée un appareil simulé et retourne sa clé X-Device-Key UNE SEULE FOIS (seul son hash est conservé)."""
    from sqlalchemy import select
    if db.scalar(select(Device).where(Device.device_id == body.device_id)):
        raise ApiError(409, "DEVICE_EXISTS", "device_id déjà utilisé.")
    key = generate_device_key()
    db.add(Device(device_id=body.device_id, name=body.name, is_simulated=True))
    db.flush()
    db.add(DeviceCredential(device_id=body.device_id, key_hash=hash_device_key(key)))
    devices_svc.create_default_config(db, body.device_id)
    db.commit()
    return {"device_id": body.device_id, "device_key": key,
            "warning": "Clé affichée une seule fois ; seul son hash est conservé."}


@router.get("/{device_id}")
def snapshot(device_id: str, db: Session = Depends(get_db), _: User = Depends(admin_only)):
    return _runner(db, device_id).snapshot()


@router.post("/{device_id}/world")
def set_world(device_id: str, body: WorldIn, db: Session = Depends(get_db), _: User = Depends(admin_only)):
    r = _runner(db, device_id)
    r.apply_world(**body.model_dump(exclude_unset=True))
    return r.snapshot()


@router.post("/{device_id}/scenarios/{name}")
def run_scenario(device_id: str, name: str, db: Session = Depends(get_db), _: User = Depends(admin_only)):
    r = _runner(db, device_id)
    if name not in sim.SCENARIOS:
        raise ApiError(404, "SCENARIO_NOT_FOUND", "Scénario inconnu.", {"available": list(sim.SCENARIOS)})
    sc = r.apply_scenario(name)
    return {"applied": name, "description": sc["description"], "snapshot": r.snapshot()}


@router.post("/{device_id}/step")
def step(device_id: str, body: StepIn, db: Session = Depends(get_db), _: User = Depends(admin_only)):
    """Avance le temps simulé de N secondes (instantané) puis synchronise avec le backend."""
    r = _runner(db, device_id)
    r.step(db, body.seconds)
    return r.snapshot()


@router.post("/{device_id}/run")
async def run(device_id: str, body: RunIn, db: Session = Depends(get_db), _: User = Depends(admin_only)):
    r = _runner(db, device_id)
    r.start(body.speed)
    return r.snapshot()


@router.post("/{device_id}/pause")
def pause(device_id: str, db: Session = Depends(get_db), _: User = Depends(admin_only)):
    r = _runner(db, device_id)
    r.pause()
    return r.snapshot()


@router.post("/{device_id}/reset")
def reset(device_id: str, db: Session = Depends(get_db), _: User = Depends(admin_only)):
    r = _runner(db, device_id)
    r.pause()
    r.reset()
    return r.snapshot()


def simulator_enabled() -> bool:
    return settings.SIMULATOR_ENABLED and settings.is_dev

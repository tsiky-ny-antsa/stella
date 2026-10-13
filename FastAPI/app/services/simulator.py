"""Simulateur logiciel : fait « vivre » un VirtualDevice en passant par les MÊMES services que l'API appareil
(heartbeat, événements, polling des commandes, accusés). Développement uniquement.

Temps : le temps simulé du contrôleur est indépendant de l'horloge réelle. Les horodatages stockés en base
(received_at, expires_at des commandes) restent en horloge réelle ; captured_at = horloge réelle au moment
de la simulation.
"""
from __future__ import annotations

import asyncio
import threading
from collections import deque

from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.errors import ApiError
from app.db.session import SessionLocal
from app.schemas.device import AckIn, EventIn, HeartbeatIn
from app.services import commands as commands_svc
from app.services import devices as devices_svc
from app.services import events as events_svc
from app.services.virtual_device import VirtualDevice

MAX_QUEUE = 100  # file bornée en cas de perte réseau (contrat : « file bornée »)

SCENARIOS: dict[str, dict] = {
    "jirama_outage": {"description": "Coupure JIRAMA", "world": {"jirama_present": False}},
    "jirama_restored": {"description": "Retour JIRAMA", "world": {"jirama_present": True}},
    "ups_low_battery": {"description": "Batterie UPS faible", "world": {"ups_low_battery": True}},
    "ups_battery_ok": {"description": "Batterie UPS normale", "world": {"ups_low_battery": False}},
    "ge_start_failure": {"description": "Le GE ne démarre pas", "world": {"ge_start_will_succeed": False}},
    "ge_start_ok": {"description": "Le GE démarre normalement", "world": {"ge_start_will_succeed": True}},
    "ge_unstable": {"description": "Tension GE instable", "world": {"ge_unstable": True}},
    "ge_stable": {"description": "Tension GE stable", "world": {"ge_unstable": False}},
    "ats_stuck": {"description": "ATS bloqué (retour de position absent)", "world": {"ats_fails": True}},
    "ats_ok": {"description": "ATS fonctionnel", "world": {"ats_fails": False}},
    "ge_fault": {"description": "Défaut GE", "world": {"ge_fault": True}},
    "ge_fault_clear": {"description": "Défaut GE disparu", "world": {"ge_fault": False}},
    "emergency_stop": {"description": "Arrêt d'urgence enfoncé", "world": {"emergency_stop": True}},
    "emergency_release": {"description": "Arrêt d'urgence relâché", "world": {"emergency_stop": False}},
    "manual_mode": {"description": "Sélecteur MANUEL activé", "world": {"manual_selector": True}},
    "manual_release": {"description": "Sélecteur MANUEL relâché", "world": {"manual_selector": False}},
    "network_down": {"description": "Perte réseau (le contrôleur continue en local)", "network": False},
    "network_up": {"description": "Reconnexion réseau (file d'événements renvoyée)", "network": True},
    "reset_world": {"description": "Entrées physiques par défaut", "reset": True},
}


class SimRunner:
    def __init__(self, device_id: str):
        self.device_id = device_id
        self.lock = threading.RLock()
        self.network_up = True
        self.queue: deque[dict] = deque(maxlen=MAX_QUEUE)
        self.vd = VirtualDevice(device_id)
        self._last_hb = -1e9
        self._last_poll = -1e9
        self._cfg_version = 1
        self.running = False
        self.speed = 1.0
        self._task: asyncio.Task | None = None
        self.last_errors: deque[str] = deque(maxlen=20)

    # ---------------------------------------------------------------- synchronisation avec le backend
    def _flush_queue(self, db: Session, device) -> None:
        while self.queue:
            item = self.queue[0]
            try:
                if item["kind"] == "event":
                    events_svc.ingest_event(db, device, EventIn(**item["payload"]))
                else:
                    commands_svc.ack(db, device, item["command_id"], AckIn(**item["payload"]))
            except ApiError as e:   # rejet définitif (ex. commande expirée) : on journalise et on abandonne
                self.last_errors.append(f"{e.code}: {e.message}")
            self.queue.popleft()

    def _sync(self, db: Session, force: bool) -> None:
        device = devices_svc.get_device(db, self.device_id)
        self.queue.extend(self.vd.drain_outbox())
        if not self.network_up:
            return
        hb_due = self.vd.t - self._last_hb >= settings.DEVICE_HEARTBEAT_INTERVAL_SECONDS
        poll_due = self.vd.t - self._last_poll >= settings.DEVICE_COMMAND_POLL_SECONDS
        if force or hb_due:
            devices_svc.ingest_heartbeat(db, device, HeartbeatIn(**self.vd.heartbeat_payload()))
            self._last_hb = self.vd.t
        self._flush_queue(db, device)
        if force or poll_due:
            self._last_poll = self.vd.t
            new_cfg = devices_svc.get_config(db, self.device_id)
            if new_cfg["version"] != self._cfg_version:
                self.vd.apply_config(new_cfg)
                self._cfg_version = new_cfg["version"]
            for _ in range(5):
                cmd = commands_svc.next_for_device(db, device)
                if not cmd:
                    break
                self.vd.handle_command(cmd)
                self.queue.extend(self.vd.drain_outbox())
                self._flush_queue(db, device)
            if force:
                devices_svc.ingest_heartbeat(db, device, HeartbeatIn(**self.vd.heartbeat_payload()))
                self._last_hb = self.vd.t

    def step(self, db: Session, seconds: float) -> None:
        with self.lock:
            n = max(1, int(round(seconds)))
            for i in range(n):
                self.vd.tick(1.0)
                self._sync(db, force=False)
            self._sync(db, force=True)

    # ---------------------------------------------------------------- scénarios / monde
    def apply_world(self, **kw) -> dict:
        with self.lock:
            return self.vd.set_world(**kw)

    def apply_scenario(self, name: str) -> dict:
        sc = SCENARIOS[name]
        with self.lock:
            if sc.get("reset"):
                self.vd.set_world(jirama_present=True, ups_low_battery=False, emergency_stop=False,
                                  manual_selector=False, ge_fault=False, ge_start_will_succeed=True,
                                  ge_unstable=False, ge_stop_fails=False, ats_fails=False)
                self.network_up = True
            if "world" in sc:
                self.vd.set_world(**sc["world"])
            if "network" in sc:
                self.network_up = sc["network"]
        return sc

    def reset(self) -> None:
        with self.lock:
            self.vd = VirtualDevice(self.device_id)
            self.queue.clear()
            self.network_up = True
            self._last_hb = self._last_poll = -1e9
            self._cfg_version = 1

    def snapshot(self) -> dict:
        with self.lock:
            return {**self.vd.snapshot(), "network_up": self.network_up, "queued_messages": len(self.queue),
                    "running": self.running, "speed": self.speed, "recent_errors": list(self.last_errors)}

    # ---------------------------------------------------------------- temps réel (tâche de fond)
    async def _loop(self) -> None:
        while self.running:
            def one_tick():
                db = SessionLocal()
                try:
                    self.step(db, 1.0)
                except Exception as e:  # ne jamais tuer la boucle de simulation
                    self.last_errors.append(f"{type(e).__name__}: {e}")
                finally:
                    db.close()
            await asyncio.to_thread(one_tick)
            await asyncio.sleep(max(0.05, 1.0 / self.speed))

    def start(self, speed: float) -> None:
        self.speed = speed
        if not self.running:
            self.running = True
            self._task = asyncio.get_running_loop().create_task(self._loop())

    def pause(self) -> None:
        self.running = False
        if self._task:
            self._task.cancel()
            self._task = None


_registry: dict[str, SimRunner] = {}
_registry_lock = threading.Lock()


def get_runner(device_id: str) -> SimRunner:
    with _registry_lock:
        if device_id not in _registry:
            _registry[device_id] = SimRunner(device_id)
        return _registry[device_id]


def shutdown_all() -> None:
    for r in _registry.values():
        r.pause()


def clear_registry() -> None:
    shutdown_all()
    _registry.clear()

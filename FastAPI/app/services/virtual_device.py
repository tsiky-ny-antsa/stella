"""Contrôleur ESP32 VIRTUEL (mock de la partie électronique).

Module pur : aucune dépendance FastAPI/DB/réseau. Il simule :
  * un « monde physique » minimal (JIRAMA, GE, ATS, UPS, arrêt d'urgence, sélecteur manuel)
    dont on peut forcer les entrées (pannes injectées) ;
  * un contrôleur local qui applique le contrat de Doc/API_FASTAPI_ELECTRONIQUE.md :
    machine à états, sécurités locales prioritaires, revérification de chaque commande,
    accusés ACCEPTED/RUNNING/SUCCEEDED/FAILED/REJECTED envoyés SEULEMENT après
    constat de l'état physique simulé.

AVERTISSEMENT : ce n'est pas le firmware. Il ne valide ni câblage, ni GPIO, ni
temporisations réelles, ni sécurité du système physique (cf. Doc/DECISIONS_ET_LIMITES.md).
"""
from __future__ import annotations

import uuid
from datetime import datetime, timezone
from typing import Callable

DEFAULT_CONFIG = {
    "version": 1,
    "auto_enabled": True,
    "ups_autonomy_seconds": None,      # non mesuré
    "safety_margin_seconds": None,
    "t_attente_override_seconds": 120,  # valeur de maquette
    "jirama_stable_seconds": 180,
    "ge_stable_seconds": 8,
    "start_max_attempts": 3,
    "start_attempt_timeout_seconds": 30,
    "start_retry_pause_seconds": 10,
    "cooldown_seconds": 60,
}

# Constantes internes du mock (non portées par le contrat de configuration)
REMINDER_SECONDS = 60
TRANSFER_TIMEOUT_SECONDS = 10
STOP_TIMEOUT_SECONDS = 30
ENGINE_VOLTAGE_DELAY = 2.0

START_STATES = {"STARTING_GE", "GE_DETECTED"}
GE_ON_STATES = {"GE_RUNNING", "TRANSFER_TO_GE", "SUPPLY_GE", "RETURNING_TO_JIRAMA", "COOLDOWN", "STOPPING_GE"}


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


def _iso(dt: datetime) -> str:
    return dt.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


class VirtualDevice:
    def __init__(self, device_id: str, config: dict | None = None,
                 clock: Callable[[], datetime] = _utc_now, firmware_version: str = "0.1.0-sim"):
        self.device_id = device_id
        self.config = {**DEFAULT_CONFIG, **(config or {})}
        self.clock = clock
        self.firmware_version = firmware_version
        self.t = 0.0           # temps simulé (s)
        self._seq = 0
        self.outbox: list[dict] = []

        # ---- monde physique simulé (modifiable pour injecter des pannes) ----
        self.jirama_present = True
        self.ups_low_battery: bool | None = False   # None = signal indisponible
        self.emergency_stop = False
        self.manual_selector = False
        self.ge_fault = False
        self.ge_start_will_succeed = True
        self.ge_start_delay = 5.0
        self.ge_unstable = False
        self.ge_stop_fails = False
        self.ats_fails = False
        self.ats_delay = 1.0
        self.engine_running = False
        self._engine_since: float | None = None
        self.ats_pos = "JIRAMA"
        self._ats_target = "JIRAMA"
        self._ats_move_since: float | None = None
        self._start_phys_since: float | None = None
        self._stop_phys_since: float | None = None

        # ---- contrôleur local ----
        self.state = "INIT"
        self.mode = "AUTO"
        self.fault_code = "NONE"
        self.fault_message: str | None = None
        self.out_start = False
        self.out_stop = False
        self.out_alarm = False
        self.start_attempt = 0
        self._attempt_started = 0.0
        self._retry_until: float | None = None
        self._wait_started = 0.0
        self._last_reminder = 0.0
        self._jirama_back_since: float | None = None
        self._ge_detect_since = 0.0
        self._transfer_started = 0.0
        self._cooldown_until = 0.0
        self._stop_started = 0.0
        self._prev_ups_low: bool | None = False
        self._ups_low_trigger = False
        self._seen_commands: dict[str, str] = {}
        self.pending: dict[str, dict] = {}   # command_id -> {"type": ...}

        self._event("DEVICE_BOOT", "INFO", "Démarrage du contrôleur simulé", {"simulated": True})
        self.state = "NORMAL"

    # ------------------------------------------------------------------ utilitaires
    @property
    def ge_present(self) -> bool:
        """Tension GE vue par le contrôleur (instable = clignotante)."""
        if not self.engine_running or self._engine_since is None:
            return False
        if self.t - self._engine_since < ENGINE_VOLTAGE_DELAY:
            return False
        if self.ge_unstable:
            return int(self.t // 2) % 2 == 0
        return True

    def _event(self, type_: str, severity: str, message: str, data: dict | None = None) -> None:
        self._seq += 1
        self.outbox.append({"kind": "event", "payload": {
            "event_id": str(uuid.uuid4()), "seq": self._seq, "type": type_, "severity": severity,
            "state": self.state, "message": message, "captured_at": _iso(self.clock()), "data": data or {},
        }})

    def _ack(self, command_id: str, stage: str, error_code: str | None = None, message: str | None = None) -> None:
        self.outbox.append({"kind": "ack", "command_id": command_id, "payload": {
            "stage": stage, "captured_at": _iso(self.clock()), "state": self.state,
            "error_code": error_code, "message": message,
        }})

    def drain_outbox(self) -> list[dict]:
        items, self.outbox = self.outbox, []
        return items

    def apply_config(self, cfg: dict) -> None:
        self.config = {**self.config, **{k: v for k, v in cfg.items() if k in DEFAULT_CONFIG}}

    def set_world(self, **kwargs) -> dict:
        """Modifie les entrées physiques simulées. Retourne les clés appliquées."""
        allowed = {"jirama_present", "ups_low_battery", "emergency_stop", "manual_selector", "ge_fault",
                   "ge_start_will_succeed", "ge_start_delay", "ge_unstable", "ge_stop_fails",
                   "ats_fails", "ats_delay"}
        applied = {}
        for k, v in kwargs.items():
            if k in allowed and v is not None or (k == "ups_low_battery" and k in kwargs):
                setattr(self, k, v)
                applied[k] = v
        return applied

    # ------------------------------------------------------------------ boucle
    def step(self, seconds: float, dt: float = 1.0) -> None:
        remaining = seconds
        while remaining > 1e-9:
            self.tick(min(dt, remaining))
            remaining -= dt

    def tick(self, dt: float = 1.0) -> None:
        self.t += dt
        self._physics()
        self._ups_edge()
        if self._safety():
            self._complete_commands()
            return
        self._sequence()
        self._complete_commands()

    # ------------------------------------------------------------------ physique simulée
    def _physics(self) -> None:
        # Démarrage moteur
        if self.out_start and not self.engine_running and not self.ge_fault:
            if self._start_phys_since is None:
                self._start_phys_since = self.t
            if self.ge_start_will_succeed and self.t - self._start_phys_since >= self.ge_start_delay:
                self.engine_running, self._engine_since = True, self.t
        if not self.out_start:
            self._start_phys_since = None
        # Arrêt moteur
        if self.out_stop and self.engine_running:
            if self._stop_phys_since is None:
                self._stop_phys_since = self.t
            if not self.ge_stop_fails and self.t - self._stop_phys_since >= 2.0:
                self.engine_running, self._engine_since = False, None
        if not self.out_stop:
            self._stop_phys_since = None
        # ATS
        if self._ats_target != self.ats_pos:
            if self._ats_move_since is None:
                self._ats_move_since = self.t
            elif not self.ats_fails and self.t - self._ats_move_since >= self.ats_delay:
                self.ats_pos, self._ats_move_since = self._ats_target, None
        else:
            self._ats_move_since = None

    def _ups_edge(self) -> None:
        if self.ups_low_battery is True and self._prev_ups_low is not True:
            self._event("UPS_LOW_BATTERY", "WARNING", "Batterie UPS faible", {"ups_low_battery": True})
            self._ups_low_trigger = True
        if self.ups_low_battery is not True:
            self._ups_low_trigger = False
        self._prev_ups_low = self.ups_low_battery

    # ------------------------------------------------------------------ sécurités locales (prioritaires)
    def _abort_pending(self, code: str, message: str) -> None:
        for cid in list(self.pending):
            self._ack(cid, "FAILED", code, message)
            del self.pending[cid]

    def _all_outputs_off(self) -> None:
        self.out_start = False
        self._ats_target = self.ats_pos  # on ne commande plus de transfert

    def _safety(self) -> bool:
        """True si une sécurité bloque la séquence automatique/distante."""
        if self.manual_selector:
            if self.state != "MANUAL_LOCKOUT":
                self._all_outputs_off()
                self.out_stop = False
                self.mode = "MANUAL"
                self._abort_pending("MANUAL_MODE_ACTIVE", "Sélecteur manuel actif : commandes distantes interdites.")
                self.state = "MANUAL_LOCKOUT"
                self._event("MODE_CHANGED", "WARNING", "Sélecteur manuel activé", {"mode": "MANUAL"})
            return True
        if self.state == "MANUAL_LOCKOUT":
            self.mode = "AUTO"
            self._event("MODE_CHANGED", "INFO", "Sélecteur manuel relâché, retour en AUTO", {"mode": "AUTO"})
            self._recompute_state()
        if self.emergency_stop:
            if self.fault_code != "EMERGENCY_STOP":
                self._all_outputs_off()
                self.out_stop = False
                self.fault_code, self.fault_message = "EMERGENCY_STOP", "Arrêt d'urgence actif"
                self.out_alarm = True
                self._abort_pending("EMERGENCY_STOP_ACTIVE", "Arrêt d'urgence actif.")
                self.state = "FAULT"
                self._event("EMERGENCY_STOP", "CRITICAL", "Arrêt d'urgence actif", {})
            return True
        if self.ge_fault and self.fault_code != "GE_FAULT" and self.state != "FAULT":
            self._enter_fault("GE_FAULT", "Défaut GE signalé", "FAULT")
            return True
        return self.state == "FAULT"

    def _enter_fault(self, code: str, message: str, event_type: str = "FAULT") -> None:
        self._all_outputs_off()
        self.fault_code, self.fault_message = code, message
        self.out_alarm = True
        self._abort_pending(code, message)
        self.state = "FAULT"
        self._event(event_type, "CRITICAL", message, {"code": code})

    def _recompute_state(self) -> None:
        if self.ats_pos == "GE":
            self.state = "SUPPLY_GE" if self.ge_present else "GE_RUNNING"
        elif self.engine_running:
            self.state = "GE_RUNNING"
        elif self.jirama_present:
            self.state = "NORMAL"
        else:
            self.state = "WAITING"
            self._wait_started = self._last_reminder = self.t

    # ------------------------------------------------------------------ séquence automatique
    def _auto(self) -> bool:
        return self.mode == "AUTO" and bool(self.config["auto_enabled"])

    def _jirama_stable(self) -> bool:
        if not self.jirama_present:
            self._jirama_back_since = None
            return False
        if self._jirama_back_since is None:
            self._jirama_back_since = self.t
        return self.t - self._jirama_back_since >= self.config["jirama_stable_seconds"]

    def _sequence(self) -> None:
        s, cfg = self.state, self.config
        if s == "NORMAL":
            if not self.jirama_present:
                self.state = "JIRAMA_FAILURE"
                self._event("JIRAMA_OFF", "WARNING", "Absence JIRAMA détectée", {"jirama_present": False})
        elif s == "JIRAMA_FAILURE":
            self.state = "WAITING"
            self._wait_started = self._last_reminder = self.t
            self._jirama_back_since = None
            self._event("WAIT_STARTED", "INFO", "Temporisation d'attente démarrée",
                        {"wait_seconds": cfg["t_attente_override_seconds"]})
        elif s == "WAITING":
            if self._jirama_stable():
                self.state = "NORMAL"
                self._event("JIRAMA_ON", "INFO", "JIRAMA revenue et stable", {"jirama_present": True})
            elif self._auto():
                if self._ups_low_trigger or self.t - self._wait_started >= cfg["t_attente_override_seconds"]:
                    self._begin_start()
                elif self.t - self._last_reminder >= REMINDER_SECONDS:
                    self._last_reminder = self.t
                    self._event("WAIT_REMINDER", "WARNING", "Rappel : JIRAMA toujours absente",
                                {"remaining_seconds": self.waiting_remaining()})
        elif s == "STARTING_GE":
            self._starting()
        elif s == "GE_DETECTED":
            if not self.ge_present:
                self._attempt_failed("GE_UNSTABLE")
            elif self.t - self._ge_detect_since >= cfg["ge_stable_seconds"]:
                self.state = "GE_RUNNING"
                self._event("GE_STABLE", "INFO", "Tension GE stable", {})
        elif s == "GE_RUNNING":
            if not self.ge_present:
                self._enter_fault("GE_LOST", "GE perdu après stabilisation")
            elif self._auto():
                if not self.jirama_present:
                    self._begin_transfer("GE")
                elif self._jirama_stable():
                    self._begin_cooldown()
        elif s == "TRANSFER_TO_GE":
            if self.ats_pos == "GE":
                self.state = "SUPPLY_GE"
                self._event("ATS_ON_GE", "INFO", "ATS confirmé sur GE", {})
            elif self.t - self._transfer_started > TRANSFER_TIMEOUT_SECONDS:
                self._enter_fault("ATS_INCONSISTENT", "ATS : position GE non confirmée")
        elif s == "SUPPLY_GE":
            if not self.ge_present:
                self._enter_fault("GE_LOST", "GE perdu en charge")
            elif self._auto() and self._jirama_stable():
                self._begin_transfer("JIRAMA")
        elif s == "RETURNING_TO_JIRAMA":
            if self.ats_pos == "JIRAMA":
                self.state = "COOLDOWN"
                self._cooldown_until = self.t + cfg["cooldown_seconds"]
                self._event("ATS_ON_JIRAMA", "INFO", "ATS confirmé sur JIRAMA", {})
            elif self.t - self._transfer_started > TRANSFER_TIMEOUT_SECONDS:
                self._enter_fault("ATS_INCONSISTENT", "ATS : position JIRAMA non confirmée")
        elif s == "COOLDOWN":
            if self.t >= self._cooldown_until:
                self._begin_stop()
        elif s == "STOPPING_GE":
            if not self.engine_running:
                self.out_stop = False
                self._recompute_state()
            elif self.t - self._stop_started > STOP_TIMEOUT_SECONDS:
                self.out_stop = False
                self._event("STOP_FAILED", "CRITICAL", "Arrêt du GE non confirmé", {})
                self._enter_fault("STOP_FAILED", "Arrêt du GE non confirmé")

    def waiting_remaining(self) -> int | None:
        if self.state != "WAITING":
            return None
        return max(0, int(self.config["t_attente_override_seconds"] - (self.t - self._wait_started)))

    # ---- sous-séquences
    def _begin_start(self) -> None:
        self.start_attempt = 0
        self._next_attempt()

    def _next_attempt(self) -> None:
        self.start_attempt += 1
        self._retry_until = None
        self.out_start = True
        self._attempt_started = self.t
        self.state = "STARTING_GE"
        self._event("START_REQUESTED", "INFO", f"Démarrage GE, tentative {self.start_attempt}",
                    {"attempt": self.start_attempt})

    def _starting(self) -> None:
        cfg = self.config
        if self._retry_until is not None:
            if self.t >= self._retry_until:
                self._next_attempt()
            return
        if self.ge_present:
            self.out_start = False
            self.state = "GE_DETECTED"
            self._ge_detect_since = self.t
            self._event("GE_DETECTED", "INFO", "Tension GE détectée", {})
        elif self.t - self._attempt_started >= cfg["start_attempt_timeout_seconds"]:
            self._attempt_failed("TIMEOUT")

    def _attempt_failed(self, reason: str) -> None:
        self.out_start = False
        self._event("START_FAILED", "WARNING", f"Échec tentative {self.start_attempt} ({reason})",
                    {"attempt": self.start_attempt, "reason": reason})
        if self.start_attempt < self.config["start_max_attempts"]:
            self.state = "STARTING_GE"
            self._retry_until = self.t + self.config["start_retry_pause_seconds"]
        else:
            self._enter_fault("START_FAILED_MAX", "Échec de toutes les tentatives de démarrage GE")

    def _begin_transfer(self, target: str) -> None:
        self._transfer_started = self.t
        self._ats_target = target
        self.state = "TRANSFER_TO_GE" if target == "GE" else "RETURNING_TO_JIRAMA"
        self._event("TRANSFER_REQUESTED", "INFO", f"Transfert demandé vers {target}", {"target": target})

    def _begin_cooldown(self) -> None:
        self.state = "COOLDOWN"
        self._cooldown_until = self.t + self.config["cooldown_seconds"]

    def _begin_stop(self) -> None:
        self.out_stop = True
        self._stop_started = self.t
        self.state = "STOPPING_GE"
        self._event("STOP_REQUESTED", "INFO", "Arrêt GE demandé", {})

    # ------------------------------------------------------------------ commandes distantes
    def handle_command(self, cmd: dict) -> None:
        """Traite une commande reçue par polling : revérification locale puis accusés."""
        cid, ctype, params = cmd["command_id"], cmd["type"], cmd.get("parameters") or {}
        if cid in self._seen_commands or cid in self.pending:      # redelivery : déjà traité
            return
        self._seen_commands[cid] = ctype
        reject = self._check_command(ctype, params)
        if reject:
            self._ack(cid, "REJECTED", *reject)
            return
        self._ack(cid, "ACCEPTED")
        if ctype == "START_GE":
            self._begin_start()
            self._ack(cid, "RUNNING")
            self.pending[cid] = {"type": ctype}
        elif ctype == "STOP_GE":
            self._begin_stop()
            self._ack(cid, "RUNNING")
            self.pending[cid] = {"type": ctype}
        elif ctype == "TRANSFER_TO_GE":
            self._begin_transfer("GE")
            self._ack(cid, "RUNNING")
            self.pending[cid] = {"type": ctype}
        elif ctype == "TRANSFER_TO_JIRAMA":
            self._begin_transfer("JIRAMA")
            self._ack(cid, "RUNNING")
            self.pending[cid] = {"type": ctype}
        elif ctype == "RESET_FAULT":
            self.fault_code, self.fault_message, self.out_alarm = "NONE", None, False
            self._event("FAULT_RESET", "INFO", "Défaut réinitialisé", {})
            self._recompute_state()
            self._ack(cid, "SUCCEEDED")
        elif ctype == "SET_MODE":
            self.mode = params["mode"]
            self._event("MODE_CHANGED", "INFO", f"Mode {self.mode}", {"mode": self.mode})
            self._ack(cid, "SUCCEEDED")

    def _check_command(self, ctype: str, params: dict) -> tuple[str, str] | None:
        """Retourne (error_code, message) si la commande doit être REFUSÉE localement."""
        if self.manual_selector:
            return "MANUAL_MODE_ACTIVE", "Commande distante refusée en mode manuel."
        if self.emergency_stop:
            return "EMERGENCY_STOP_ACTIVE", "Arrêt d'urgence actif."
        if ctype == "RESET_FAULT":
            if self.state != "FAULT":
                return "NO_FAULT", "Aucun défaut à réinitialiser."
            if self.ge_fault:
                return "FAULT_STILL_ACTIVE", "Le défaut GE est toujours présent."
            return None
        if self.state == "FAULT":
            return "FAULT_ACTIVE", f"Défaut actif ({self.fault_code}) : réinitialisation requise."
        if ctype == "SET_MODE":
            if params.get("mode") not in ("AUTO", "REMOTE"):
                return "INVALID_MODE", "Mode non autorisé à distance."
            return None
        if ctype == "START_GE":
            if self.state in START_STATES:
                return "ALREADY_STARTING", "Démarrage déjà en cours."
            if self.engine_running or self.state in GE_ON_STATES:
                return "ALREADY_RUNNING", "GE déjà en marche."
        elif ctype == "STOP_GE":
            if self.ats_pos == "GE" or self.state in {"TRANSFER_TO_GE", "SUPPLY_GE", "RETURNING_TO_JIRAMA"}:
                return "LOAD_ON_GE", "Arrêt refusé : la charge dépend du GE."
            if not self.engine_running and self.state not in START_STATES:
                return "GE_NOT_RUNNING", "GE à l'arrêt."
        elif ctype == "TRANSFER_TO_GE":
            if self.state != "GE_RUNNING" or not self.ge_present:
                return "GE_NOT_STABLE", "Transfert refusé : GE non détecté/stable."
            if self.ats_pos == "GE":
                return "ALREADY_ON_GE", "ATS déjà sur GE."
        elif ctype == "TRANSFER_TO_JIRAMA":
            if self.ats_pos != "GE":
                return "ALREADY_ON_JIRAMA", "ATS déjà sur JIRAMA."
            if not self.jirama_present:
                return "JIRAMA_ABSENT", "Retour refusé : JIRAMA absente."
        return None

    def _complete_commands(self) -> None:
        """Envoie SUCCEEDED/FAILED uniquement après constat de l'état physique simulé."""
        for cid, info in list(self.pending.items()):
            t = info["type"]
            done = False
            if t == "START_GE":
                done = self.state in {"GE_RUNNING", "TRANSFER_TO_GE", "SUPPLY_GE"} and self.ge_present
            elif t == "STOP_GE":
                done = not self.engine_running and self.state != "STOPPING_GE"
            elif t == "TRANSFER_TO_GE":
                done = self.ats_pos == "GE" and self.state == "SUPPLY_GE"
            elif t == "TRANSFER_TO_JIRAMA":
                done = self.ats_pos == "JIRAMA" and self.state in {"COOLDOWN", "STOPPING_GE", "NORMAL"}
            if done:
                self._ack(cid, "SUCCEEDED")
                del self.pending[cid]

    # ------------------------------------------------------------------ vues
    def heartbeat_payload(self) -> dict:
        return {
            "captured_at": _iso(self.clock()),
            "uptime_ms": int(self.t * 1000),
            "firmware_version": self.firmware_version,
            "state": self.state,
            "control_mode": self.mode,
            "inputs": {
                "jirama_present": self.jirama_present,
                "ge_present": self.ge_present,
                "ups_low_battery": self.ups_low_battery,
                "ats_on_jirama": self.ats_pos == "JIRAMA",
                "ats_on_ge": self.ats_pos == "GE",
                "ge_fault": self.ge_fault,
                "emergency_stop": self.emergency_stop,
                "manual_selector": self.manual_selector,
            },
            "outputs": {
                "start_ge_command_active": self.out_start,
                "stop_ge_command_active": self.out_stop,
                "transfer_to_ge_command_active": self._ats_target == "GE" and self.ats_pos != "GE",
                "transfer_to_jirama_command_active": self._ats_target == "JIRAMA" and self.ats_pos != "JIRAMA",
                "alarm_active": self.out_alarm,
            },
            "ge": {
                "detected": self.ge_present,
                "stable": self.state in {"GE_RUNNING", "TRANSFER_TO_GE", "SUPPLY_GE", "RETURNING_TO_JIRAMA"} and self.ge_present,
                "running": self.engine_running,
                "start_attempt": self.start_attempt,
            },
            "fault": {"code": self.fault_code, "message": self.fault_message},
            # Extension proposée (hors contrat initial) : temps d'attente restant, pour l'UI mobile.
            "waiting": {"remaining_seconds": self.waiting_remaining()},
        }

    def snapshot(self) -> dict:
        hb = self.heartbeat_payload()
        hb["sim"] = {"t": round(self.t, 1), "world": {
            "ge_start_will_succeed": self.ge_start_will_succeed, "ge_start_delay": self.ge_start_delay,
            "ge_unstable": self.ge_unstable, "ge_stop_fails": self.ge_stop_fails,
            "ats_fails": self.ats_fails, "ats_delay": self.ats_delay,
        }}
        return hb

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

from app.schemas.common import AckStage, CommandType, Severity

State = Literal["INIT", "NORMAL", "JIRAMA_FAILURE", "WAITING", "STARTING_GE", "GE_DETECTED", "GE_RUNNING",
                "TRANSFER_TO_GE", "SUPPLY_GE", "RETURNING_TO_JIRAMA", "COOLDOWN", "STOPPING_GE", "FAULT",
                "MANUAL_LOCKOUT"]
Mode = Literal["MANUAL", "AUTO", "REMOTE"]


class _Tolerant(BaseModel):
    model_config = ConfigDict(extra="ignore")


class HeartbeatInputs(_Tolerant):
    # None = signal indisponible / inconnu (JAMAIS converti en False).
    jirama_present: bool | None = None
    ge_present: bool | None = None
    ups_low_battery: bool | None = None
    ats_on_jirama: bool | None = None
    ats_on_ge: bool | None = None
    ge_fault: bool | None = None
    emergency_stop: bool | None = None
    manual_selector: bool | None = None


class HeartbeatOutputs(_Tolerant):
    start_ge_command_active: bool | None = None
    stop_ge_command_active: bool | None = None
    transfer_to_ge_command_active: bool | None = None
    transfer_to_jirama_command_active: bool | None = None
    alarm_active: bool | None = None


class HeartbeatGe(_Tolerant):
    detected: bool | None = None
    stable: bool | None = None
    running: bool | None = None
    start_attempt: int | None = None


class HeartbeatFault(_Tolerant):
    code: str = "NONE"
    message: str | None = None


class HeartbeatWaiting(_Tolerant):
    remaining_seconds: int | None = None


class HeartbeatIn(_Tolerant):
    captured_at: datetime
    uptime_ms: int | None = Field(default=None, ge=0)
    firmware_version: str | None = Field(default=None, max_length=32)
    state: State
    control_mode: Mode
    inputs: HeartbeatInputs = HeartbeatInputs()
    outputs: HeartbeatOutputs = HeartbeatOutputs()
    ge: HeartbeatGe = HeartbeatGe()
    fault: HeartbeatFault = HeartbeatFault()
    waiting: HeartbeatWaiting | None = None  # extension proposée (hors contrat initial)


class EventIn(_Tolerant):
    event_id: str = Field(min_length=8, max_length=64)
    seq: int | None = Field(default=None, ge=0)
    type: str = Field(min_length=1, max_length=48)
    severity: Severity
    state: str | None = Field(default=None, max_length=32)
    message: str | None = Field(default=None, max_length=1000)
    captured_at: datetime
    data: dict = Field(default_factory=dict)


class AckIn(_Tolerant):
    stage: AckStage
    captured_at: datetime
    state: str | None = Field(default=None, max_length=32)
    error_code: str | None = Field(default=None, max_length=64)
    message: str | None = Field(default=None, max_length=1000)


class CommandCreate(BaseModel):
    type: CommandType
    request_id: str = Field(min_length=8, max_length=64)
    reason: str | None = Field(default=None, max_length=255)
    parameters: dict = Field(default_factory=dict)


class ConfigPatch(BaseModel):
    model_config = ConfigDict(extra="forbid")
    auto_enabled: bool | None = None
    ups_autonomy_seconds: int | None = Field(default=None, ge=0, le=86400)
    safety_margin_seconds: int | None = Field(default=None, ge=0, le=86400)
    t_attente_override_seconds: int | None = Field(default=None, ge=5, le=86400)
    jirama_stable_seconds: int | None = Field(default=None, ge=0, le=3600)
    ge_stable_seconds: int | None = Field(default=None, ge=1, le=600)
    start_max_attempts: int | None = Field(default=None, ge=1, le=10)
    start_attempt_timeout_seconds: int | None = Field(default=None, ge=5, le=300)
    start_retry_pause_seconds: int | None = Field(default=None, ge=0, le=600)
    cooldown_seconds: int | None = Field(default=None, ge=0, le=3600)



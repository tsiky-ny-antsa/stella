"""Tests unitaires du contrôleur ESP32 virtuel (sans API ni base)."""
from app.services.virtual_device import VirtualDevice


def types(d):
    return [i["payload"]["type"] for i in d.drain_outbox() if i["kind"] == "event"]


def fast(**cfg):
    return VirtualDevice("t", {"t_attente_override_seconds": 20, "jirama_stable_seconds": 10, "cooldown_seconds": 5,
                               "ge_stable_seconds": 4, "start_attempt_timeout_seconds": 15,
                               "start_retry_pause_seconds": 3, **cfg})


def test_boot_event_and_normal_state():
    d = fast()
    assert types(d) == ["DEVICE_BOOT"] and d.state == "NORMAL"
    hb = d.heartbeat_payload()
    assert hb["inputs"]["jirama_present"] is True and hb["inputs"]["ats_on_jirama"] is True


def test_full_outage_cycle():
    d = fast()
    d.drain_outbox()
    d.jirama_present = False
    d.step(60)
    assert d.state == "SUPPLY_GE" and d.ats_pos == "GE"
    evs = types(d)
    for expected in ("JIRAMA_OFF", "WAIT_STARTED", "START_REQUESTED", "GE_DETECTED", "GE_STABLE",
                     "TRANSFER_REQUESTED", "ATS_ON_GE"):
        assert expected in evs
    # GE détecté puis STABLE avant transfert
    assert evs.index("GE_STABLE") < evs.index("TRANSFER_REQUESTED") < evs.index("ATS_ON_GE")
    d.jirama_present = True
    d.step(60)
    assert d.state == "NORMAL" and d.ats_pos == "JIRAMA" and not d.engine_running
    evs = types(d)
    assert evs.index("ATS_ON_JIRAMA") < evs.index("STOP_REQUESTED")        # arrêt APRÈS retour sur JIRAMA


def test_wait_reminder_and_countdown():
    d = VirtualDevice("t", {"t_attente_override_seconds": 200})
    d.drain_outbox()
    d.jirama_present = False
    d.step(70)
    assert "WAIT_REMINDER" in types(d)
    assert 0 < d.heartbeat_payload()["waiting"]["remaining_seconds"] < 200


def test_jirama_back_before_timer_cancels_start():
    d = fast(t_attente_override_seconds=100)
    d.drain_outbox()
    d.jirama_present = False
    d.step(5)
    d.jirama_present = True
    d.step(15)
    assert d.state == "NORMAL" and not d.engine_running and "START_REQUESTED" not in types(d)


def test_low_battery_triggers_start_before_timer():
    d = fast(t_attente_override_seconds=3000)
    d.drain_outbox()
    d.jirama_present = False
    d.step(5)
    d.ups_low_battery = True
    d.step(3)
    evs = types(d)
    assert "UPS_LOW_BATTERY" in evs and d.state in {"STARTING_GE", "GE_DETECTED"}


def test_three_failed_starts_lead_to_fault_and_reset():
    d = fast()
    d.ge_start_will_succeed = False
    d.jirama_present = False
    d.step(200)
    evs = types(d)
    assert evs.count("START_FAILED") == 3 and d.state == "FAULT" and d.fault_code == "START_FAILED_MAX"
    assert d.out_alarm is True and d.ats_pos == "JIRAMA"       # jamais de transfert sans GE
    d.handle_command({"command_id": "c1", "type": "RESET_FAULT"})
    out = d.drain_outbox()
    assert [i["payload"]["stage"] for i in out if i["kind"] == "ack"] == ["ACCEPTED", "SUCCEEDED"]
    assert d.fault_code == "NONE"


def test_unstable_ge_never_transfers():
    d = fast()
    d.ge_unstable = True
    d.jirama_present = False
    d.step(150)
    assert d.ats_pos == "JIRAMA" and "ATS_ON_GE" not in types(d)


def test_ats_stuck_leads_to_fault():
    d = fast()
    d.ats_fails = True
    d.jirama_present = False
    d.step(80)
    assert d.state == "FAULT" and d.fault_code == "ATS_INCONSISTENT"


def test_ats_not_returning_is_fault():
    d = fast()
    d.jirama_present = False
    d.step(60)
    assert d.state == "SUPPLY_GE"
    d.ats_fails = True
    d.jirama_present = True
    d.step(60)
    assert d.state == "FAULT" and d.fault_code == "ATS_INCONSISTENT"


def test_emergency_stop_has_priority_and_rejects_commands():
    d = fast()
    d.drain_outbox()
    d.emergency_stop = True
    d.tick()
    assert d.state == "FAULT" and d.fault_code == "EMERGENCY_STOP"
    assert "EMERGENCY_STOP" in types(d)
    d.handle_command({"command_id": "c1", "type": "START_GE"})
    ack = [i for i in d.drain_outbox() if i["kind"] == "ack"][0]["payload"]
    assert ack["stage"] == "REJECTED" and ack["error_code"] == "EMERGENCY_STOP_ACTIVE"
    d.handle_command({"command_id": "c2", "type": "RESET_FAULT"})
    assert d.drain_outbox()[0]["payload"]["error_code"] == "EMERGENCY_STOP_ACTIVE"


def test_manual_selector_locks_out_remote_and_auto():
    d = fast()
    d.drain_outbox()
    d.jirama_present = False
    d.manual_selector = True
    d.step(100)
    assert d.state == "MANUAL_LOCKOUT" and d.mode == "MANUAL" and not d.engine_running   # pas d'automatisme
    d.handle_command({"command_id": "c1", "type": "START_GE"})
    ack = [i for i in d.drain_outbox() if i["kind"] == "ack"][0]["payload"]
    assert ack["error_code"] == "MANUAL_MODE_ACTIVE" and ack["stage"] == "REJECTED"
    d.manual_selector = False
    d.step(3)
    assert d.mode == "AUTO" and d.state != "MANUAL_LOCKOUT"


def test_ge_fault_goes_to_fault():
    d = fast()
    d.ge_fault = True
    d.tick()
    assert d.state == "FAULT" and d.fault_code == "GE_FAULT"
    d.handle_command({"command_id": "c1", "type": "RESET_FAULT"})
    assert d.drain_outbox()[-1]["payload"]["error_code"] == "FAULT_STILL_ACTIVE"


def test_start_command_succeeds_only_after_physical_confirmation():
    d = fast()
    d.drain_outbox()
    d.handle_command({"command_id": "c1", "type": "START_GE"})
    stages = [i["payload"]["stage"] for i in d.drain_outbox() if i["kind"] == "ack"]
    assert stages == ["ACCEPTED", "RUNNING"]                      # pas encore SUCCEEDED
    for _ in range(30):
        d.tick()
        stages += [i["payload"]["stage"] for i in d.drain_outbox() if i["kind"] == "ack"]
        if "SUCCEEDED" in stages:
            break
    assert stages[-1] == "SUCCEEDED" and d.state == "GE_RUNNING" and d.ge_present


def test_failed_start_command_reports_failed():
    d = fast(start_max_attempts=1)
    d.ge_start_will_succeed = False
    d.drain_outbox()
    d.handle_command({"command_id": "c1", "type": "START_GE"})
    d.step(40)
    acks = [i["payload"] for i in d.drain_outbox() if i["kind"] == "ack"]
    assert acks[-1]["stage"] == "FAILED" and acks[-1]["error_code"] == "START_FAILED_MAX"


def test_stop_refused_while_load_on_ge_and_transfer_refused_unstable():
    d = fast()
    d.drain_outbox()
    d.handle_command({"command_id": "c0", "type": "TRANSFER_TO_GE"})          # GE pas démarré
    assert d.drain_outbox()[0]["payload"]["error_code"] == "GE_NOT_STABLE"
    d.jirama_present = False
    d.step(60)
    assert d.state == "SUPPLY_GE"
    d.drain_outbox()
    d.handle_command({"command_id": "c1", "type": "STOP_GE"})
    assert d.drain_outbox()[0]["payload"]["error_code"] == "LOAD_ON_GE"
    d.handle_command({"command_id": "c2", "type": "TRANSFER_TO_JIRAMA"})
    assert d.drain_outbox()[0]["payload"]["error_code"] == "JIRAMA_ABSENT"


def test_redelivered_command_is_not_executed_twice():
    d = fast()
    d.drain_outbox()
    cmd = {"command_id": "c1", "type": "START_GE"}
    d.handle_command(cmd)
    first = d.drain_outbox()
    assert [i["payload"]["stage"] for i in first if i["kind"] == "ack"] == ["ACCEPTED", "RUNNING"]
    d.handle_command(cmd)                    # bail expiré côté backend : re-livraison
    assert d.drain_outbox() == [] and d.start_attempt == 1


def test_set_mode_remote_disables_automation():
    d = fast()
    d.drain_outbox()
    d.handle_command({"command_id": "c1", "type": "SET_MODE", "parameters": {"mode": "REMOTE"}})
    assert d.mode == "REMOTE"
    d.jirama_present = False
    d.step(100)
    assert d.state == "WAITING" and not d.engine_running          # plus de démarrage automatique

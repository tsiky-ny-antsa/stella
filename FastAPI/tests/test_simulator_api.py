"""Simulateur de bout en bout : VirtualDevice <-> services <-> API (mêmes chemins que le vrai ESP32)."""
import pytest

from tests.conftest import API, DEVICE_ID

SIM = f"{API}/simulator/{DEVICE_ID}"
FAST_CFG = {"t_attente_override_seconds": 20, "jirama_stable_seconds": 10, "cooldown_seconds": 5,
            "ge_stable_seconds": 4, "start_attempt_timeout_seconds": 15, "start_retry_pause_seconds": 3}


@pytest.fixture()
def sim(client, admin):
    """Configure des temporisations courtes via l'API admin, puis démarre le contrôleur simulé."""
    assert client.patch(f"{API}/devices/{DEVICE_ID}/config", json=FAST_CFG, headers=admin).status_code == 200

    class S:
        def scenario(self, name):
            r = client.post(f"{SIM}/scenarios/{name}", headers=admin)
            assert r.status_code == 200, r.text
            return r.json()

        def step(self, seconds):
            r = client.post(f"{SIM}/step", json={"seconds": seconds}, headers=admin)
            assert r.status_code == 200, r.text
            return r.json()

        def status(self):
            return client.get(f"{API}/devices/{DEVICE_ID}/status", headers=admin).json()

        def events(self, limit=200):
            return [e["type"] for e in reversed(
                client.get(f"{API}/devices/{DEVICE_ID}/events?limit={limit}", headers=admin).json()["items"])]

    s = S()
    s.step(1)            # premier heartbeat + config prise en compte
    return s


def test_simulator_routes_require_admin(client, operateur, lecture):
    assert client.get(SIM, headers=operateur).status_code == 403
    assert client.post(f"{SIM}/step", json={"seconds": 1}, headers=lecture).status_code == 403
    assert client.get(SIM).status_code == 401


def test_unknown_scenario_and_bounds(client, admin):
    assert client.post(f"{SIM}/scenarios/nope", headers=admin).status_code == 404
    assert client.post(f"{SIM}/step", json={"seconds": 99999}, headers=admin).status_code == 422
    assert client.get(f"{API}/simulator/scenarios", headers=admin).json()["items"]


def test_normal_state_visible_via_mobile_api(sim):
    st = sim.status()
    assert st["connection"]["status"] == "ONLINE" and st["state"] == "NORMAL"
    assert st["inputs"]["jirama_present"] is True and st["inputs"]["ats_position"] == "JIRAMA"
    assert "DEVICE_BOOT" in sim.events()


def test_jirama_outage_waiting_countdown_then_start(sim):
    sim.scenario("jirama_outage")
    sim.step(5)
    st = sim.status()
    assert st["state"] == "WAITING" and 0 < st["waiting"]["remaining_seconds"] <= 20
    sim.step(40)
    assert sim.status()["state"] == "SUPPLY_GE"
    assert sim.status()["inputs"]["ats_position"] == "GE"


def test_full_cycle_with_return_cooldown_and_stop(sim):
    sim.scenario("jirama_outage")
    sim.step(60)
    sim.scenario("jirama_restored")
    sim.step(60)
    st = sim.status()
    assert st["state"] == "NORMAL" and st["inputs"]["ats_position"] == "JIRAMA" and st["ge"]["running"] is False
    ev = sim.events()
    for t in ("JIRAMA_OFF", "WAIT_STARTED", "START_REQUESTED", "GE_DETECTED", "GE_STABLE", "ATS_ON_GE",
              "ATS_ON_JIRAMA", "STOP_REQUESTED"):
        assert t in ev, t


def test_low_battery_alert_and_early_start(client, admin, sim):
    client.patch(f"{API}/devices/{DEVICE_ID}/config", json={"t_attente_override_seconds": 3000}, headers=admin)
    sim.step(5)
    sim.scenario("jirama_outage")
    sim.step(6)
    assert sim.status()["state"] == "WAITING"
    sim.scenario("ups_low_battery")
    sim.step(30)
    assert sim.status()["state"] in {"SUPPLY_GE", "TRANSFER_TO_GE", "GE_RUNNING", "GE_DETECTED"}
    kinds = [a["kind"] for a in client.get(f"{API}/alerts?status=OPEN", headers=admin).json()["items"]]
    assert "UPS_LOW_BATTERY" in kinds and "JIRAMA_OFF" in kinds


def test_reminder_event_during_long_wait(client, admin, sim):
    client.patch(f"{API}/devices/{DEVICE_ID}/config", json={"t_attente_override_seconds": 600}, headers=admin)
    sim.step(5)
    sim.scenario("jirama_outage")
    sim.step(80)
    assert "WAIT_REMINDER" in sim.events()


def test_three_failed_starts_raise_critical_alert(client, admin, sim):
    sim.scenario("ge_start_failure")
    sim.scenario("jirama_outage")
    sim.step(150)
    st = sim.status()
    assert st["state"] == "FAULT" and st["fault"]["code"] == "START_FAILED_MAX"
    assert sim.events().count("START_FAILED") == 3
    assert st["inputs"]["ats_position"] == "JIRAMA"
    sev = {a["kind"]: a["severity"] for a in client.get(f"{API}/alerts", headers=admin).json()["items"]}
    assert sev["START_FAILED"] == "WARNING" and sev["FAULT"] == "CRITICAL"


def test_unstable_ge_blocks_transfer(sim):
    sim.scenario("ge_unstable")
    sim.scenario("jirama_outage")
    sim.step(120)
    assert sim.status()["inputs"]["ats_position"] != "GE" and "ATS_ON_GE" not in sim.events()


def test_ats_return_missing_is_fault(sim):
    sim.scenario("jirama_outage")
    sim.step(60)
    sim.scenario("ats_stuck")
    sim.scenario("jirama_restored")
    sim.step(60)
    st = sim.status()
    assert st["state"] == "FAULT" and st["fault"]["code"] == "ATS_INCONSISTENT"


def test_emergency_stop_manual_and_ge_fault(client, admin, operateur, sim):
    sim.scenario("emergency_stop")
    sim.step(2)
    st = sim.status()
    assert st["state"] == "FAULT" and st["inputs"]["emergency_stop"] is True
    r = client.post(f"{API}/devices/{DEVICE_ID}/commands", headers=operateur,
                    json={"type": "START_GE", "request_id": "req-urgence-1"})
    assert r.status_code == 409 and r.json()["error"]["details"]["reason"] == "EMERGENCY_STOP_ACTIVE"
    sim.scenario("reset_world")
    sim.step(1)
    sim.scenario("manual_mode")
    sim.step(2)
    st = sim.status()
    assert st["state"] == "MANUAL_LOCKOUT" and st["control_mode"] == "MANUAL"
    sim.scenario("reset_world")
    sim.scenario("ge_fault")
    sim.step(2)
    assert sim.status()["fault"]["code"] in {"GE_FAULT", "EMERGENCY_STOP", "NONE"}


def test_operator_start_command_executed_and_confirmed(client, operateur, sim):
    client.post(f"{API}/devices/{DEVICE_ID}/commands", headers=operateur,
                json={"type": "SET_MODE", "request_id": "req-mode-0002", "parameters": {"mode": "REMOTE"}})
    sim.step(2)
    r = client.post(f"{API}/devices/{DEVICE_ID}/commands", headers=operateur,
                    json={"type": "START_GE", "request_id": "req-start-0001", "reason": "Essai"})
    assert r.status_code == 202 and r.json()["status"] == "QUEUED"
    cid = r.json()["command_id"]
    sim.step(1)   # le contrôleur récupère par polling et accepte
    mid = client.get(f"{API}/commands/{cid}", headers=operateur).json()
    assert mid["status"] == "RUNNING"          # accepté et en cours, PAS encore réussi
    assert sim.status()["ge"]["running"] is False or sim.status()["state"] != "GE_RUNNING"
    sim.step(30)
    done = client.get(f"{API}/commands/{cid}", headers=operateur).json()
    assert done["status"] == "SUCCEEDED"
    assert [h["stage"] for h in done["result"]["history"]] == ["ACCEPTED", "RUNNING", "SUCCEEDED"]
    st = sim.status()
    assert st["state"] == "GE_RUNNING" and st["ge"]["stable"] is True


def test_operator_transfer_and_return_via_commands(client, operateur, sim):
    def cmd(t, rid):
        r = client.post(f"{API}/devices/{DEVICE_ID}/commands", headers=operateur,
                        json={"type": t, "request_id": rid})
        assert r.status_code == 202, r.text
        return r.json()["command_id"]

    def status(cid):
        return client.get(f"{API}/commands/{cid}", headers=operateur).json()["status"]

    client.post(f"{API}/devices/{DEVICE_ID}/commands", headers=operateur,
                json={"type": "SET_MODE", "request_id": "req-mode-0001", "parameters": {"mode": "REMOTE"}})
    sim.step(2)
    a = cmd("START_GE", "req-op-start-1")
    sim.step(30)
    assert status(a) == "SUCCEEDED"
    b = cmd("TRANSFER_TO_GE", "req-op-trans-1")
    sim.step(1)   # livraison par polling
    sim.step(5)
    assert status(b) == "SUCCEEDED" and sim.status()["inputs"]["ats_position"] == "GE"
    stop = cmd("STOP_GE", "req-op-stop-01")           # la charge dépend du GE : refus local
    sim.step(1)
    sim.step(2)
    done = client.get(f"{API}/commands/{stop}", headers=operateur).json()
    assert done["status"] == "REJECTED" and done["result"]["error_code"] == "LOAD_ON_GE"


def test_controller_rejects_when_manual_pressed_after_queueing(client, operateur, sim):
    r = client.post(f"{API}/devices/{DEVICE_ID}/commands", headers=operateur,
                    json={"type": "START_GE", "request_id": "req-race-0001"})
    cid = r.json()["command_id"]
    sim.scenario("manual_mode")                       # le sélecteur passe en MANUEL avant le polling
    sim.step(1)
    done = client.get(f"{API}/commands/{cid}", headers=operateur).json()
    assert done["status"] == "REJECTED" and done["result"]["error_code"] == "MANUAL_MODE_ACTIVE"
    assert sim.status()["ge"]["running"] is False


def test_network_loss_keeps_local_automation_and_replays_events(client, admin, sim):
    before = len(sim.events())
    sim.scenario("network_down")
    sim.scenario("jirama_outage")
    snap = sim.step(60)
    assert snap["network_up"] is False and snap["queued_messages"] > 0
    assert snap["state"] in {"SUPPLY_GE", "TRANSFER_TO_GE", "GE_RUNNING"}     # automatisme local sans réseau
    assert len(sim.events()) == before                                       # rien n'a pu être transmis
    sim.scenario("network_up")
    snap = sim.step(1)
    assert snap["queued_messages"] == 0
    ev = sim.events()
    assert "JIRAMA_OFF" in ev and "ATS_ON_GE" in ev                          # rejoué dans l'ordre à la reconnexion
    assert ev.index("JIRAMA_OFF") < ev.index("ATS_ON_GE")


def test_config_change_is_picked_up_by_simulated_controller(client, admin, sim):
    client.patch(f"{API}/devices/{DEVICE_ID}/config", json={"t_attente_override_seconds": 5000}, headers=admin)
    sim.step(5)
    sim.scenario("jirama_outage")
    sim.step(60)
    assert sim.status()["state"] == "WAITING"        # nouvelle temporisation appliquée


def test_reset_and_world_endpoint(client, admin, sim):
    r = client.post(f"{SIM}/world", json={"jirama_present": False, "ge_start_delay": 2}, headers=admin)
    assert r.json()["inputs"]["jirama_present"] is False
    assert client.post(f"{SIM}/world", json={"ge_start_delay": -5}, headers=admin).status_code == 422
    r = client.post(f"{SIM}/reset", headers=admin)
    assert r.json()["inputs"]["jirama_present"] is True and r.json()["state"] == "NORMAL"


def test_create_simulated_device_returns_key_once(client, admin):
    r = client.post(f"{API}/simulator/devices", json={"device_id": "sim-02", "name": "Second"}, headers=admin)
    assert r.status_code == 201 and len(r.json()["device_key"]) >= 32
    key = r.json()["device_key"]
    hb = {"captured_at": "2026-10-12T16:20:00Z", "state": "NORMAL", "control_mode": "AUTO"}
    assert client.post(f"{API}/devices/sim-02/heartbeat", json=hb, headers={"X-Device-Key": key}).status_code == 200
    assert client.post(f"{API}/simulator/devices", json={"device_id": "sim-02"}, headers=admin).status_code == 409
    assert client.get(f"{API}/devices/sim-02", headers=admin).json()["is_simulated"] is True


def test_auto_mode_stops_ge_started_by_operator_when_jirama_present(client, operateur, sim):
    """Comportement documenté : en AUTO, un GE démarré à la main alors que la JIRAMA est présente
    est arrêté automatiquement après stabilité JIRAMA + refroidissement (utiliser REMOTE pour un essai)."""
    client.post(f"{API}/devices/{DEVICE_ID}/commands", headers=operateur,
                json={"type": "START_GE", "request_id": "req-auto-start1"})
    sim.step(60)
    st = sim.status()
    assert st["state"] == "NORMAL" and st["ge"]["running"] is False
    assert "STOP_REQUESTED" in sim.events()

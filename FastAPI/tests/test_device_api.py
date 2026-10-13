from datetime import timedelta
from uuid import uuid4

from sqlalchemy import select

from app.db.models import Alert, DeviceStatus
from app.services import devices as devices_svc
from app.services.common import utcnow
from tests.conftest import API, DEVICE_ID, heartbeat_body

HB = f"{API}/devices/{DEVICE_ID}/heartbeat"
EV = f"{API}/devices/{DEVICE_ID}/events"


def event(**over):
    body = {"event_id": str(uuid4()), "seq": 1, "type": "JIRAMA_OFF", "severity": "WARNING", "state": "WAITING",
            "message": "Absence JIRAMA détectée", "captured_at": "2026-10-12T16:20:00Z",
            "data": {"jirama_present": False}}
    body.update(over)
    return body


def test_device_key_required_and_checked(client, dev_key):
    assert client.post(HB, json=heartbeat_body()).status_code == 401
    assert client.post(HB, json=heartbeat_body(), headers={"X-Device-Key": "mauvaise"}).status_code == 401
    assert client.post(f"{API}/devices/inconnu/heartbeat", json=heartbeat_body(), headers=dev_key).status_code == 401
    r = client.post(HB, json=heartbeat_body(), headers=dev_key)
    assert r.status_code == 200 and r.json()["offline_after_seconds"] == 90


def test_user_jwt_cannot_replace_device_key(client, admin):
    assert client.post(HB, json=heartbeat_body(), headers=admin).status_code == 401


def test_device_key_is_stored_hashed_and_revocable(client, db, dev_key):
    from app.core.config import settings
    from app.db.models import DeviceCredential
    cred = db.scalar(select(DeviceCredential))
    assert settings.SEED_DEVICE_KEY not in cred.key_hash
    cred.revoked_at = utcnow()
    db.commit()
    assert client.post(HB, json=heartbeat_body(), headers=dev_key).status_code == 401


def test_heartbeat_updates_status_and_keeps_unknown_as_null(client, admin, dev_key):
    client.post(HB, json=heartbeat_body(), headers=dev_key)
    st = client.get(f"{API}/devices/{DEVICE_ID}/status", headers=admin).json()
    assert st["connection"]["status"] == "ONLINE" and st["connection"]["stale"] is False
    assert st["inputs"]["ups_low_battery"] is None            # inconnu != false
    assert st["inputs"]["ats_position"] == "JIRAMA"
    assert st["state"] == "NORMAL" and st["captured_at"] == "2026-10-12T16:20:00Z"


def test_omitted_inputs_are_null_not_false(client, admin, dev_key):
    body = heartbeat_body()
    body["inputs"] = {"jirama_present": True}
    assert client.post(HB, json=body, headers=dev_key).status_code == 200
    st = client.get(f"{API}/devices/{DEVICE_ID}/status", headers=admin).json()
    assert st["inputs"]["ge_fault"] is None and st["inputs"]["ats_position"] is None


def test_invalid_heartbeat_rejected(client, dev_key):
    bad = heartbeat_body(state="N_IMPORTE_QUOI")
    r = client.post(HB, json=bad, headers=dev_key)
    assert r.status_code == 422 and r.json()["error"]["code"] == "VALIDATION_ERROR"


def test_never_seen_then_offline_detection(client, admin, db, dev_key):
    st = client.get(f"{API}/devices/{DEVICE_ID}/status", headers=admin).json()
    assert st["connection"]["status"] == "NEVER_SEEN" and st["state"] == "UNKNOWN"
    client.post(HB, json=heartbeat_body(), headers=dev_key)
    row = db.get(DeviceStatus, DEVICE_ID)
    row.received_at = utcnow() - timedelta(seconds=91)
    db.commit()
    st = client.get(f"{API}/devices/{DEVICE_ID}/status", headers=admin).json()
    assert st["connection"]["status"] == "OFFLINE" and st["connection"]["stale"] is True
    row.received_at = utcnow() - timedelta(seconds=60)
    db.commit()
    assert client.get(f"{API}/devices/{DEVICE_ID}/status", headers=admin).json()["connection"]["status"] == "ONLINE"


def test_watchdog_creates_single_offline_alert(client, db, dev_key):
    client.post(HB, json=heartbeat_body(), headers=dev_key)
    db.get(DeviceStatus, DEVICE_ID).received_at = utcnow() - timedelta(seconds=500)
    db.commit()
    assert devices_svc.watchdog_offline_alerts(db) == 1
    assert devices_svc.watchdog_offline_alerts(db) == 0   # pas de doublon tant que l'alerte est ouverte
    a = db.scalar(select(Alert))
    assert a.kind == "DEVICE_OFFLINE" and a.severity == "CRITICAL"


def test_event_idempotent_201_then_200(client, admin, dev_key):
    ev = event()
    assert client.post(EV, json=ev, headers=dev_key).status_code == 201
    r = client.post(EV, json=ev, headers=dev_key)
    assert r.status_code == 200
    hist = client.get(f"{API}/devices/{DEVICE_ID}/events", headers=admin).json()
    assert len(hist["items"]) == 1


def test_event_validation(client, dev_key):
    assert client.post(EV, json=event(type="INCONNU"), headers=dev_key).status_code == 422
    assert client.post(EV, json=event(severity="BOF"), headers=dev_key).status_code == 422
    assert client.post(EV, json=event(), headers={}).status_code == 401


def test_warning_event_creates_one_alert_and_ack_does_not_reset_fault(client, admin, operateur, lecture, db, dev_key):
    ev = event(type="FAULT", severity="CRITICAL", message="Défaut GE")
    client.post(EV, json=ev, headers=dev_key)
    client.post(EV, json=ev, headers=dev_key)  # retransmission : pas de 2e alerte
    client.post(EV, json=event(type="GE_STABLE", severity="INFO"), headers=dev_key)  # INFO : pas d'alerte
    alerts = client.get(f"{API}/alerts?status=OPEN", headers=lecture).json()["items"]
    assert len(alerts) == 1 and alerts[0]["severity"] == "CRITICAL"
    aid = alerts[0]["alert_id"]
    assert client.post(f"{API}/alerts/{aid}/ack", headers=lecture).status_code == 403
    r = client.post(f"{API}/alerts/{aid}/ack", headers=operateur)
    assert r.status_code == 200 and r.json()["status"] == "ACKNOWLEDGED"
    assert client.get(f"{API}/alerts?status=OPEN", headers=lecture).json()["items"] == []
    # l'acquittement d'une alerte ne touche pas l'état physique
    client.post(HB, json=heartbeat_body(state="FAULT", fault={"code": "GE_FAULT", "message": "x"}), headers=dev_key)
    client.post(f"{API}/alerts/{aid}/ack", headers=operateur)
    st = client.get(f"{API}/devices/{DEVICE_ID}/status", headers=admin).json()
    assert st["state"] == "FAULT" and st["fault"]["code"] == "GE_FAULT"
    audit = client.get(f"{API}/audit", headers=admin).json()["items"]
    assert any(a["action"] == "ALERT_ACKED" for a in audit)


def test_events_pagination(client, admin, dev_key):
    for i in range(5):
        client.post(EV, json=event(seq=i, type="WAIT_STARTED", severity="INFO"), headers=dev_key)
    p1 = client.get(f"{API}/devices/{DEVICE_ID}/events?limit=2", headers=admin).json()
    assert len(p1["items"]) == 2 and p1["next_cursor"]
    p2 = client.get(f"{API}/devices/{DEVICE_ID}/events?limit=2&before={p1['next_cursor']}", headers=admin).json()
    p3 = client.get(f"{API}/devices/{DEVICE_ID}/events?limit=2&before={p2['next_cursor']}", headers=admin).json()
    ids = [e["event_id"] for p in (p1, p2, p3) for e in p["items"]]
    assert len(ids) == 5 and len(set(ids)) == 5 and p3["next_cursor"] is None
    assert client.get(f"{API}/devices/{DEVICE_ID}/events?before=zzz", headers=admin).status_code == 422


def test_config_get_by_device_and_patch_by_admin(client, admin, dev_key):
    cfg = client.get(f"{API}/devices/{DEVICE_ID}/config", headers=dev_key).json()
    assert cfg["version"] == 1 and cfg["ups_autonomy_seconds"] is None and cfg["t_attente_override_seconds"] == 120
    r = client.patch(f"{API}/devices/{DEVICE_ID}/config", headers=admin, json={"cooldown_seconds": 30})
    assert r.status_code == 200 and r.json()["version"] == 2 and r.json()["cooldown_seconds"] == 30
    assert client.get(f"{API}/devices/{DEVICE_ID}/config", headers=dev_key).json()["version"] == 2
    assert client.get(f"{API}/devices/{DEVICE_ID}/config", headers=admin).status_code == 200
    assert client.get(f"{API}/devices/{DEVICE_ID}/config").status_code == 401


def test_config_ranges_validated(client, admin):
    for bad in ({"start_max_attempts": 0}, {"cooldown_seconds": -1}, {"inconnu": 1}, {}):
        assert client.patch(f"{API}/devices/{DEVICE_ID}/config", headers=admin, json=bad).status_code == 422


def test_dashboard_summary(client, admin, dev_key):
    s = client.get(f"{API}/dashboard/summary", headers=admin).json()
    assert s["global_state"] == "UNKNOWN"
    client.post(HB, json=heartbeat_body(), headers=dev_key)
    s = client.get(f"{API}/dashboard/summary", headers=admin).json()
    assert s["global_state"] == "OK" and s["devices"][0]["connection"]["status"] == "ONLINE"
    client.post(EV, json=event(type="FAULT", severity="CRITICAL"), headers=dev_key)
    s = client.get(f"{API}/dashboard/summary", headers=admin).json()
    assert s["global_state"] == "CRITICAL" and s["open_alerts"] == 1


def test_unknown_device_404(client, admin):
    r = client.get(f"{API}/devices/nope/status", headers=admin)
    assert r.status_code == 404 and r.json()["error"]["code"] == "DEVICE_NOT_FOUND"

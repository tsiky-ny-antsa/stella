from datetime import timedelta

from app.db.models import Command
from app.services.common import utcnow
from tests.conftest import API, DEVICE_ID, heartbeat_body

CMDS = f"{API}/devices/{DEVICE_ID}/commands"
NEXT = f"{CMDS}/next"


def post_cmd(client, headers, type_="START_GE", request_id="req-00000001", **extra):
    return client.post(CMDS, json={"type": type_, "request_id": request_id, **extra}, headers=headers)


def ack(client, dev_key, cid, stage, **extra):
    body = {"stage": stage, "captured_at": "2026-10-12T16:20:05Z", "state": "STARTING_GE", **extra}
    return client.post(f"{CMDS}/{cid}/ack", json=body, headers=dev_key)


def test_create_returns_202_queued_not_executed(client, operateur, online):
    r = post_cmd(client, operateur)
    assert r.status_code == 202
    b = r.json()
    assert b["status"] == "QUEUED" and b["command_id"] and "non confirmée" in b["message"]
    assert b["request_id"] == "req-00000001" and b["expires_at"] > b["created_at"]


def test_request_id_makes_creation_idempotent(client, operateur, online, db):
    a = post_cmd(client, operateur).json()
    b = post_cmd(client, operateur)
    assert b.status_code == 202 and b.json()["command_id"] == a["command_id"]
    assert db.query(Command).count() == 1
    conflict = post_cmd(client, operateur, type_="STOP_GE")          # même request_id, autre commande
    assert conflict.status_code == 409 and conflict.json()["error"]["code"] == "REQUEST_ID_CONFLICT"


def test_duplicate_active_command_refused(client, operateur, online):
    post_cmd(client, operateur, request_id="req-aaaaaaaa")
    r = post_cmd(client, operateur, request_id="req-bbbbbbbb")
    assert r.status_code == 409 and r.json()["error"]["details"]["reason"] == "DUPLICATE_ACTIVE_COMMAND"


def test_offline_device_refuses_new_command_503(client, operateur):
    r = post_cmd(client, operateur)    # jamais vu
    assert r.status_code == 503 and r.json()["error"]["code"] == "DEVICE_OFFLINE"


def test_known_manual_or_emergency_state_refused_409(client, operateur, dev_key):
    hb = heartbeat_body(state="MANUAL_LOCKOUT", control_mode="MANUAL")
    hb["inputs"]["manual_selector"] = True
    client.post(f"{API}/devices/{DEVICE_ID}/heartbeat", json=hb, headers=dev_key)
    r = post_cmd(client, operateur)
    assert r.status_code == 409
    e = r.json()["error"]
    assert e["code"] == "COMMAND_NOT_ALLOWED" and e["details"]["reason"] == "MANUAL_MODE_ACTIVE"
    hb = heartbeat_body(state="FAULT")
    hb["inputs"]["emergency_stop"] = True
    client.post(f"{API}/devices/{DEVICE_ID}/heartbeat", json=hb, headers=dev_key)
    assert post_cmd(client, operateur, request_id="req-bbbbbbbb").json()["error"]["details"]["reason"] \
        == "EMERGENCY_STOP_ACTIVE"


def test_reset_fault_only_when_fault(client, operateur, online):
    r = post_cmd(client, operateur, type_="RESET_FAULT")
    assert r.status_code == 409 and r.json()["error"]["details"]["reason"] == "NO_FAULT"


def test_set_mode_parameters_validated(client, operateur, online):
    assert post_cmd(client, operateur, type_="SET_MODE", parameters={"mode": "MANUAL"}).status_code == 422
    assert post_cmd(client, operateur, type_="SET_MODE").status_code == 422
    assert post_cmd(client, operateur, type_="SET_MODE", parameters={"mode": "REMOTE"}).status_code == 202
    assert post_cmd(client, operateur, type_="N_IMPORTE_QUOI", request_id="req-zzzzzzzz").status_code == 422


def test_next_204_when_empty_and_requires_key(client, dev_key, operateur, online):
    assert client.get(NEXT, headers=dev_key).status_code == 204
    assert client.get(NEXT).status_code == 401
    assert client.get(NEXT, headers=operateur).status_code == 401


def test_delivery_with_lease_and_redelivery(client, operateur, dev_key, online, db):
    cid = post_cmd(client, operateur).json()["command_id"]
    first = client.get(NEXT, headers=dev_key)
    assert first.status_code == 200 and first.json()["command_id"] == cid and first.json()["attempt"] == 1
    assert client.get(NEXT, headers=dev_key).status_code == 204          # bail actif : pas de re-livraison
    assert client.get(f"{API}/commands/{cid}", headers=operateur).json()["status"] == "DELIVERED"
    c = db.get(Command, cid)
    c.lease_expires_at = utcnow() - timedelta(seconds=1)                  # accusé perdu, bail expiré
    db.commit()
    second = client.get(NEXT, headers=dev_key)
    assert second.status_code == 200 and second.json()["command_id"] == cid and second.json()["attempt"] == 2


def test_accepted_command_is_not_redelivered(client, operateur, dev_key, online, db):
    cid = post_cmd(client, operateur).json()["command_id"]
    client.get(NEXT, headers=dev_key)
    assert ack(client, dev_key, cid, "ACCEPTED").status_code == 200
    db.get(Command, cid).lease_expires_at = utcnow() - timedelta(seconds=5)
    db.commit()
    assert client.get(NEXT, headers=dev_key).status_code == 204


def test_expired_command_never_delivered(client, operateur, dev_key, online, db):
    cid = post_cmd(client, operateur).json()["command_id"]
    db.get(Command, cid).expires_at = utcnow() - timedelta(seconds=1)
    db.commit()
    assert client.get(NEXT, headers=dev_key).status_code == 204
    assert client.get(f"{API}/commands/{cid}", headers=operateur).json()["status"] == "EXPIRED"
    r = ack(client, dev_key, cid, "ACCEPTED")
    assert r.status_code == 409 and r.json()["error"]["code"] == "COMMAND_EXPIRED"


def test_ack_full_lifecycle_and_history(client, operateur, dev_key, online):
    cid = post_cmd(client, operateur).json()["command_id"]
    client.get(NEXT, headers=dev_key)
    for stage in ("ACCEPTED", "RUNNING", "SUCCEEDED"):
        assert ack(client, dev_key, cid, stage).json()["status"] == stage
    final = client.get(f"{API}/commands/{cid}", headers=operateur).json()
    assert final["status"] == "SUCCEEDED" and [h["stage"] for h in final["result"]["history"]] \
        == ["ACCEPTED", "RUNNING", "SUCCEEDED"]
    assert ack(client, dev_key, cid, "SUCCEEDED").status_code == 200       # accusé rejoué : idempotent


def test_ack_invalid_transitions(client, operateur, dev_key, online):
    cid = post_cmd(client, operateur).json()["command_id"]
    client.get(NEXT, headers=dev_key)
    assert ack(client, dev_key, cid, "SUCCEEDED").status_code == 409       # pas de succès sans acceptation
    assert ack(client, dev_key, cid, "REJECTED", error_code="MANUAL_MODE_ACTIVE").status_code == 200
    r = ack(client, dev_key, cid, "ACCEPTED")
    assert r.status_code == 409 and r.json()["error"]["code"] == "INVALID_TRANSITION"
    final = client.get(f"{API}/commands/{cid}", headers=operateur).json()
    assert final["status"] == "REJECTED" and final["result"]["error_code"] == "MANUAL_MODE_ACTIVE"


def test_ack_unknown_command_and_bad_stage(client, dev_key, online):
    assert ack(client, dev_key, "00000000-0000-0000-0000-000000000000", "ACCEPTED").status_code == 404
    assert ack(client, dev_key, "x", "N_IMPORTE_QUOI").status_code == 422


def test_other_device_cannot_ack(client, admin, operateur, dev_key, online):
    cid = post_cmd(client, operateur).json()["command_id"]
    other = client.post(f"{API}/simulator/devices", json={"device_id": "autre-01"}, headers=admin).json()
    r = client.post(f"{CMDS.replace(DEVICE_ID, 'autre-01')}/{cid}/ack", headers={"X-Device-Key": other["device_key"]},
                    json={"stage": "ACCEPTED", "captured_at": "2026-10-12T16:20:05Z"})
    assert r.status_code == 404


def test_command_history_pagination_and_audit(client, operateur, admin, dev_key, online):
    for i in range(3):
        cid = post_cmd(client, operateur, request_id=f"req-hist-{i:04d}", type_="STOP_GE").json()["command_id"]
        client.get(NEXT, headers=dev_key)
        ack(client, dev_key, cid, "REJECTED", error_code="GE_NOT_RUNNING")
    p1 = client.get(f"{CMDS}?limit=2", headers=operateur).json()
    p2 = client.get(f"{CMDS}?limit=2&before={p1['next_cursor']}", headers=operateur).json()
    assert len(p1["items"]) == 2 and len(p2["items"]) == 1 and p2["next_cursor"] is None
    audit = client.get(f"{API}/audit", headers=admin).json()["items"]
    assert sum(a["action"] == "COMMAND_CREATED" for a in audit) == 3

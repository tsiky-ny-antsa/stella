#!/usr/bin/env python
"""Mock ESP32 AUTONOME : un vrai client HTTP qui parle à FastAPI exactement comme le firmware cible
(Doc/API_FASTAPI_ELECTRONIQUE.md) : X-Device-Key, heartbeat, événements idempotents, polling des
commandes, accusés. Utile pour tester le contrat de bout en bout sans la partie interne du backend.

Exemples :
  python tools/esp32_mock.py                                   # valeurs par défaut du dev
  python tools/esp32_mock.py --speed 10                        # temps simulé x10
  python tools/esp32_mock.py --script "10:jirama_present=false,200:jirama_present=true"
  python tools/esp32_mock.py --script "5:network=down,60:network=up"

--script : liste "t_simulé_en_s:clé=valeur" ; clés = entrées du monde simulé
(jirama_present, ups_low_battery, emergency_stop, manual_selector, ge_fault, ge_start_will_succeed,
ge_unstable, ats_fails, ...) + network=up|down.

Ce n'est PAS le firmware : aucun GPIO, aucune validation électrique.
"""
from __future__ import annotations

import argparse
import os
import sys
import time
from collections import deque

import httpx

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from app.services.virtual_device import VirtualDevice  # noqa: E402


def parse_value(v: str):
    low = v.lower()
    if low in ("true", "false"):
        return low == "true"
    if low in ("null", "none"):
        return None
    try:
        return float(v) if "." in v else int(v)
    except ValueError:
        return v


def parse_script(text: str) -> list[tuple[float, str, object]]:
    items = []
    for part in filter(None, (p.strip() for p in text.split(","))):
        when, _, assign = part.partition(":")
        key, _, val = assign.partition("=")
        items.append((float(when), key.strip(), parse_value(val.strip())))
    return sorted(items, key=lambda i: i[0])


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--url", default=os.getenv("STELLA_URL", "http://localhost:8000/api/v1"))
    ap.add_argument("--device-id", default=os.getenv("STELLA_DEVICE_ID", "ge-ctrl-01"))
    ap.add_argument("--key", default=os.getenv("STELLA_DEVICE_KEY", "dev-device-key-ge-ctrl-01"),
                    help="clé d'appareil (dev uniquement par défaut)")
    ap.add_argument("--speed", type=float, default=1.0, help="facteur de temps simulé (1 = temps réel)")
    ap.add_argument("--heartbeat", type=float, default=30.0, help="période heartbeat (s simulées)")
    ap.add_argument("--poll", type=float, default=4.0, help="période de polling (s simulées)")
    ap.add_argument("--duration", type=float, default=0, help="durée en s simulées (0 = infini)")
    ap.add_argument("--script", default="", help="injections de pannes planifiées")
    ap.add_argument("--quiet", action="store_true")
    a = ap.parse_args()

    def log(msg: str) -> None:
        if not a.quiet:
            print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)

    headers = {"X-Device-Key": a.key}
    base = f"{a.url}/devices/{a.device_id}"
    client = httpx.Client(timeout=10)
    vd = VirtualDevice(a.device_id)
    queue: deque[dict] = deque(maxlen=100)       # file bornée (contrat : réessai après panne réseau)
    network_up = True
    script = parse_script(a.script)
    last_hb = last_poll = -1e9
    cfg_version = 0

    def send_queue() -> None:
        while queue:
            item = queue[0]
            try:
                if item["kind"] == "event":
                    r = client.post(f"{base}/events", json=item["payload"], headers=headers)
                else:
                    r = client.post(f"{base}/commands/{item['command_id']}/ack", json=item["payload"], headers=headers)
            except httpx.HTTPError as e:
                log(f"réseau indisponible, nouvel essai plus tard ({e.__class__.__name__})")
                return
            if r.status_code >= 500:
                return                                   # on réessaiera (même event_id)
            if r.status_code in (401, 403):
                log(f"ERREUR authentification appareil ({r.status_code}) : clé invalide ?")
                sys.exit(2)
            if r.status_code >= 400:
                log(f"rejet définitif {r.status_code} : {r.text[:120]}")
            queue.popleft()

    log(f"mock ESP32 '{a.device_id}' -> {a.url} (x{a.speed})")
    try:
        while a.duration == 0 or vd.t < a.duration:
            vd.tick(1.0)
            while script and script[0][0] <= vd.t:
                _, key, val = script.pop(0)
                if key == "network":
                    network_up = val == "up"
                    log(f"réseau {'rétabli' if network_up else 'COUPÉ'}")
                else:
                    vd.set_world(**{key: val})
                    log(f"injection : {key}={val}")
            for item in vd.drain_outbox():
                queue.append(item)
                if item["kind"] == "event":
                    p = item["payload"]
                    log(f"événement {p['type']} ({p['severity']}) état={p['state']}")
            if network_up:
                try:
                    if vd.t - last_hb >= a.heartbeat:
                        r = client.post(f"{base}/heartbeat", json=vd.heartbeat_payload(), headers=headers)
                        if r.status_code == 401:
                            log("ERREUR : clé d'appareil refusée (401)")
                            return 2
                        last_hb = vd.t
                    send_queue()
                    if vd.t - last_poll >= a.poll:
                        last_poll = vd.t
                        c = client.get(f"{base}/config", headers=headers)
                        if c.status_code == 200 and c.json()["version"] != cfg_version:
                            cfg_version = c.json()["version"]
                            vd.apply_config(c.json())
                            log(f"configuration v{cfg_version} appliquée")
                        r = client.get(f"{base}/commands/next", headers=headers)
                        if r.status_code == 200:
                            cmd = r.json()
                            log(f"commande reçue {cmd['type']} ({cmd['command_id'][:8]}) tentative {cmd['attempt']}")
                            vd.handle_command(cmd)
                            queue.extend(vd.drain_outbox())
                            send_queue()
                except httpx.HTTPError as e:
                    log(f"réseau indisponible ({e.__class__.__name__}) : le contrôleur continue en local")
            time.sleep(1.0 / a.speed)
    except KeyboardInterrupt:
        log("arrêt demandé")
    return 0


if __name__ == "__main__":
    sys.exit(main())

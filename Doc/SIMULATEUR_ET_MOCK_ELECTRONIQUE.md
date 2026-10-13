# Simulateur et mock de la partie électronique

Permet de développer backend et mobile **sans matériel**. Ce n'est pas le firmware : aucun GPIO, aucune validation électrique ni de sécurité réelle.

## Trois façons de l'utiliser

1. **Intégré à l'API** (dev, ADMIN) : routes `/api/v1/simulator/{device_id}/...`
   - `GET` instantané ; `POST /world` (forcer les entrées) ; `POST /scenarios/{nom}` ; `POST /step {seconds}` (avance le temps instantanément) ; `POST /run {speed}` / `/pause` (temps réel) ; `POST /reset` ; `POST /simulator/devices` (crée un appareil simulé, clé affichée une seule fois).
2. **Faux ESP32 HTTP** : `python tools/esp32_mock.py --speed 20 --script "10:jirama_present=false,80:jirama_present=true"` — vrai client du contrat (`X-Device-Key`, heartbeat, événements, polling, accusés).
3. **Module pur** `app/services/virtual_device.py` pour les tests unitaires.

## Scénarios (`POST /simulator/{id}/scenarios/{nom}`)
`jirama_outage`, `jirama_restored`, `ups_low_battery`, `ups_battery_ok`, `ge_start_failure`, `ge_start_ok`, `ge_unstable`, `ge_stable`, `ats_stuck`, `ats_ok`, `ge_fault`, `ge_fault_clear`, `emergency_stop`, `emergency_release`, `manual_mode`, `manual_release`, `network_down`, `network_up`, `reset_world`.

## Comportement simulé
- États du contrat : NORMAL, JIRAMA_FAILURE, WAITING, STARTING_GE, GE_DETECTED, GE_RUNNING, TRANSFER_TO_GE, SUPPLY_GE, RETURNING_TO_JIRAMA, COOLDOWN, STOPPING_GE, FAULT, MANUAL_LOCKOUT.
- Sécurités locales prioritaires : manuel > arrêt d'urgence > défaut GE > commandes distantes.
- Transfert vers GE seulement après détection **puis** stabilité ; pas d'arrêt GE tant que l'ATS est sur GE ; JIRAMA stable avant retour ; refroidissement avant arrêt.
- 3 tentatives de démarrage max → FAULT `START_FAILED_MAX` ; ATS non confirmé en 10 s → FAULT `ATS_INCONSISTENT`.
- Commandes : revérifiées localement ; `ACCEPTED`/`RUNNING` immédiats, `SUCCEEDED`/`FAILED` **uniquement après constat** de l'état simulé ; re-livraison dédoublonnée.
- Perte réseau : le contrôleur continue en local, file bornée (100) rejouée à la reconnexion.

## Points à connaître
- **Mode AUTO** : un GE démarré à la main alors que la JIRAMA est présente est arrêté automatiquement après stabilité + refroidissement. Pour un essai prolongé : `SET_MODE REMOTE` (désactive les automatismes).
- Le temps simulé est indépendant de l'horloge ; les horodatages en base restent en horloge réelle.
- Valeurs par défaut = valeurs de maquette du contrat (attente 120 s, etc.), modifiables via `PATCH /devices/{id}/config`.

## Extensions proposées au contrat (à valider avec la responsable électronique)
- Heartbeat : champ optionnel `waiting.remaining_seconds` (compte à rebours pour l'UI).
- Status mobile : `ats_position` peut valoir `TRANSIT` (aucun retour actif) ou `INCONSISTENT` (deux retours actifs) ; connexion `NEVER_SEEN`.
- Config : champs ajoutés par rapport à l'exemple : aucun ; reste `ups_autonomy_seconds=null` (non mesuré).
- Constantes internes du mock non portées par la config : rappel 60 s, délai ATS 10 s, délai d'arrêt 30 s.

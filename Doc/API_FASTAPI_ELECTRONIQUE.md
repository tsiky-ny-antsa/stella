# Contrat API — ESP32 / firmware ↔ FastAPI

**Statut : contrat cible.** Le firmware réel sera adapté par la responsable électronique selon les équipements et interfaces réellement choisis.

## Principe réseau

L'ESP32 initie les requêtes HTTP vers FastAPI : heartbeat régulier, événements, polling des commandes toutes les 3 à 5 secondes et accusés de réception. FastAPI n'appelle pas directement une adresse privée de l'ESP32.

## Authentification appareil

En-tête obligatoire : X-Device-Key: <clé-propre-à-cet-appareil>.

Chaque appareil possède une clé distincte, révocable et renouvelable. Le backend conserve une vérification de cette clé plutôt que sa valeur en clair. Une clé invalide ou un appareil désactivé donne 401. Ne jamais placer la clé d'appareil dans l'APK.

HTTPS est la cible de production. HTTP ne peut servir qu'à une maquette ou un réseau local isolé, sans accès Internet.

## Heartbeat — POST /devices/{device_id}/heartbeat

Périodicité cible : 30 s ; passage hors ligne après 90 s sans heartbeat.

Exemple :
~~~json
{
  "captured_at":"2026-10-12T16:20:00Z",
  "uptime_ms":1234567,
  "firmware_version":"0.1.0",
  "state":"WAITING",
  "control_mode":"AUTO",
  "inputs":{
    "jirama_present":false,
    "ge_present":false,
    "ups_low_battery":null,
    "ats_on_jirama":true,
    "ats_on_ge":false,
    "ge_fault":false,
    "emergency_stop":false,
    "manual_selector":false
  },
  "outputs":{
    "start_ge_command_active":false,
    "stop_ge_command_active":false,
    "transfer_to_ge_command_active":false,
    "transfer_to_jirama_command_active":false,
    "alarm_active":false
  },
  "ge":{"detected":false,"stable":false,"running":false,"start_attempt":0},
  "fault":{"code":"NONE","message":null}
}
~~~

Réponse 200 : {"received_at":"2026-10-12T16:20:01Z","offline_after_seconds":90}.

Les entrées indisponibles doivent rester null ou être omises selon le schéma convenu ; ne pas les déclarer false sans l'indiquer.

États recommandés : INIT, NORMAL, JIRAMA_FAILURE, WAITING, STARTING_GE, GE_DETECTED, GE_RUNNING, TRANSFER_TO_GE, SUPPLY_GE, RETURNING_TO_JIRAMA, COOLDOWN, STOPPING_GE, FAULT, MANUAL_LOCKOUT. Modes : MANUAL, AUTO, REMOTE.

## Événement — POST /devices/{device_id}/events

Un événement par requête. L'event_id reste identique lors d'une nouvelle tentative ; le backend dédoublonne sur cet identifiant.

~~~json
{
  "event_id":"uuid",
  "seq":42,
  "type":"JIRAMA_OFF",
  "severity":"WARNING",
  "state":"WAITING",
  "message":"Absence JIRAMA détectée",
  "captured_at":"2026-10-12T16:20:00Z",
  "data":{"jirama_present":false}
}
~~~

Types initiaux : DEVICE_BOOT, JIRAMA_OFF, JIRAMA_ON, WAIT_STARTED, WAIT_REMINDER, UPS_LOW_BATTERY, START_REQUESTED, GE_DETECTED, GE_STABLE, START_FAILED, TRANSFER_REQUESTED, ATS_ON_GE, ATS_ON_JIRAMA, STOP_REQUESTED, STOP_FAILED, FAULT, FAULT_RESET, MODE_CHANGED, EMERGENCY_STOP.

Sévérités : INFO, WARNING, CRITICAL. Réponse 201 si créé, 200 si déjà reçu. En cas de panne réseau, l'appareil garde une file bornée et réessaie.

## Récupération de commande — GET /devices/{device_id}/commands/next

Le firmware interroge périodiquement cette route. 204 No Content signifie aucune commande disponible.

Réponse 200 :
~~~json
{
  "command_id":"uuid",
  "type":"START_GE",
  "parameters":{},
  "created_at":"2026-10-12T16:20:00Z",
  "expires_at":"2026-10-12T16:21:00Z",
  "attempt":1
}
~~~

Une livraison n'est pas une exécution. La commande est livrée avec un bail ; si aucun accusé n'arrive, elle peut être livrée à nouveau. Le firmware doit dédoublonner command_id autant que son stockage le permet.

## Accusé — POST /devices/{device_id}/commands/{command_id}/ack

Acceptation :
~~~json
{
  "stage":"ACCEPTED",
  "captured_at":"2026-10-12T16:20:05Z",
  "state":"STARTING_GE",
  "error_code":null,
  "message":null
}
~~~

Étapes permises : ACCEPTED, REJECTED, RUNNING, SUCCEEDED, FAILED.

Refus local :
~~~json
{
  "stage":"REJECTED",
  "captured_at":"2026-10-12T16:20:05Z",
  "state":"MANUAL_LOCKOUT",
  "error_code":"MANUAL_MODE_ACTIVE",
  "message":"Commande distante refusée en mode manuel."
}
~~~

N'envoyer SUCCEEDED qu'après vérification physique du résultat exigé, par exemple GE stable ou position ATS confirmée. Une impulsion envoyée n'est pas une confirmation de succès.

## Configuration — GET /devices/{device_id}/config

La réponse donne une configuration versionnée : temporisations, stabilité JIRAMA/GE, délai de refroidissement, nombre et intervalle des tentatives et rappels. Les détails propres au matériel (impulsion démarrage GE, polarité, commande ATS) restent non définis tant que les modèles réels ne sont pas confirmés.

~~~json
{
  "version":1,
  "auto_enabled":true,
  "ups_autonomy_seconds":null,
  "safety_margin_seconds":null,
  "t_attente_override_seconds":120,
  "jirama_stable_seconds":180,
  "ge_stable_seconds":8,
  "start_max_attempts":3,
  "start_attempt_timeout_seconds":30,
  "start_retry_pause_seconds":10,
  "cooldown_seconds":60
}
~~~

ups_autonomy_seconds null signifie non mesuré. La valeur 120 s est réservée à la maquette jusqu'à mesure de l'autonomie réelle et validation de la marge.

## Sécurité fonctionnelle

- Revérifier localement chaque commande avec les entrées physiques actuelles.
- Manuel, arrêt d'urgence, défaut GE et incohérence ATS passent avant les commandes distantes.
- Ne transférer vers GE qu'après détection et stabilité de sa tension.
- Ne pas arrêter le GE tant que la charge peut en dépendre.
- La prévention de la mise en parallèle de deux sources ne peut pas dépendre uniquement du logiciel.
- Ce contrat ne fixe aucun câblage, GPIO ou niveau électrique.

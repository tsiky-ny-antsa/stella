# Contrat API — Android Java ↔ FastAPI

**Statut : contrat cible.** L'ancien serveur Java/Spring ne propose pas encore ces routes.

## Conventions

- Base : http://<hôte>:8000/api/v1 en développement ; HTTPS en production.
- JSON UTF-8, dates ISO 8601 UTC, identifiants de commande et d'événement UUID.
- Authentification : Authorization: Bearer <access_token>.
- Le mobile ne contient aucun secret d'appareil.
- Les rôles sont vérifiés sur le backend même si l'interface masque certaines options.

## Rôles

| Rôle | Lire états/historique | Commander | Acquitter alerte | Modifier config / gérer utilisateurs |
|---|---|---|---|---|
| ADMIN | Oui | Oui | Oui | Oui |
| OPERATEUR | Oui | Oui, si autorisé | Oui | Non |
| LECTURE | Oui | Non | Non | Non |

Le mode manuel matériel et l'arrêt d'urgence restent prioritaires quel que soit le rôle.

## Authentification

### POST /auth/login

Requête :
~~~json
{"username":"operateur","password":"<mot-de-passe>"}
~~~

Réponse 200 :
~~~json
{
  "access_token": "<jwt>",
  "token_type": "Bearer",
  "expires_in": 43200,
  "user": {"id":"uuid","username":"operateur","display_name":"Opérateur","role":"OPERATEUR"}
}
~~~

Erreurs : 401 INVALID_CREDENTIALS, 422 VALIDATION_ERROR.

### GET /auth/me

Retourne l'utilisateur et son rôle. Token absent ou expiré : 401.

## Vue d'ensemble et état

- GET /dashboard/summary — état global, alertes ouvertes, commandes en cours.
- GET /devices — équipements disponibles pour l'utilisateur.
- GET /devices/{device_id} — métadonnées, version et dernière communication.
- GET /devices/{device_id}/status — état courant connu, entrées, mode, défauts et fraîcheur.
- GET /devices/{device_id}/events?limit=50&before=<cursor> — historique paginé.
- GET /devices/{device_id}/commands — historique paginé.
- GET /alerts?status=OPEN&limit=50 — alertes filtrables.
- POST /alerts/{alert_id}/ack — acquitter une alerte ; ne réinitialise pas un défaut physique.
- GET /audit?limit=50 — journal sensible, ADMIN seulement.

Exemple de réponse GET status :
~~~json
{
  "device_id": "ge-ctrl-01",
  "connection": {"status":"ONLINE","last_seen_at":"2026-10-12T16:20:00Z","stale":false},
  "state": "WAITING",
  "control_mode": "AUTO",
  "inputs": {
    "jirama_present": false,
    "ge_present": false,
    "ups_low_battery": null,
    "ats_position": "JIRAMA",
    "ge_fault": false,
    "emergency_stop": false,
    "manual_selector": false
  },
  "ge": {"detected":false,"stable":false,"running":false},
  "waiting": {"remaining_seconds":820},
  "fault": {"code":"NONE","message":null},
  "captured_at":"2026-10-12T16:20:00Z"
}
~~~

Les champs de capteur inconnus peuvent être null ; ils ne doivent pas être transformés silencieusement en false.

## Commandes

### POST /devices/{device_id}/commands

~~~json
{
  "type": "START_GE",
  "request_id": "uuid-client-unique",
  "reason": "Demande opérateur"
}
~~~

Valeurs autorisées de type : START_GE, STOP_GE, TRANSFER_TO_GE, TRANSFER_TO_JIRAMA, RESET_FAULT, SET_MODE. Pour SET_MODE, fournir parameters: {"mode":"AUTO"} ou {"mode":"REMOTE"}. Le mode distant ne peut pas neutraliser un sélecteur matériel MANUEL.

Réponse 202 Accepted :
~~~json
{
  "command_id":"uuid",
  "request_id":"uuid-client-unique",
  "device_id":"ge-ctrl-01",
  "type":"START_GE",
  "status":"QUEUED",
  "created_at":"2026-10-12T16:20:00Z",
  "expires_at":"2026-10-12T16:21:00Z",
  "message":"Demande enregistrée ; exécution non confirmée."
}
~~~

request_id rend la création idempotente : si le client répète la demande après un délai réseau, le backend retourne la commande existante.

### GET /commands/{command_id}

Statuts : QUEUED, DELIVERED, ACCEPTED, RUNNING, SUCCEEDED, REJECTED, FAILED, EXPIRED, CANCELLED.

Erreurs principales de POST /commands : 401 non authentifié, 403 FORBIDDEN, 404 DEVICE_NOT_FOUND, 409 COMMAND_NOT_ALLOWED, 422 VALIDATION_ERROR, 503 DEVICE_OFFLINE si la politique l'interdit.

**202 ne signifie jamais « GE démarré » ou « ATS basculé ».**

## Configuration et utilisateurs (ADMIN)

- GET /devices/{device_id}/config
- PATCH /devices/{device_id}/config
- GET /users
- POST /users
- PATCH /users/{user_id}

Le backend valide les plages, persiste la configuration et la rend disponible au contrôleur.

## Format d'erreur

~~~json
{
  "error": {
    "code":"COMMAND_NOT_ALLOWED",
    "message":"La commande n'est pas autorisée dans l'état actuel.",
    "details":{"state":"MANUAL_LOCKOUT"},
    "request_id":"uuid"
  }
}
~~~

## Règles UI

- Demander confirmation avant démarrage, arrêt ou transfert.
- Afficher la progression par le statut serveur.
- Désactiver les actions incompatibles avec l'état connu, sans considérer l'UI comme un mécanisme de sécurité.
- Montrer « État inconnu / données périmées » si le timestamp est trop ancien.
- Ne jamais afficher le GE actif sur le seul retour d'une commande START_GE.

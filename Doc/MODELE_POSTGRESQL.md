# Modèle de données PostgreSQL

Schéma de départ volontairement réduit ; ajouter une table seulement si elle sert à une fonction prévue.

| Table | Rôle | Champs proposés |
|---|---|---|
| users | Comptes et rôles | id, username, password_hash, display_name, role, is_active, created_at |
| devices | Contrôleurs connus | id, device_id, name, location, is_active, firmware_version |
| device_credentials | Authentification contrôleur | device_id, key_hash, revoked_at, rotated_at |
| device_status | Dernier état reçu | device_id, status_json, captured_at, received_at, connection_state |
| device_events | Historique immuable | id, event_id, device_id, seq, type, severity, data_json, captured_at, received_at |
| commands | File et résultats | id, request_id, device_id, requested_by, type, parameters_json, status, created_at, expires_at, result_json |
| alerts | Alertes opérateur | id, device_id, event_id, severity, title, message, status, acknowledged_by, acknowledged_at |
| device_configs | Configuration versionnée | device_id, version, config_json, updated_by, updated_at |
| audit_logs | Actions sensibles | id, user_id, device_id, action, details_json, created_at |

## Contraintes

- Clé primaire UUID ou identifiant stable équivalent.
- users.username et devices.device_id uniques.
- device_events.event_id unique pour dédoublonner les retransmissions.
- (device_id, request_id) unique pour idempotence des demandes mobile.
- Index événements par appareil/date ; commandes par appareil/date et statut/expiration ; alertes par statut/date.
- Timestamps en timestamptz UTC.
- Suppression logique des comptes/appareils ; conservation de l'audit des opérations critiques.

## File de commandes

Statuts : QUEUED, DELIVERED, ACCEPTED, RUNNING, SUCCEEDED, REJECTED, FAILED, EXPIRED, CANCELLED.

Ne jamais déclarer SUCCEEDED par la seule écriture en base : le statut vient d'un accusé contrôleur/simulateur après confirmation. Une commande livrée porte un bail pour être renvoyée si l'appareil perd l'accusé, sans rejouer l'action deux fois.

La configuration est versionnée et validée côté backend et firmware. Le délai d'attente cible est basé sur l'autonomie réelle UPS moins une marge ; les paramètres propres au matériel restent non validés tant que GE, ATS et UPS ne sont pas identifiés.

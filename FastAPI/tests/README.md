# Tests du backend

Les tests doivent démontrer le fonctionnement sans ESP32 physique.

Priorités :
1. authentification et permissions ;
2. création de commande avec réponse 202 et identifiant stable ;
3. dédoublonnage par request_id ;
4. expiration et récupération répétée d'une commande ;
5. refus si l'état ne permet pas l'action ;
6. heartbeat et détection hors ligne ;
7. événements et alertes idempotents ;
8. simulateur : coupure, batterie faible, démarrage, stabilisation GE, transfert ATS, retour JIRAMA, échec et arrêt d'urgence.

Utiliser une base de test séparée. Ne jamais lancer de migration ou de nettoyage contre une base utilisée par le projet réel.

## État actuel (78 tests)

| Fichier | Couvre |
|---|---|
| `test_auth.py` | login, hash, JWT expiré/invalide, rôles côté backend, gestion d'utilisateurs, refus de config dangereuse en production |
| `test_device_api.py` | clé d'appareil (invalide/révoquée), heartbeat, null ≠ false, hors ligne (90 s), watchdog, événements idempotents, alertes, pagination, config |
| `test_commands.py` | 202 + identifiant stable, `request_id`, doublons, 503 hors ligne, 409 manuel/urgence, bail et re-livraison, expiration, transitions d'accusé |
| `test_virtual_device.py` | contrôleur virtuel seul : cycle complet, 3 échecs de démarrage, GE instable, ATS bloqué, urgence, manuel, défaut GE |
| `test_simulator_api.py` | scénarios de bout en bout via l'API, commandes opérateur confirmées/refusées, perte et reprise réseau |

Base de test : SQLite en mémoire, **jamais** la base du projet. Non couvert : PostgreSQL réel, charge, firmware réel (voir le journal d'implémentation).

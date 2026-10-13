# Journal d'implémentation FastAPI + mock électronique

Date : 2026-10-13. Base : `main` (commit ee9fa52, docs seulement). Résultat : backend exécutable, 78 tests verts, essai réel uvicorn réussi.

## Livré
1. Socle : config (refus de démarrer en production avec JWT faible/simulateur/seed), sécurité (PBKDF2, JWT, clés d'appareil hachées), format d'erreur unique, 9 tables SQLAlchemy, migration Alembic 0001.
2. API mobile (JWT, rôles ADMIN/OPERATEUR/LECTURE) et API contrôleur (`X-Device-Key`) conformes aux contrats `Doc/API_FASTAPI_*.md`.
3. File de commandes : 202 QUEUED, idempotence `request_id`, bail + re-livraison, expiration, accusés avec transitions contrôlées, refus anticipé 409/503.
4. Événements idempotents (201/200), alertes, hors ligne (90 s + watchdog), configuration versionnée validée, audit.
5. **Mock électronique** : contrôleur ESP32 virtuel, simulateur intégré (scénarios, temps réel), `tools/esp32_mock.py` HTTP. Voir [guide](SIMULATEUR_ET_MOCK_ELECTRONIQUE.md).
6. Checklist mise à jour (31 cases cochées, partielles annotées).

## Vérifié / non vérifié
- Vérifié : 78 tests (SQLite mémoire) ; uvicorn réel + mock HTTP + commande opérateur confirmée ; migration SQLite aller/retour.
- **Non vérifié** : PostgreSQL réel (SQL généré seulement) ; matériel ESP32 ; charge ; application Android.

## Décisions
- UUID en `String(36)`, JSON générique (JSONB sur PG) pour tester sur SQLite.
- `COMMANDS_REQUIRE_ONLINE=true` : commande refusée (503) si l'appareil est hors ligne.
- Comptes/clé de démo seulement en `APP_ENV=development`.

## Reste à faire (priorité)
1. Tester sur un vrai PostgreSQL (`alembic upgrade head`) et corriger au besoin.
2. Application Android Java contre ce contrat.
3. Adapter le firmware (responsable électronique) ; confronter ses échanges au mock.
4. Renouveler la clé historique de l'archive ; HTTPS en production.
5. P1 restant : résumé des coupures/durées, export CSV, rotation de clé d'appareil par API.

## Sécurité
Le token GitHub fourni dans la conversation doit être **révoqué** (Settings → Developer settings → Tokens). Il n'est écrit dans aucun fichier du dépôt.

# Backend FastAPI

Python + FastAPI, SQLAlchemy 2.x, Alembic. Cible de production : PostgreSQL. Ce backend est **implémenté et testé avec un contrôleur ESP32 simulé** (voir [Simulateur et mock électronique](../Doc/SIMULATEUR_ET_MOCK_ELECTRONIQUE.md)) ; il n'a **pas** été validé avec le matériel réel.

> État détaillé, décisions, limites et reste à faire : [Journal d'implémentation](../Doc/JOURNAL_IMPLEMENTATION_FASTAPI.md).

## Démarrage rapide (développement, sans PostgreSQL)

~~~bash
cd FastAPI
python -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt   # inclut pytest et httpx
cp .env.example .env
# Essai local : éditer .env  ->  DATABASE_URL=sqlite:///./stella_dev.db
uvicorn app.main:app --reload
~~~

- Documentation interactive : <http://localhost:8000/docs> — santé : `/health`.
- En `APP_ENV=development` avec SQLite, les tables sont créées au démarrage et des comptes de **démo** sont semés : `admin`, `operateur`, `lecture` (mots de passe `*-dev-only`, cf. `.env.example`) et l'appareil simulé `ge-ctrl-01`.
- Avec PostgreSQL : créer une base dédiée puis `alembic upgrade head` (jamais contre une base de production sans sauvegarde).

### Voir un scénario complet en 2 minutes

~~~bash
# Terminal 1 : API
uvicorn app.main:app
# Terminal 2 : faux ESP32 HTTP (coupure JIRAMA à t=10 s, retour à t=80 s), temps accéléré x20
python tools/esp32_mock.py --speed 20 --heartbeat 10 --duration 200 \
  --script "10:jirama_present=false,80:jirama_present=true"
~~~

Ou sans second processus : se connecter (`POST /api/v1/auth/login`), puis utiliser les routes `/api/v1/simulator/...` (scénarios, avance du temps, mode temps réel) — voir le guide du simulateur.

## Docker

Le `Dockerfile` utilise Python 3.12 slim, installe uniquement les dépendances d'exécution depuis `requirements.txt`, exécute Uvicorn sans mode `--reload` et lance l'application avec un utilisateur non privilégié. Les dépendances de test sont dans `requirements-dev.txt`. Le simulateur et les données de démonstration sont désactivés par défaut dans l'image.

Depuis la racine du dépôt :

```bash
docker build -t stella-api:local ./FastAPI
```

Démarrage (remplacer l'adresse de base de données par une adresse joignable depuis le conteneur) :

```bash
docker run --rm --name stella-api \
  -p 8000:8000 \
  -e APP_ENV=production \
  -e DATABASE_URL='postgresql+psycopg://stella_app:mot-de-passe@hote-postgres:5432/stella' \
  -e JWT_SECRET_KEY='<secret-aleatoire-long-de-32-caracteres-minimum>' \
  -e SIMULATOR_ENABLED=false \
  -e SEED_DEV_DATA=false \
  stella-api:local
```

Ne pas placer de vrais secrets dans le dépôt, le Dockerfile ou l'historique du terminal partagé. Injecter les valeurs depuis l'environnement de déploiement ou un gestionnaire de secrets. Depuis un conteneur, `localhost` désigne le conteneur lui-même, pas l'hôte PostgreSQL.

- API : `http://localhost:8000`
- Documentation interactive : `http://localhost:8000/docs`
- Contrôle de santé HTTP : `http://localhost:8000/health`

Le contrôle Docker `HEALTHCHECK` vérifie que l'endpoint HTTP répond ; il ne prouve pas que PostgreSQL accepte les connexions.

## Limites actuelles avant un déploiement réel

- Le dépôt ne contient pas encore de migration Alembic/configuration de migration. Le schéma PostgreSQL doit donc être préparé séparément ; le conteneur n'exécute pas de création automatique des tables en production.
- La validation de démarrage exige un `JWT_SECRET_KEY` valide en production, avec au moins 32 caractères.
- En production, garder `SIMULATOR_ENABLED=false` et `SEED_DEV_DATA=false`.
- Le simulateur et les comptes de démonstration sont réservés au développement/test.
- Une compilation Docker ne remplace ni les tests FastAPI, ni les tests d'intégration à PostgreSQL, ni la validation de sécurité.

## Organisation

~~~text
FastAPI/
├── app/
│   ├── main.py                     # app, erreurs, CORS, watchdog hors ligne, seed dev
│   ├── api/deps.py                 # JWT, rôles, clé d'appareil
│   ├── api/v1/router.py
│   ├── api/v1/routes/              # auth, devices, commands, events, alerts, users, simulator
│   ├── core/                       # config, security (PBKDF2/JWT/clés), errors (format unique)
│   ├── db/                         # session, models (9 tables)
│   ├── schemas/                    # Pydantic (auth, device, common)
│   └── services/                   # règles métier
│       ├── devices.py              # heartbeat, état/fraîcheur, config versionnée, watchdog
│       ├── events.py               # événements idempotents, alertes
│       ├── commands.py             # file de commandes : bail, expiration, accusés
│       ├── virtual_device.py       # ESP32 VIRTUEL (module pur, sans I/O)
│       ├── simulator.py            # fait vivre le contrôleur virtuel via les services
│       └── seed.py                 # données de démo (développement uniquement)
├── alembic/                        # migration 0001 (schéma initial)
├── tools/esp32_mock.py             # faux ESP32 autonome en HTTP
├── tests/                          # 78 tests
├── Dockerfile
├── .env.example
├── requirements.txt / requirements-dev.txt
~~~

## Routes (préfixe `/api/v1`)

| Public cible | Routes |
|---|---|
| Mobile (JWT) | `POST /auth/login`, `GET /auth/me`, `GET /dashboard/summary`, `GET /devices`, `GET /devices/{id}`, `GET /devices/{id}/status`, `GET /devices/{id}/events`, `GET/POST /devices/{id}/commands`, `GET /commands/{id}`, `GET /alerts`, `POST /alerts/{id}/ack`, `GET/PATCH /devices/{id}/config` (PATCH = ADMIN), `GET/POST/PATCH /users`, `GET /audit` (ADMIN) |
| Contrôleur (`X-Device-Key`) | `POST /devices/{id}/heartbeat`, `POST /devices/{id}/events`, `GET /devices/{id}/commands/next`, `POST /devices/{id}/commands/{cid}/ack`, `GET /devices/{id}/config` |
| Dev seulement (ADMIN) | `/simulator/...` |

Contrats : [mobile](../Doc/API_FASTAPI_MOBILE.md) et [électronique](../Doc/API_FASTAPI_ELECTRONIQUE.md). Erreurs : `{"error":{"code","message","details","request_id"}}`.

## Principes appliqués

- **202 Accepted = demande enregistrée**, jamais « GE démarré ». `SUCCEEDED` ne vient que d'un accusé du contrôleur (ou simulateur) après constat de l'état.
- Aucun endpoint n'agit sur le matériel : il crée une commande ; le contrôleur la récupère, **revérifie localement** et accuse.
- Idempotence : `(device_id, request_id)` pour les commandes, `event_id` pour les événements, accusés rejouables.
- Capteur inconnu = `null`, jamais converti en `false`.
- Rôles contrôlés côté backend ; clés d'appareil et mots de passe stockés hachés ; en production le démarrage est refusé si JWT faible, simulateur ou données de démo actifs.

## Tests

~~~bash
cd FastAPI && python -m pytest -q      # 78 tests, base SQLite en mémoire, sans ESP32
~~~

Voir [tests/README.md](tests/README.md).

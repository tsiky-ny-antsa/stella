# Stella — supervision et commande de l'alimentation

## Objectif

Construire rapidement une solution simple à tester de bout en bout pour superviser l'alimentation JIRAMA / UPS / groupe électrogène (GE).

Cible : **FastAPI + Python**, **PostgreSQL**, **Android en Java**, et un **simulateur logiciel** pour les essais sans matériel. La responsable électronique adaptera ensuite son firmware et l'interface physique au contrat documenté ici.

Le backend FastAPI est implémenté et testé avec un **contrôleur ESP32 simulé** (voir [FastAPI](FastAPI/README.md)). Le nouveau firmware, l'application Android et la validation sur PostgreSQL/matériel réels ne sont pas faits.

## Dossiers

| Dossier | Rôle |
|---|---|
| [FastAPI](FastAPI/README.md) | Organisation du backend Python |
| [mobile](mobile/README.md) | Application Android Java |
| [Doc](Doc/README.md) | Architecture, contrats API, conception, checklist et données |
| [Electronique](Electronique/README.md) | Contrat logiciel et sketch de référence inchangé |
| [Archive](Archive/README.md) | Copies des sources avant réorganisation |

## À lire en premier

1. [Architecture cible](Doc/ARCHITECTURE.md)
2. [Contrat mobile ↔ FastAPI](Doc/API_FASTAPI_MOBILE.md)
3. [Contrat ESP32 ↔ FastAPI](Doc/API_FASTAPI_ELECTRONIQUE.md)
4. [Conception mobile](Doc/DESIGN_MOBILE.md)
5. [Checklist fonctionnelle](Doc/CHECKLIST_FONCTIONNELLE.md)
6. [Modèle PostgreSQL](Doc/MODELE_POSTGRESQL.md)
7. [Décisions et limites](Doc/DECISIONS_ET_LIMITES.md)

## Principe d'intégration

Le mobile ne communique qu'avec FastAPI. L'ESP32 initie aussi ses requêtes HTTP vers FastAPI : heartbeat, événements, récupération des commandes et accusés de réception. Le backend n'a donc pas besoin de pouvoir joindre directement l'ESP32.

Le contrôleur conserve les automatismes et sécurités locales. Une commande enregistrée ne prouve pas que le GE a démarré ou que l'ATS a changé de position.

## État

- [x] Implémenter FastAPI (testé sur SQLite ; PostgreSQL à vérifier).
- [x] Implémenter le simulateur logiciel ([guide](Doc/SIMULATEUR_ET_MOCK_ELECTRONIQUE.md)).
- [ ] Construire l'application Android Java selon la conception.
- [ ] Adapter et tester le firmware selon le contrat.
- [ ] Tester l'intégration ; faire valider les essais physiques par la personne compétente.

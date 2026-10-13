# Documentation de référence

Cette documentation définit la cible à développer maintenant. Elle combine les deux conceptions fonctionnelles, la conception logicielle existante et les capacités visibles du sketch Arduino actuel.

## Documents

- [Architecture cible](ARCHITECTURE.md)
- [Contrat API FastAPI ↔ Android](API_FASTAPI_MOBILE.md)
- [Contrat API FastAPI ↔ électronique](API_FASTAPI_ELECTRONIQUE.md)
- [Conception mobile](DESIGN_MOBILE.md)
- [Checklist fonctionnelle](CHECKLIST_FONCTIONNELLE.md)
- [Modèle PostgreSQL](MODELE_POSTGRESQL.md)
- [Décisions et limites](DECISIONS_ET_LIMITES.md)
- [Simulateur et mock électronique](SIMULATEUR_ET_MOCK_ELECTRONIQUE.md)
- [Journal d'implémentation FastAPI](JOURNAL_IMPLEMENTATION_FASTAPI.md)

## Références amont

- [Analyse des conceptions](https://github.com/ProjetStella/ge-controller-esp32/blob/main/docs/ANALYSE_CONCEPTIONS.md)
- [Architecture logicielle et machine à états](https://github.com/ProjetStella/ge-controller-esp32/blob/main/docs/ARCHITECTURE.md)
- [API de la conception ESP32 existante](https://github.com/ProjetStella/ge-controller-esp32/blob/main/docs/API.md)
- [Signaux et GPIO — point de départ à confirmer](https://github.com/ProjetStella/ge-controller-esp32/blob/main/docs/HARDWARE_SIGNALS.md)
- [Conception fonctionnelle précédente](../Archive/avant-reorganisation/Doc/mm_projet_commande_GE_v2.md)
- [Sketch de référence](../Electronique/reference/DetectionJIRAMA2.ino)

Les routes ci-dessous sont la cible du nouveau développement, pas la description de l'ancien serveur Java ou du sketch.

- [Journal d'implémentation mobile](MOBILE_IMPLEMENTATION.md) — ce qui est fait, hypothèses API, reste à faire.

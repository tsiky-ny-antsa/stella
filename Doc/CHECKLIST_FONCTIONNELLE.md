# Checklist fonctionnelle

Cocher uniquement après implémentation et test, pas lorsque la fonction est simplement documentée.

**Mise à jour (backend FastAPI + simulateur)** : `[x]` = implémenté **et** testé avec le backend et le contrôleur *simulé* (78 tests + essai réel uvicorn). Cela ne valide aucun comportement du matériel réel. Les cases non cochées ont une note quand le travail est partiel. Détail : [Journal d'implémentation](JOURNAL_IMPLEMENTATION_FASTAPI.md).

> Application Java : code écrit sur la branche `feature/mobile-android` mais non compilé ni testé sur appareil — les cases restent donc décochées. Détails : [MOBILE_IMPLEMENTATION.md](MOBILE_IMPLEMENTATION.md).

## P0 — Minimum démontrable

### Backend et stockage
- [x] FastAPI démarre avec configuration locale. *(vérifié avec uvicorn + SQLite)*
- [ ] PostgreSQL est la base active. *(à faire/à vérifier : SQLite utilisé pour les tests ; PostgreSQL non vérifié en conditions réelles)*
- [ ] Migrations Alembic créent et mettent à jour les tables. *(à faire/à vérifier : migration 0001 vérifiée aller/retour sur SQLite ; SQL PostgreSQL généré mais non exécuté)*
- [ ] Aucun secret réel n'est commité. *(à faire/à vérifier : valeurs de démo seulement dans le nouveau code ; clé historique toujours présente dans Archive/ (à renouveler))*
- [x] /health ne divulgue aucune donnée sensible.
- [x] Format d'erreur API cohérent.

### Connexion et droits
- [x] Connexion identifiant/mot de passe.
- [x] Mots de passe stockés sous forme de hash. *(PBKDF2-SHA256 salé)*
- [ ] Token expirant et gestion de session expirée dans le mobile. *(à faire/à vérifier : backend fait (expiration + 401) ; côté mobile à faire)*
- [x] Rôles ADMIN, OPERATEUR, LECTURE validés dans le backend.
- [x] Un utilisateur sans droits ne peut pas contourner les contrôles via l'API.

### État et événements
- [x] Heartbeat du contrôleur.
- [x] Détection d'appareil hors ligne. *(90 s + alerte DEVICE_OFFLINE)*
- [x] Données inconnues distinctes de false.
- [ ] Timestamp et fraîcheur visibles. *(à faire/à vérifier : exposés par l'API (captured_at, last_seen_at, stale) ; affichage mobile à faire)*
- [ ] Historique d'événements enregistré dans PostgreSQL. *(à faire/à vérifier : enregistré en base (testé SQLite) ; PostgreSQL non vérifié)*

### Commandes
- [x] Création d'une commande avec identifiant et request_id.
- [x] 202 Accepted signifie demande enregistrée, pas résultat physique.
- [x] Expiration des commandes.
- [x] ESP32 simulé récupère les commandes par polling.
- [x] Contrôleur accepte ou refuse avec une raison. *(simulateur)*
- [x] Résultat provenant du contrôleur/simulateur après vérification.
- [x] Une requête répétée ne provoque pas une commande dupliquée.
- [x] Mode manuel et arrêt d'urgence restent prioritaires. *(backend + simulateur ; à re-valider sur le matériel)*

### Application Java 
- [ ] Connexion et gestion de session expirée.  *(implémenté, non testé)*
- [ ] Tableau de bord et état alimentation.  *(implémenté, non testé)*
- [ ] Statut JIRAMA, UPS, GE, ATS avec heure et fraîcheur.  *(implémenté, non testé)*
- [ ] Confirmation des commandes.  *(implémenté, non testé)*
- [ ] Progression des commandes visible.  *(implémenté, non testé)*
- [ ] Historique et alertes consultables.  *(implémenté, non testé)*
- [ ] Chargement, erreur réseau, état vide et hors ligne traités.  *(implémenté, non testé)*
- [ ] Accès administrateur conditionné par le rôle.  *(squelette seulement ; implémenté, non testé)*
- [ ] Palette JIRAMA et contrastes vérifiés. *(palette appliquée, contrastes calculés ; non testé sur appareil)*

### Simulateur et tests
- [x] Perte JIRAMA et temporisation.
- [x] Rappel opérateur et batterie faible.
- [x] Démarrage GE réussi et GE instable.
- [x] Trois tentatives de démarrage échouées.
- [x] Détection puis stabilité GE avant transfert.
- [ ] Retour ATS absent ou contradictoire. *(à faire/à vérifier : « absent » simulé et testé ; « contradictoire » (deux positions à la fois) non simulé)*
- [x] Retour JIRAMA stable, refroidissement puis arrêt.
- [x] Mode manuel, arrêt d'urgence, défaut GE.
- [x] Perte réseau et reconnexion.
- [x] Tests automatisés des API et transitions critiques. *(78 tests)*

## P1 — Si le temps le permet
- [x] Gestion d'utilisateurs par ADMIN.
- [x] Configuration des temporisations autorisées. *(PATCH config validé, versionné)*
- [x] Acquittement des alertes.
- [ ] Résumé des coupures et durées.
- [ ] Export CSV.
- [x] Journal d'audit. *(commandes, config, utilisateurs, alertes)*
- [ ] Notifications Android.
- [ ] Thème sombre.

## P2 — Pas avant P0
- [ ] Graphiques avancés.
- [ ] MQTT ou WebSocket.
- [ ] Services distribués.
- [ ] Fonctions automatiques supplémentaires non demandées.
- [ ] Communication directe mobile → ESP32.

## Critère de démonstration

L'opérateur peut se connecter, observer un scénario simulé, soumettre une commande, suivre son résultat, consulter l'historique et constater un refus de sécurité sans matériel réel. Cela ne valide ni le câblage ni la sécurité du système physique installé.

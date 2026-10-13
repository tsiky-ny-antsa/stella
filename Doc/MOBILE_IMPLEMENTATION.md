# Application Android Java — journal d'implémentation

Branche : `feature/mobile-android` — dossier `mobile/`.
Dernière mise à jour : 13/10/2026.

> **Statut honnête : le code est écrit mais n'a pas été compilé ni lancé sur un appareil/émulateur.**
> L'environnement de développement n'avait ni SDK Android ni accès à Google Maven. Seuls ont été vérifiés :
> les 6 layouts + ressources (XML bien formé), la syntaxe Java de tous les fichiers (`javac`, aucune erreur de syntaxe ;
> seuls les symboles Android/`org.json`, absents ici, ne se résolvent pas) et la classe `Dates` (exécutée réellement).
> **Première action à faire : ouvrir `mobile/` dans Android Studio, synchroniser Gradle, compiler, corriger les éventuelles erreurs.**

## Ce qui a été fait

| Élément | Fichiers | État |
|---|---|---|
| Projet Gradle (AGP/Gradle repris de l'ancienne app archivée), thème Material 3 bleu/orange JIRAMA | `mobile/build.gradle`, `settings.gradle`, `app/build.gradle`, `values/themes.xml`, `colors.xml` | écrit, non compilé |
| Textes français centralisés | `res/values/strings.xml` | écrit |
| Client API unique (HttpURLConnection + threads, callbacks sur thread principal, Bearer, format d'erreur du contrat) | `api/ApiClient.java`, `ApiException.java` | écrit, non testé |
| Session (jeton, expiration, rôle, URL serveur, équipement, cache du dernier état) | `auth/SessionManager.java` | écrit |
| Modèles : état appareil (champs inconnus = `null`, jamais `false`), événement/alerte, commande | `model/*` | écrit |
| Connexion (bouton désactivé pendant la requête, erreurs lisibles, URL serveur éditable en debug seulement, préchargement des équipements) | `ui/login/LoginActivity.java` | écrit, non testé |
| Navigation basse : Vue d'ensemble / Commandes / Événements / Paramètres | `ui/MainActivity.java` | écrit |
| Vue d'ensemble : connexion + dernière communication, état global, temps restant (mesure valide et récente seulement), JIRAMA / UPS / GE / ATS / sécurités, alertes ouvertes, commande en cours, derniers événements, rafraîchissement auto 10 s | `ui/dashboard/DashboardFragment.java` | écrit, non testé |
| Commandes : 7 actions, confirmation, `request_id` UUID idempotent (réutilisé après échec réseau), suivi par polling `GET /commands/{id}`, « Terminée » seulement sur `SUCCEEDED`, historique | `ui/commands/CommandsFragment.java` | écrit, non testé |
| Événements : alertes ouvertes + acquittement, journal, filtre par sévérité | `ui/events/EventsFragment.java` | écrit, non testé |
| Paramètres : compte, rôle, serveur, infos équipement (firmware, dernière communication), section ADMIN (squelette), déconnexion | `ui/settings/SettingsFragment.java` | écrit, non testé |
| Tests unitaires JVM (`Dates`, `DeviceStatus` : inconnu ≠ false, fraîcheur, temps restant masqué si périmé) | `app/src/test/...` | écrits, **non exécutés** (voir ci-dessus ; `Dates` vérifié à la main) |

## Règles du contrat respectées

- Aucun secret d'appareil/backend dans l'APK ; HTTP clair autorisé uniquement en build `debug`, HTTPS requis en `release`.
- Un état inconnu (`null`) s'affiche « Inconnu », pas « Absent/Faux ».
- Fraîcheur : état « périmé » si le serveur le signale, si l'appareil n'est pas `ONLINE`, ou si `captured_at` a plus de 120 s (`DeviceStatus.STALE_AFTER_MS`). Les couleurs d'état sont alors atténuées et le temps restant masqué.
- Hors ligne : dernier état en cache affiché avec le bandeau « Hors ligne — données en cache (anciennes) » ; commandes désactivées.
- Commandes désactivées si : rôle LECTURE, données périmées/hors ligne, arrêt d'urgence, sélecteur MANUEL, état des sécurités inconnu, ou commande déjà en cours. C'est un confort d'interface : le backend reste l'autorité (403/409 gérés et affichés).
- HTTP 202 n'est jamais présenté comme « exécuté ».
- 401 hors connexion → session effacée, retour à l'écran de connexion avec « Session expirée ».
- Palette : contrastes calculés (WCAG) — texte principal 14,6:1 ; secondaire 4,97 ; vert OK 4,78 ; avertissement 5,43 ; critique 6,57 sur les cartes blanches. **Limite** : le vert « OK » sur le fond gris général `#F4F7FA` fait 4,45:1 (< 4,5) ; il n'est utilisé que sur les cartes blanches.

## Hypothèses à confirmer avec le backend FastAPI (le contrat ne les fixe pas)

1. `GET /devices`, `/events`, `/commands`, `/alerts` : tableau JSON à la racine **ou** `{"items":[...]}` — les deux sont acceptés.
2. Noms de champs d'un événement/alerte : `id`/`alert_id`, `severity`, `type`, `source`, `message`, date dans `occurred_at` / `created_at` / `timestamp`.
3. Message de refus d'une commande lu dans `reject_reason`, sinon `result_message`, sinon `message`.
4. Codes d'état global : `NORMAL, JIRAMA_OUTAGE, WAITING, GE_STARTING, GE_STABLE, TRANSFERRING, GE_POWER, RETURNING, FAULT, MANUAL_LOCKOUT` (+ quelques alias). Un code inconnu est affiché tel quel. À aligner avec `Labels.state()`.
5. `ats_position` attendu : `JIRAMA` ou `GE` ; toute autre valeur est signalée « à vérifier ».
6. `GET /devices/{id}` : `name`, `firmware_version`, `last_seen_at` (ou `connection.last_seen_at`).
7. `POST /alerts/{id}/ack` accepte un corps JSON vide `{}`.
8. Pas de route de déconnexion serveur dans le contrat : la déconnexion efface seulement la session locale.

## Reste à faire

**Avant toute démonstration**
- [ ] Ouvrir dans Android Studio, synchroniser, compiler, corriger.
- [ ] Exécuter `./gradlew test` (tests unitaires) puis tester sur émulateur contre FastAPI (`http://10.0.2.2:8000/api/v1`).
- [ ] Vérifier petits écrans, texte agrandi, rotation, coupure réseau (les 4 cas demandés par `mobile/README.md`).
- [ ] Remplacer l'URL `release` (`https://stella.example.invalid/api/v1`) dans `app/build.gradle`.

**P1 (non fait)**
- [ ] Gestion des utilisateurs (ADMIN) et configuration des temporisations : seul un encart « à venir » existe.
- [ ] Pagination « charger plus » des événements/commandes (le curseur `before` n'est pas défini dans le contrat ; limite fixe 50/20).
- [ ] Filtres par période ; filtre par appareil (un seul équipement géré : le premier de `/devices`).
- [ ] Notifications Android, thème sombre, export CSV.
- [ ] Stockage chiffré du jeton (EncryptedSharedPreferences / Keystore) ; actuellement SharedPreferences privé, `allowBackup=false`.
- [ ] Contraste du vert OK sur fond gris, vérification TalkBack.

## Sécurité — jeton GitHub

Un jeton d'accès personnel GitHub a été collé en clair dans une conversation pour pousser ce travail. Il a servi uniquement à cloner et pousser la branche `feature/mobile-android` ; il n'est écrit dans aucun fichier du dépôt. **À révoquer dans GitHub → Settings → Developer settings → Personal access tokens**, même s'il est « temporaire ».

# Conception mobile Android Java

## Palette inspirée de JIRAMA

Le logo présent sur le [site officiel JIRAMA](https://www.jirama.mg/la-jirama/) associe un emblème orange, un symbole blanc et une typographie bleue. Les hexadécimaux proposés sont des **approximations visuelles pour démarrer**, pas une charte officielle publiée.

| Jeton | Valeur initiale | Usage |
|---|---|---|
| brand.blue | #2376A8 | barre supérieure, navigation, liens |
| brand.orange | #F59B23 | accent énergie et actions mises en avant |
| brand.white | #FFFFFF | surfaces et texte sur fond coloré |
| surface | #F4F7FA | fond général |
| text.primary | #172B3A | texte principal |
| text.secondary | #667085 | métadonnées |
| status.ok | #17834D | normal confirmé |
| status.warning | #B54708 | attention ou incertitude |
| status.critical | #B42318 | défaut ou arrêt d'urgence |
| border | #D0D7DE | séparateurs |

Le bleu porte l'identité générale ; l'orange reste un accent. Les couleurs d'état sont indépendantes des couleurs de marque. Toujours accompagner une couleur d'un texte ou d'une icône.

## Principes d'interface

- Afficher d'abord alimentation, connexion et alertes critiques.
- Distinguer commande demandée, commande acceptée et état physique observé.
- Indiquer l'heure de mesure ; les données anciennes portent « Données périmées ».
- Demander confirmation avant une commande physique.
- Cibles tactiles proches de 48 dp, texte lisible et contrastes vérifiés.
- Version initiale en français ; textes centralisés pour traduction future.
- Utiliser les composants Android/Material compatibles avec le projet.

## Navigation

Barre basse : Vue d'ensemble, Commandes, Événements, Paramètres. L'administration utilisateurs et configuration détaillée sont réservées à ADMIN.

## Connexion

- Nom de l'application et repère graphique bleu/orange.
- Identifiant, mot de passe, visibilité du mot de passe et erreurs compréhensibles.
- URL serveur configurable en développement seulement.
- Bouton désactivé pendant la requête.
- Après connexion, charger le profil puis la liste des équipements.

## Vue d'ensemble

Ordre d'affichage :
1. État de connexion backend et dernière mise à jour.
2. Carte alimentation : JIRAMA, UPS, GE, ATS.
3. État global textuel : Normal, Coupure JIRAMA, En attente, Démarrage GE, GE stable, Transfert en cours, Alimentation GE, Retour JIRAMA, Défaut, Mode manuel.
4. Temps restant uniquement s'il vient d'une mesure valide du contrôleur.
5. Alertes ouvertes et commande en cours.
6. Derniers événements.

La présence d'une commande ne permet pas d'afficher le GE comme actif ; il faut un état confirmé transmis par le contrôleur.

## Commandes

- Démarrer GE, arrêter GE, transférer vers GE, retour JIRAMA, acquittement de défaut, changement de mode.
- Confirmation décrivant action et conséquences.
- Progression : En attente, Livrée, Acceptée, En cours, Terminée, Refusée, Échec, Expirée.
- Cause de refus visible : mode manuel, état incompatible, capteur inconnu, ATS non confirmé.
- Protection contre les doubles clics avec request_id idempotent.
- Ne jamais afficher Terminé sur la seule réponse 202.

## Événements et alertes

- Chronologie date/heure, source, sévérité, type et message.
- Filtres par appareil, période et sévérité.
- Acquittement d'alerte distinct de la réinitialisation d'un défaut physique.
- États vides et pertes réseau explicites.

## Paramètres et administration

- Temporisations autorisées avec unités visibles.
- Dernière communication, version firmware et état des entrées connues.
- Gestion des utilisateurs uniquement pour ADMIN.
- Confirmation des modifications et journalisation serveur.
- Paramètres électriques non validés masqués jusqu'à validation par la responsable électronique.

## États d'interface à traiter

Chargement, absence de données, erreur réseau, session expirée, appareil hors ligne, données périmées, capteur inconnu, configuration non synchronisée, commande en cours ou échouée, mode manuel, arrêt d'urgence, défaut GE et position ATS incohérente.

En mode hors ligne, les données mises en cache sont étiquetées comme anciennes ; aucune commande ne doit être présentée comme envoyée.

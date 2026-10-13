# Décisions, correspondances et limites

## Références prises en compte

1. Conception fonctionnelle récente, sauvegardée sous Archive/avant-reorganisation/Doc/mm_projet_commande_GE_v2.md.
2. Conception logicielle de [ProjetStella/ge-controller-esp32](https://github.com/ProjetStella/ge-controller-esp32), notamment analyse, architecture, API et signaux.
3. Sketch actuel recopié sans modification sous Electronique/reference/DetectionJIRAMA2.ino.

La conception fonctionnelle décrit le comportement cible ; les documents du contrôleur décrivent une machine à états et des interfaces ; le sketch actuel ne montre qu'une première réalisation partielle.

## Capacités visibles du sketch actuel

- Détection présence JIRAMA via PC817 sur GPIO 32 avec filtrage logiciel.
- Une sortie relais sur GPIO 27.
- Ethernet via ENC28J60 et requêtes HTTP sortantes.
- Synchronisation de l'heure sur l'ancienne route /api/esp32/time.
- Envoi de present et capturedAt vers /api/esp32/{device_id}/lignefixe.
- File mémoire de changements de ligne de capacité 20.
- MODE_TEST = true : délai actif 5 secondes ; délai de production défini dans le sketch : 45 minutes.

Le sketch ne montre pas la détection tension GE stable, le signal batterie UPS faible, les retours de position ATS, les commandes démarrage/arrêt GE ou les trois modes complets. Ne pas présenter ces fonctions comme déjà opérationnelles.

## Incompatibilités connues

- Ancien backend Java/Spring avec stockage SQLite sauvegardé sous Archive/avant-reorganisation/serveurmen2-modifie (3)/.
- Ancienne application Android sauvegardée sous Archive/avant-reorganisation/Interface/.
- Les anciennes routes /api/esp32/time et /api/esp32/{id}/lignefixe ne correspondent pas à la nouvelle API versionnée /api/v1.
- Aucune migration SQLite vers PostgreSQL n'est implicite.
- Le nouveau contrat ajoute heartbeat, événements, polling de commandes et accusés de réception.

Cette documentation décrit la cible, pas une compatibilité déjà implémentée.

## Décisions fonctionnelles

- Délai d'attente : autonomie réelle mesurée UPS moins marge de sécurité.
- Déclenchement automatique : délai écoulé ou batterie faible, selon le signal disponible.
- Confirmer la détection puis la stabilité GE avant transfert.
- Attendre une JIRAMA stable avant le retour.
- Distinguer commande envoyée, acceptée, action physique et résultat confirmé.
- Automatismes et sécurités locaux même en cas de panne réseau/backend.

## Responsabilités

La responsable électronique valide matériel, schéma, protections, interfaces isolées, polarités, GPIO, protocole GE, commandes ATS et retours physiques. Elle adapte le firmware au contrat ou propose une évolution documentée. La partie logicielle simule les entrées/sorties sans supposer les branchements.

## Questions à confirmer par la responsable électronique

- Quelle autonomie UPS mesurée à la charge réelle et quelle marge ?
- La commande démarrage GE est-elle une impulsion ou un contact maintenu ?
- Quels signaux confirment le moteur, la tension disponible et les défauts GE ?
- Le modèle ATS accepte-t-il impulsion ou contact maintenu, et quels retours fournit-il ?
- Existe-t-il une sortie batterie faible exploitable sur l'UPS ?
- Quels éléments du sketch actuel restent dans le montage final ?

## Secrets dans les anciens fichiers

Le sketch historique contient des paramètres réseau et une valeur de clé codée en dur (l'envoi de l'en-tête correspondant est actuellement commenté). L'archive les conserve pour traçabilité. Avant mise en service, renouveler toute clé partagée ; ne jamais commiter de secret réel.

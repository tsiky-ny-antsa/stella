## Préparation de la partie électronique et de la logique

Méthode de travail (mmm.pdf) intégrant les décisions prises : délai basé sur l'onduleur,

GE_STABLE, batterie faible, 3 modes

## JIRAMA / GE / ESP32 : version mise à jour

## 0. Ce qui change par rapport à la première version

| Sujet | Avant | Maintenant |
| --- | --- | --- |
| Temporisation | Fixe : 2 minutes | Délai configurable basé sur l'autonomie de l'onduleur (2 min |
|   |   | conservées uniquement comme valeur de test sur maquette) |
| Déclencheur auto Fin des 2 min |   | Délai écoulé OU signal « batterie faible » de l'onduleur (le |
|   |   | premier des deux) |
| Confirmation du | GE_RUNNING direct | GE_DETECTED (tension présente) puis GE_STABLE (tension |
| GE |   | continue 5 à 10 s) |
| Modes de | Peu détaillés | Manuel total, automatique, à distance (application) |
| commande |   |   |
| Information | Une seule alerte | Alerte de coupure puis rappels réguliers avec temps restant |
| opérateur |   |   |
| Échec de | Simple FAULT | Plusieurs tentatives (2 à 3), puis alerte « ÉCHEC DÉMARRAGE GE |
| démarrage |   | » |
| Autonomie réseau Mentionnée |   | Logique automatique 100 % locale sur l'ESP32 |

## 1. Objectif et règle principale

Avant de programmer l'ESP32, il faut définir précisément la partie électronique : ce que l'ESP32 détecte, ce qu'il commande, quels composants servent d'interface, quels signaux et tensions sont présents, comment vérifier qu'une commande a réellement été exécutée, quelles protections existent et quels états le programme doit gérer.

Règle principale : on définit d'abord le matériel et les signaux électriques, puis les interfaces avec l'ESP32, puis les GPIO, et seulement après la logique et le code.

## 2. Architecture générale

Le projet comporte trois parties logicielles (application, backend/API, ESP32) et une partie électronique et électrique.


- Le datacenter est toujours alimenté par l'onduleur. La JIRAMA ou le GE alimentent l'entrée de l'onduleur via l'ATS.

- Quand le GE tourne, il recharge l'onduleur (ligne temporaire).

- Une micro-coupure pendant la bascule de l'ATS n'affecte pas les serveurs, car la batterie prend le relais.

## 3. Rôle de l'ESP32

L'ESP32 est le contrôleur local. Il n'est jamais branché directement sur la puissance : il travaille avec des interfaces.

- détecter l'état de la JIRAMA et du GE ;

- connaître l'état de l'ATS et, si possible, de l'onduleur (batterie faible) ;

- recevoir des commandes manuelles (boutons) et à distance (application) ;

- commander le démarrage et l'arrêt du GE, et le transfert de l'ATS ;

- communiquer avec le backend via Ethernet.

Informations électriques → Interfaces électroniques → ESP32 → Interfaces de commande → Équipements

## 4. Les trois modes de commande du GE

| Mode | Description |
| --- | --- |
| Manuel total | Commande directe sur site, sans dépendre de l'ESP32 ni du réseau, utile s'il n'y a |
|   | personne pour intervenir à distance ou en cas de défaillance du système. |
| Automatique | L'ESP32 démarre le GE lorsque le délai est écoulé ou que la batterie de l'onduleur est |
|   | faible. |
| À distance | L'opérateur démarre le GE depuis l'application mobile, via le backend. |

## 5. Entrées et sorties de l'ESP32

## Entrées (ce que l'ESP32 observe)

| Nom logique | Fonction | Interface possible |
| --- | --- | --- |
| JIRAMA_PRESENT | Présence de la JIRAMA | PC817 / interface isolée |
| GE_DETECTED / GE_STABLE Tension du GE présente / stable |   | PC817 / interface isolée (logique dans |
|   |   | l'ESP32) |
| UPS_LOW_BATTERY | Batterie faible de l'onduleur | Contact sec ou port de communication, via |
| (nouveau) |   | optocoupleur |
| ATS_ON_JIRAMA / | Position réelle de l'ATS | Contacts auxiliaires |
| ATS_ON_GE |   |   |
| MANUAL_START / | Boutons locaux | GPIO |
| MANUAL_STOP |   |   |
| GE_FAULT | Défaut signalé par le GE | Contact / interface |
| EMERGENCY_STOP | Arrêt d'urgence | Circuit dédié |


## Sorties (ce que l'ESP32 commande)

| Nom logique | Fonction | Interface possible |
| --- | --- | --- |
| START_GE | Demande de démarrage | Driver + relais / interface |
| STOP_GE | Demande d'arrêt | Driver + relais / interface |
| TRANSFER_TO_GE | Commande ATS vers GE | Relais / interface ATS |
| TRANSFER_TO_JIRAMA | Retour vers JIRAMA | Relais / interface ATS |
| ALARM | Alarme locale | Driver / buzzer / voyant |

Les noms sont des exemples, à adapter au matériel réel. Le tableau GPIO vient seulement après la définition complète des signaux.

## 6. Détection de la JIRAMA avec le PC817

Objectif : permettre à l'ESP32 de répondre à « La JIRAMA est-elle présente ? » (JIRAMA_PRESENT = true/false).

Signal JIRAMA → Circuit d'interface / adaptation → PC817 → GPIO ESP32

| À préciser | Question |
| --- | --- |
| Signal surveillé / origine | Que détecte-t-on réellement, et où est pris le signal ? |
| Tension, type, fréquence | Quelle tension ? AC ou DC ? 50 Hz si alternatif ? |
| Interface avant le PC817 | Quel circuit d'adaptation et quelle isolation ? |
| Niveau logique | Que signifient 0 et 1 pour l'ESP32 ? |

- Ne jamais raccorder « 230 V → PC817 → ESP32 » sans circuit d'interface correctement dimensionné (résistance de forte valeur, diode en antiparallèle).

- Le signal étant alternatif, la sortie oscille à 50 Hz : filtrer (condensateur) ou détecter la fréquence.

- Ajouter un anti-rebond logiciel (10 à 30 s) pour ignorer les micro-coupures.

## 7. Détection du GE : que veut dire « GE actif » ?

| Information possible | Signification |
| --- | --- |
| START_REQUESTED | Une commande de démarrage a été envoyée |
| ENGINE_RUNNING | Le moteur tourne |
| GENERATOR_OUTPUT_PRESENT | Le GE produit une tension (GE_DETECTED) |
| GENERATOR_AVAILABLE | La tension est stable et peut alimenter la charge (GE_STABLE) |
| GENERATOR_FAULT | Le groupe signale un défaut |

Ces états ne sont pas équivalents. Dans votre projet, le deuxième PC817 mesure la présence de tension en sortie du GE, ce qui donne deux niveaux de confirmation :

- GE_DETECTED : la tension est présente (le PC817 change d'état).

- GE_STABLE : la tension est présente en continu pendant 5 à 10 s. C'est seulement à ce moment que « GE ACTIF » est envoyé à l'application et que la bascule ATS est autorisée.


## 8. Informations à rechercher dans la documentation

## Groupe électrogène

| Élément | À déterminer |
| --- | --- |
| Marque, modèle, contrôleur | Références exactes et schéma électrique |
| Remote Start | Entrée prévue pour le démarrage à distance (type, tension, impulsion ou |
|   | maintien, durée) |
| RUN / READY / AVAILABLE / FAULT Signaux disponibles, leur tension, contact sec / AC / DC, NO / NC |   |

## ATS

| Élément | À déterminer |
| --- | --- |
| Marque, modèle, tension, courant Caractéristiques nominales |   |
| Commandes | Transfert vers GE, retour vers JIRAMA, présence d'un mode automatique |
|   | propre |
| Retours et sécurité | Contacts auxiliaires (position réelle), interverrouillage empêchant une |
|   | mauvaise combinaison |

## Onduleur (nouveau)

| Élément | À déterminer |
| --- | --- |
| Modèle, puissance | Capacité et charge réelle du datacenter |
| Autonomie réelle | Mesurée à la charge actuelle, pas seulement la valeur nominale |
| Signal batterie faible | Contact sec (« Low Battery ») ou port USB / RS232 / SNMP disponible ? |
| Position de l'ATS | L'ATS alimente-t-il bien l'entrée de l'onduleur ? |

Partir des bornes et signaux réellement disponibles sur les équipements, et non choisir le câblage à l'avance.

## 9. Commande, relais et ATS

- L'ESP32 ne pilote jamais directement le moteur du GE ni la puissance : GPIO → driver → relais → entrée de commande.

- Relais à définir : tension et courant de la bobine, type de contact (NO / NC / COM), tension et courant commutés, isolation, protection (diode).

- Pour la puissance, utiliser un contacteur commandé par un relais intermédiaire.

- Verrouillage obligatoire : JIRAMA et GE ne doivent jamais être connectés en même temps, avec un court délai de transfert.

## 10. Distinguer commande et retour d'état

Envoyer START_GE ne signifie pas GE_RUNNING = true. De même, TRANSFER_TO_GE ne signifie pas ATS_SOURCE = GE. Le système doit vérifier le résultat.

Commande → Action → Détection → Confirmation → Nouvel état

```
START_GE → relais démarrage → PC817 GE (tension) → GE_STABLE → GE_RUNNING TRANSFER_TO_GE → relais ATS → contact auxiliaire → ATS_ON_GE → SUPPLY_GE (si la confirmation n'arrive pas dans le délai prévu → FAULT + alarme)
```

L'application doit donc distinguer « commande envoyée » et « GE réellement actif ».


## 11. Temporisation basée sur l'autonomie de l'onduleur

Le délai de 2 minutes est remplacé par un délai calculé à partir de l'autonomie de l'onduleur, sans attendre la fin de l'heure : l'autonomie réelle est inférieure à la nominale (charge, âge des batteries), le démarrage du GE prend du temps (plusieurs tentatives, stabilisation) et il faut laisser

le temps de recharger la batterie.

```
Délai d'attente (T_ATTENTE) = Autonomie réelle − Marge de sécurité Marge = durée max de démarrage (3 essais) + stabilisation + réserve (≈ 10 à 15 min)
Exemple : autonomie réelle 45 min, marge 15 min → démarrage auto après 30 min
```

- T_ATTENTE est un paramètre configurable (base de données ou application), pas une valeur codée en dur. • Pendant l'attente : rappels réguliers (toutes les 5 à 10 min) avec le temps restant. • Filet de sécurité : démarrage automatique si (T_ATTENTE écoulé) OU (UPS_LOW_BATTERY), selon le premier des deux. • Pour la soutenance : 2 min peuvent être utilisées comme valeur de test sur la maquette ; la

- logique est identique.

## 12. Machine à états

Cette machine à états devient la base du firmware. Elle tourne localement sur l'ESP32.

## 13. Scénarios

JIRAMA présente, GE arrêté, ATS sur JIRAMA, onduleur rechargé : le système reste en NORMAL.

## 13.1 Normal

## 13.2 Coupure JIRAMA

PC817 JIRAMA change d'état → JIRAMA_PRESENT = false (confirmé après anti-rebond) → l'onduleur passe sur batterie → ESP32 envoie JIRAMA_OFF au backend → notification de coupure dans l'application

→ début de T_ATTENTE avec rappels

## 13.3 L'opérateur démarre le GE pendant l'attente

```
Coupure → attente → opérateur demande START (appli ou sur site) → ESP32 commande le GE (ou détecte le démarrage manuel) → PC817 GE détecte la tension → GE_STABLE → "GE ACTIF" envoyé (ESP32 → serveur → application) → notification "GE déjà actif", démarrage automatique annulé
→ transfert ATS vers GE
```


## 13.4 Personne n'intervient

Coupure → T_ATTENTE écoulé (ou batterie faible) → mode automatique → démarrage GE → vérification GE_STABLE → "GE ACTIF" → transfert ATS

Éviter START_GE → TRANSFER_TO_GE sans vérification. Préférer : démarrer, vérifier que le GE est réellement stable, demander le transfert, vérifier que l'ATS confirme. Sinon : défaut et alarme.

## 13.5 Ne jamais transférer vers un GE non confirmé

## 13.6 Retour de la JIRAMA

JIRAMA revient → détection → attente de stabilité (1 à 5 min) → demande de retour ATS

- ATS confirme JIRAMA → refroidissement du GE → arrêt du GE

- l'onduleur se recharge sur la JIRAMA

Cette temporisation de stabilité évite des basculements répétés si la JIRAMA est instable.

## 14. Sécurité : le logiciel n'est pas la seule protection

- Protections du GE, de l'ATS, de l'alimentation, arrêt d'urgence, interverrouillage, protections contre surcharges et défauts.

- Le système informatique supervise et commande dans les limites prévues par les équipements.

- Internet ou backend indisponible : l'ESP32 continue localement (temporisation, démarrage, bascule) et les sécurités physiques restent actives.

- Redémarrage de l'ESP32 : restaurer un état sûr en relisant l'état réel des sources et de l'ATS.

## 15. Cas d'erreur à définir avant le code

| Situation | Réaction à définir |
| --- | --- |
| JIRAMA absente | Démarrer la temporisation, envoyer l'alerte |
| JIRAMA revient pendant l'attente | Retour à l'état normal |
| GE ne démarre pas | 2 à 3 tentatives, puis alerte « ÉCHEC DÉMARRAGE GE » |
| GE démarre mais tension instable | Rester en GE_DETECTED, ne pas transférer, défaut après délai |
|   | maximum |
| ATS ne confirme pas le transfert | Défaut et alarme |
| JIRAMA et GE détectés simultanément | État anormal à traiter (vérifier l'interverrouillage) |
| Aucune source disponible, batterie faible Alarme critique, tentatives de démarrage |   |
| Perte réseau | Fonctionnement local autonome, synchronisation au retour |
| Redémarrage ESP32 | Restaurer l'état sûr |
| Capteur incohérent | État de défaut |
| Commandes contradictoires (manuel / | Refuser ou prioriser selon des règles écrites (priorité au manuel sur |
| distance) | site) |
| Arrêt d'urgence | Arrêt selon le circuit de sécurité prévu |

## 16. Documentation à produire avant le code final

- Schéma bloc de l'ensemble.

- Schéma électronique avec les vrais composants, connexions et interfaces.

- Tableau des signaux : chaque entrée et sortie, sa logique 0 / 1 (attention aux montages en logique inversée).


- Tableau GPIO : associe les signaux logiques aux GPIO réellement utilisés, seulement après le schéma.

- Machine à états du firmware.

- Schéma de communication : Application ↔ Backend ↔ ESP32 ↔ interfaces.

Équipement → Signal → Interface → Type de signal → Entrée / sortie ESP32 → GPIO

## 17. Ordre de travail recommandé

| Étape | Action |
| --- | --- |
| 1 - 2 | Identifier les équipements (ESP32, ENC28J60, GE, ATS, onduleur, relais, PC817) et récupérer les |
|   | modèles exacts du GE, de l'ATS et de l'onduleur |
| 3 - 4 | Lire les documentations (REMOTE START, RUN, READY, FAULT, LOW BATTERY, TRANSFER, |
|   | contacts auxiliaires) et identifier chaque signal : origine, destination, tension, AC/DC, NO/NC, |
|   | fonction |
| 5 - 6 | Définir les interfaces (PC817, relais, drivers, protections, alimentation, connecteurs) puis faire le |
|   | schéma électronique |
| 7 - 8 | Définir les GPIO, puis la machine à états |
| 9 | Définir l'API de l'ESP32 : GET /status, POST /startge, POST /stopge, POST /transfer/ge, POST |
|   | /transfer/jirama |
| 10 - 12 | Développer le firmware, puis le backend, puis l'application |
| 13 | Tester, en commençant par une maquette basse tension |

## 18. Maquette de test basse tension

Avant les équipements réels, simuler les états avec des boutons / interrupteurs et des LED : JIRAMA présente ou coupée, GE arrêté ou actif, ATS sur JIRAMA ou GE, batterie faible. Cela permet de tester toute la logique du firmware (dont le délai, les rappels et le GE_STABLE) sans manipuler la puissance électrique.

Boutons / interrupteurs → ESP32 → LED JIRAMA, LED GE, LED ATS, relais de test

## 19. Ce qu'il ne faut surtout pas faire

| Mauvais ordre | Bon ordre |
| --- | --- |
| Choisir les GPIO → écrire le code → chercher comment | Connaître le GE et l'ATS → identifier leurs signaux → |
| brancher le GE | définir les interfaces → schéma électronique → GPIO → |
|   | états → API → firmware |


## 20. Les 9 questions à résoudre avant de coder

```
\# Question
1 Qu'est-ce qui est réellement détecté sur la JIRAMA ?
2 Qu'est-ce qui est réellement détecté sur le GE (tension, RUN, READY) ?
3 Quelle est la tension et le type de chaque signal ?
4 Quelle interface est utilisée entre le signal et le PC817 ?
5 Quelle entrée du GE permet le démarrage à distance ?
6 Comment confirmer que le GE fonctionne réellement et de façon stable ?
7 Comment commander l'ATS et connaître sa position réelle ?
8 L'onduleur fournit-il un contact ou un port « batterie faible » ? Quelle est son autonomie
réelle mesurée (pour fixer T_ATTENTE) ?
9 Que doit faire le système pour chaque situation normale, anormale ou de panne ?
```

Important : pour le secteur, le groupe électrogène et l'ATS, le câblage réel doit être défini à partir des schémas constructeurs et validé par une personne qualifiée. Les exemples de ce document décrivent l'architecture logique et ne constituent pas un schéma de raccordement électrique.

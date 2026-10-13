# Architecture cible

## Choix pragmatiques

- Android Java : interface opérateur.
- FastAPI / Python : API REST et règles métier.
- PostgreSQL : stockage durable.
- ESP32 : automatismes et protections locales.
- Simulateur logiciel : tests sans matériel physique.

Pas de microservices, MQTT, WebSocket ou broker dans la première version. HTTP + JSON suffit pour la portée prévue.

## Vue d'ensemble

~~~mermaid
flowchart TD
    A[Application Android Java] <-->|HTTPS JSON / JWT| B[FastAPI]
    B <--> C[(PostgreSQL)]
    D[ESP32 / firmware] -->|heartbeat et événements| B
    D -->|polling des commandes| B
    E[Simulateur logiciel] -->|mêmes états et événements| B
    B -->|état, alertes, historique| A
~~~

L'ESP32 initie les requêtes sortantes vers FastAPI. Le backend n'ouvre pas de connexion vers l'ESP32 ; cela évite de dépendre de l'adresse IP de l'appareil ou d'un accès entrant à son réseau.

## Responsabilités

### FastAPI
- authentifier l'utilisateur et le contrôleur ;
- vérifier les permissions ;
- persister états, commandes, événements, alertes et audit ;
- exposer un contrat stable pour mobile et firmware ;
- simuler les équipements en développement.

### ESP32 / électronique
- lire et filtrer les entrées physiques ;
- garder l'automatisme local disponible sans réseau ;
- donner priorité au manuel et aux sécurités ;
- vérifier chaque commande sur l'état physique courant ;
- rapporter le résultat physique réel.

### Android
- afficher état, connexion et fraîcheur des données ;
- soumettre des commandes et en suivre la progression ;
- ne jamais remplacer les contrôles de sécurité par ceux de l'interface.

## Séquence d'une commande distante

1. L'opérateur soumet une commande.
2. FastAPI vérifie le droit et l'état connu, puis crée une commande QUEUED.
3. L'application reçoit 202 Accepted avec command_id : demande enregistrée, pas encore exécutée.
4. L'ESP32 interroge /devices/{device_id}/commands/next toutes les 3–5 secondes.
5. Le firmware revérifie les sécurités et acquitte ACCEPTED ou REJECTED.
6. Après vérification physique, le contrôleur rapporte SUCCEEDED ou FAILED.
7. FastAPI enregistre le résultat et l'application actualise l'affichage.

## Fraîcheur et disponibilité

- Heartbeat cible toutes les 30 secondes.
- Appareil hors ligne après 90 secondes sans heartbeat.
- Le mobile affiche l'heure de dernière mesure et signale les données périmées.
- En cas de perte réseau, aucune commande distante nouvelle ne peut être reçue ; les automatismes et sécurités locales ne dépendent pas du backend.

Les valeurs ci-dessus sont des valeurs de configuration initiales, pas des paramètres électriques validés.

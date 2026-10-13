# Archive des sources antérieures

Le dossier Archive/avant-reorganisation/ conserve une copie de tous les fichiers suivis dans le dépôt avant la réorganisation, avec leurs chemins relatifs. Rien n'est supprimé de l'historique Git.

Contenu sauvegardé :
- Doc/ : conception fonctionnelle antérieure.
- Electronique/ : sketch de détection JIRAMA.
- Interface/ : ancienne application Android Java, contrats et fichiers associés.
- serveurmen2-modifie (3)/ : ancien backend Java/Spring et fichier SQLite contenu dans le projet.

Ces fichiers sont des références historiques, pas l'implémentation active de la nouvelle cible. Le backend cible est sous FastAPI/ et utilisera PostgreSQL. Un éventuel import de l'ancienne base SQLite devra être explicite et testé.

**Sécurité :** avant tout partage public ou mise en service, vérifier les anciennes configurations et renouveler toute clé codée en dur. Ne jamais copier de vrais secrets dans la nouvelle documentation ou dans FastAPI/.env.example.

Le sketch d'origine est également recopié sans modification dans Electronique/reference/DetectionJIRAMA2.ino.

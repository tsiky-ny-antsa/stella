# Organisation de l'application

Créer ici le code FastAPI défini dans le README parent.

- api/v1/routes/ : routes HTTP et validation d'entrée.
- schemas/ : modèles Pydantic de requête et réponse.
- services/ : authentification, état, commandes, alertes et simulateur.
- repositories/ : accès SQLAlchemy.
- db/models.py et db/session.py : modèles et session PostgreSQL.
- core/ : configuration et sécurité.

Ne jamais exécuter une commande physique directement depuis un endpoint. L'endpoint crée une commande en base ; le contrôleur la récupère puis vérifie localement les conditions de sécurité.

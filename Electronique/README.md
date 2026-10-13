# Électronique et firmware

- [Sketch de référence inchangé](reference/DetectionJIRAMA2.ino)
- [Contrat API ESP32 ↔ FastAPI](../Doc/API_FASTAPI_ELECTRONIQUE.md)
- [Décisions et limites](../Doc/DECISIONS_ET_LIMITES.md)

La responsable électronique adapte les interfaces physiques et le firmware au matériel réellement choisi, tout en respectant le contrat d'états et de communication validé. Ce dossier ne fixe aucun nouveau câblage ou GPIO du GE, de l'ATS ou de l'UPS.

La cible attend, lorsque le matériel les fournit : présence JIRAMA, présence et stabilité GE, position ATS, défaut GE, arrêt d'urgence, mode manuel et éventuellement batterie UPS faible. L'appareil envoie ses états au backend, récupère les commandes et retourne des résultats confirmés.

Automatismes critiques, mode manuel, arrêt d'urgence et interverrouillage doivent rester locaux. Aucun branchement de puissance n'est défini ici.

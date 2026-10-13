package com.projet.serveurmen2.config;

public class ApiConstants {
    private ApiConstants() {

    }

    // Routes
    public static final String BASE_ESP32 = "/api/esp32";
    public static final String ROUTE_TIME = "/time";
    public static final String ROUTE_LIGNE_FIXE = "/{deviceId}/lignefixe";
    public static final String ROUTE_LIGNE_FIXE_LATEST = ROUTE_LIGNE_FIXE + "/latest";

    // Routes ESP32 : suivi de l'alimentation (declaration de la source, alertes,
    // carburant du groupe electrogene, configuration centralisee)
    public static final String ROUTE_ESP_SOURCE = "/{deviceId}/source";
    public static final String ROUTE_ESP_ALERTES = "/{deviceId}/alertes";
    public static final String ROUTE_ESP_CARBURANT = "/{deviceId}/carburant";
    public static final String ROUTE_ESP_CONFIG = "/{deviceId}/config";

    // Routes clients (consultation des etats)
    public static final String BASE_ETATS = "/api/etats";
    // Desactive : hors cahier des charges (iteration future) : public static final String ROUTE_APPAREILS = "/appareils";
    public static final String ROUTE_ETAT_ACTUEL = "/{deviceId}/actuel";
    public static final String ROUTE_HISTORIQUE = "/{deviceId}/historique";
    // Desactive : hors cahier des charges (iteration future) : public static final String ROUTE_STATS = "/{deviceId}/stats";

    // Routes clients : suivi de l'alimentation (sources, alertes, carburant)
    public static final String ROUTE_ALIMENTATION = "/{deviceId}/alimentation";
    public static final String ROUTE_ALIMENTATION_HISTORIQUE = ROUTE_ALIMENTATION + "/historique";
    public static final String ROUTE_ALERTES = "/{deviceId}/alertes";
    public static final String ROUTE_CARBURANT = "/{deviceId}/carburant";

    // Routes d'authentification (login des clients)
    public static final String BASE_AUTH = "/api/auth";
    // Desactive (comptes crees par l'ADMIN via /api/users) : public static final String ROUTE_REGISTER = "/register";
    public static final String ROUTE_LOGIN = "/login";
    public static final String ROUTE_ME = "/me";

    // Gestion des utilisateurs (reservee ADMIN)
    public static final String BASE_USERS = "/api/users";

    // (Supprime) Ancienne route /api/admins : le controleur Admin legacy a ete
    // retire — remplace par la gestion des utilisateurs via /api/users (ADMIN).

    //Id appareils
    public static final int DEVICE_ID_LONGUEUR_MAX = 64;
    public static final String DEVICE_ID_REGEX = "[A-Za-z0-9_-]{1,"+ DEVICE_ID_LONGUEUR_MAX + "}";

    //Declaration de source : motif de transition envoye par l'ESP32 (champ libre)
    public static final int MOTIF_LONGUEUR_MAX = 64;

    //Alertes envoyees par l'ESP32 (types libres, extensible : FORCAGE_... dans une iteration future)
    public static final int TYPE_ALERTE_LONGUEUR_MAX = 64;
    public static final int MESSAGE_ALERTE_LONGUEUR_MAX = 255;
    public static final int DETAILS_ALERTE_LONGUEUR_MAX = 1024;
    // Types d'alertes connus du serveur (pour l'auto-resolution)
    public static final String TYPE_SECOURS_INOPERATIONNEL = "SECOURS_INOPERATIONNEL";
    public static final String TYPE_CARBURANT_BAS = "CARBURANT_BAS";

    //Historique
    public static final String LIMITE_PAR_DEFAUT_TEXTE = "50";
    public static final int LIMITE_MAX = 500;

    //Pagination cote clients
    public static final String TAILLE_PAGE_PAR_DEFAUT_TEXTE = "50";
    public static final int TAILLE_PAGE_MAX = 500;

    //Nombre maximal de coupures renvoyees dans les statistiques
    // Desactive : hors cahier des charges (iteration future, route stats) :
    // public static final int COUPURES_MAX_RENVOYEES = 100;

    //Horodotage
    public static final long TOLERANCE_FUTUR_MINUTES = 5;

    //Messages d'erreur
    public static final String CLE_ERREUR = "erreur";
    public static final String ERREUR_REQUETE = "requete_invalide";
}

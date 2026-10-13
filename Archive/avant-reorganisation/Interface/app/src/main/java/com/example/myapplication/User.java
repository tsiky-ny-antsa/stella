package com.example.myapplication;

/**
 * Modèle utilisateur avec rôle.
 * Rôles du SERVEUR "serveurmen2" (trois niveaux, décision équipe client) :
 *   ADMIN       : accès complet — crée tous les rôles et supprime des comptes ;
 *   RESPONSABLE : supervision — consultation + gestion partielle des comptes
 *                 (lister, créer des USER et RESPONSABLE ; ni ADMIN ni suppression) ;
 *   USER        : client standard, CONSULTATION SEULE (lecture seule).
 * (Remarque historique : le rôle "OBSERVATEUR" des premières itérations de
 * démonstration n'existe pas côté serveur — c'est USER qui le remplace.)
 */
public class User {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_RESPONSABLE = "RESPONSABLE";
    /** Client standard (consultation seule) — code renvoyé par le serveur. */
    public static final String ROLE_USER = "USER";

    public final String username;
    public final String email;
    public final String password;
    public final String role;
    public final String fullName;

    public User(String username, String email, String password, String role, String fullName) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
        this.fullName = fullName;
    }

    /** True si le rôle est ADMIN (tous droits). */
    public static boolean estAdmin(String role) {
        return ROLE_ADMIN.equals(role);
    }

    /** True si le rôle est RESPONSABLE (supervision : consultation + comptes partiels). */
    public static boolean estResponsable(String role) {
        return ROLE_RESPONSABLE.equals(role);
    }

    /** True si le rôle permet de consulter les états (tous les rôles connectés). */
    public static boolean peutConsulter(String role) {
        return ROLE_ADMIN.equals(role) || ROLE_RESPONSABLE.equals(role) || ROLE_USER.equals(role);
    }

    /** True si le rôle permet d'ouvrir l'écran de gestion des comptes (ADMIN, RESPONSABLE). */
    public static boolean peutGererComptes(String role) {
        return ROLE_ADMIN.equals(role) || ROLE_RESPONSABLE.equals(role);
    }

    /** True si le rôle permet de supprimer des comptes (ADMIN uniquement). */
    public static boolean peutSupprimerComptes(String role) {
        return ROLE_ADMIN.equals(role);
    }
}

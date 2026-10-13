package com.example.myapplication;

import java.util.ArrayList;
import java.util.List;

/**
 * Annuaire des comptes + authentification.
 * Première itération : comptes de démonstration en mémoire.
 * TODO (prochaine itération) : base de données / mot de passe hashé.
 */
public class UserStore {

    private static final List<User> USERS = new ArrayList<>();

    static {
        USERS.add(new User("admin", "admin@energy.mg", "admin123",
                User.ROLE_ADMIN, "Administrateur système"));
        USERS.add(new User("responsable", "responsable@energy.mg", "resp123",
                User.ROLE_RESPONSABLE, "Responsable exploitation"));
        // Compte de démonstration aligné sur le serveur : USER = consultation seule
        USERS.add(new User("personnel", "personnel@energy.mg", "pers123",
                User.ROLE_USER, "Client (consultation seule)"));
    }

    /**
     * Vérifie la combinaison username + email + password.
     * @return l'utilisateur correspondant, ou null si identifiants invalides.
     */
    public static User authenticate(String username, String email, String password) {
        for (User u : USERS) {
            if (u.username.equalsIgnoreCase(username)
                    && u.email.equalsIgnoreCase(email)
                    && u.password.equals(password)) {
                return u;
            }
        }
        return null;
    }

    /**
     * Repli quand le serveur est éteint : identifiant unique = username OU email
     * (même comportement que POST /api/auth/login du serveur).
     */
    public static User authenticateParIdentifiant(String identifiant, String password) {
        for (User u : USERS) {
            if ((u.username.equalsIgnoreCase(identifiant)
                    || u.email.equalsIgnoreCase(identifiant))
                    && u.password.equals(password)) {
                return u;
            }
        }
        return null;
    }

    public static List<User> all() {
        return new ArrayList<>(USERS);
    }
}

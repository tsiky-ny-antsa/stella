package com.projet.serveurmen2.exception;

/** Levee quand le login ou le mot de passe est incorrect (-> 401). */
public class IdentifiantsIncorrectsException extends RuntimeException {
    public IdentifiantsIncorrectsException() {
        super("identifiant ou mot de passe incorrect");
    }
}

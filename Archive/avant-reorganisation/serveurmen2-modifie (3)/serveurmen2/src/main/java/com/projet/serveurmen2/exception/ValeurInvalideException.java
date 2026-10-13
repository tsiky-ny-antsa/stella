package com.projet.serveurmen2.exception;

/** Valeur envoyee par un client hors du domaine accepte (ex : source inconnue). */
public class ValeurInvalideException extends RuntimeException {
    public ValeurInvalideException(String message) {
        super(message);
    }
}

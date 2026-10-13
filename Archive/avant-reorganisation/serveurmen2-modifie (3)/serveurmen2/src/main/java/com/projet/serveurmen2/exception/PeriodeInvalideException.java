package com.projet.serveurmen2.exception;

/**
 * Levee quand la periode demandee par un client est incoherente
 * (ex : debut posterieur a fin).
 */
public class PeriodeInvalideException extends RuntimeException {
    public PeriodeInvalideException(String message) {
        super(message);
    }
}

package com.projet.serveurmen2.exception;

/** Levee quand un username ou un email est deja pris (-> 409). */
public class DoublonException extends RuntimeException {
    public DoublonException(String message) {
        super(message);
    }
}

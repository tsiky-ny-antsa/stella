package com.projet.serveurmen2.controller;

import com.projet.serveurmen2.exception.DoublonException;
import com.projet.serveurmen2.exception.PeriodeInvalideException;
import com.projet.serveurmen2.exception.IdentifiantsIncorrectsException;
import com.projet.serveurmen2.exception.ValeurInvalideException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.stream.Collectors;

import static com.projet.serveurmen2.config.ApiConstants.CLE_ERREUR;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> jsonIllisible(HttpMessageNotReadableException e) {
        log.warn("POST refuse - JSON illisible : {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest().body(Map.of(CLE_ERREUR, "json_illisible"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> champInvalide(MethodArgumentNotValidException e) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("POST refuse - validation du corps : {}", details);
        return ResponseEntity.badRequest().body(Map.of(CLE_ERREUR, "champ_invalide", "details", details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> parametreInvalide(ConstraintViolationException e) {
        String details = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("POST refuse - validation URL/parametres : {}", details);
        return ResponseEntity.badRequest().body(Map.of(CLE_ERREUR, "parametre_invalide", "details", details));
    }

    // Parametre de type incompatible (ex : date pas au format ISO-8601)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> typeIncompatible(MethodArgumentTypeMismatchException e) {
        String details = e.getName() + ": valeur '" + e.getValue() + "' invalide pour le type "
                + e.getRequiredType().getSimpleName();
        log.warn("Requete refusee - parametre de type incompatible : {}", details);
        return ResponseEntity.badRequest().body(Map.of(CLE_ERREUR, "parametre_invalide", "details", details));
    }

    // Periode incoherente (debut apres fin) sur les routes clients
    @ExceptionHandler(PeriodeInvalideException.class)
    public ResponseEntity<Map<String, String>> periodeInvalide(PeriodeInvalideException e) {
        log.warn("Requete refusee - periode invalide : {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of(CLE_ERREUR, "periode_invalide", "details", e.getMessage()));
    }

    // Valeur hors du domaine accepte (ex : source d'alimentation inconnue)
    @ExceptionHandler(ValeurInvalideException.class)
    public ResponseEntity<Map<String, String>> valeurInvalide(ValeurInvalideException e) {
        log.warn("Requete refusee - valeur invalide : {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of(CLE_ERREUR, "valeur_invalide", "details", e.getMessage()));
    }

    // Validation des parametres de methode (Spring 6.1+)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, String>> validationMethode(HandlerMethodValidationException e) {
        log.warn("Requete refusee - validation de methode : {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of(CLE_ERREUR, "parametre_invalide"));
    }

    // Login ou mot de passe incorrect
    @ExceptionHandler(IdentifiantsIncorrectsException.class)
    public ResponseEntity<Map<String, String>> identifiantsIncorrects(IdentifiantsIncorrectsException e) {
        log.warn("Login refuse : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(CLE_ERREUR, "identifiants_incorrects"));
    }

    // Username ou email deja pris (inscription / creation par un admin)
    @ExceptionHandler(DoublonException.class)
    public ResponseEntity<Map<String, String>> doublon(DoublonException e) {
        log.warn("Creation refusee - doublon : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(CLE_ERREUR, "doublon", "details", e.getMessage()));
    }
}
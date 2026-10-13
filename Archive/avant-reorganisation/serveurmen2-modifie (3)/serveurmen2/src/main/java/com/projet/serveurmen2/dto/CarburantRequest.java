package com.projet.serveurmen2.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Niveau de carburant du groupe electrogene mesure par l'ESP32.
 * niveauPourcent : 0 a 100 ; valeurBrute : lecture ADC optionnelle (0..4095)
 * pour permettre l'etalonnage cote serveur/client.
 */
public record CarburantRequest(
        @NotNull @DecimalMin(value = "0.0", message = "le niveau doit etre >= 0")
        @DecimalMax(value = "100.0", message = "le niveau doit etre <= 100")
        Double niveauPourcent,
        Integer valeurBrute,
        Instant capturedAt) {}

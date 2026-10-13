package com.projet.serveurmen2.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

import static com.projet.serveurmen2.config.ApiConstants.MESSAGE_ALERTE_LONGUEUR_MAX;
import static com.projet.serveurmen2.config.ApiConstants.TYPE_ALERTE_LONGUEUR_MAX;

/**
 * Alerte envoyee par l'ESP32 (ex : ligne de secours non operationnelle pendant
 * la fenetre de bascule, carburant bas). details : objet JSON libre.
 */
public record AlerteRequest(
        @NotBlank @Size(max = TYPE_ALERTE_LONGUEUR_MAX) String type,
        @Size(max = MESSAGE_ALERTE_LONGUEUR_MAX) String message,
        JsonNode details,
        Instant capturedAt) {}

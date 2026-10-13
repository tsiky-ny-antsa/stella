package com.projet.serveurmen2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

import static com.projet.serveurmen2.config.ApiConstants.MOTIF_LONGUEUR_MAX;

/**
 * Declaration par l'ESP32 de la source qui alimente l'appareil important.
 * source : LIGNE_FIXE | ONDULEUR | GROUPE_ELECTROGENE (insensible a la casse)
 * motif (optionnel) : DEMARRAGE, COUPURE_LIGNE_FIXE, BASCULE_SECOURS, RETOUR_LIGNE_FIXE...
 * capturedAt (optionnel) : horodatage ISO-8601 UTC mesure par l'ESP32 ; sinon l'heure serveur.
 */
public record SourceAlimentationRequest(
        @NotBlank @Size(max = 32) String source,
        @Size(max = MOTIF_LONGUEUR_MAX) String motif,
        Instant capturedAt) {}

package com.projet.serveurmen2.dto;

import tools.jackson.databind.JsonNode;
import com.projet.serveurmen2.model.AlerteSecours;

import java.time.Instant;

/**
 * Alerte restituee aux clients. details est renvoye tel que l'ESP32 l'a envoye
 * (objet JSON libre). active = false des que le serveur a resolu l'alerte
 * (auto-resolution : retour ligne fixe, bascule secours effectuee, carburant remonte).
 */
public record AlerteResponse(
        Long id,
        String deviceId,
        String type,
        String message,
        JsonNode details,
        Instant signaleeAt,
        Instant resolueAt,
        boolean active) {

    public static AlerteResponse from(AlerteSecours a, JsonNode details) {
        return new AlerteResponse(a.getId(), a.getDeviceId(), a.getType(), a.getMessage(),
                details, a.getSignaleeAt(), a.getResolueAt(), a.getResolueAt() == null);
    }
}

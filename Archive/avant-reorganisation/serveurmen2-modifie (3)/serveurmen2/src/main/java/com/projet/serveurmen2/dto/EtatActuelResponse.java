package com.projet.serveurmen2.dto;

import com.projet.serveurmen2.model.LigneFixeState;

import java.time.Duration;
import java.time.Instant;

/**
 * Etat present d'un appareil : dernier etat connu + anciennete de la mesure.
 * ageSecondes permet au client de juger la fraicheur de l'information
 * (ex : un etat vieux de plusieurs heures est probablement perime).
 */
public record EtatActuelResponse(
        String deviceId,
        boolean present,
        Instant capturedAt,
        Instant receivedAt,
        long ageSecondes) {

    public static EtatActuelResponse from(LigneFixeState s, Instant maintenant) {
        long age = Duration.between(s.getCapturedAt(), maintenant).getSeconds();
        if (age < 0) {
            age = 0; // horodatage legerement dans le futur (tolerance serveur)
        }
        return new EtatActuelResponse(s.getDeviceId(), s.isPresent(), s.getCapturedAt(), s.getReceivedAt(), age);
    }
}

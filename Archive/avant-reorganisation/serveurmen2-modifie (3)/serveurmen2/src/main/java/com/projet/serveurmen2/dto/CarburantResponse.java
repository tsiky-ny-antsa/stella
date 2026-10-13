package com.projet.serveurmen2.dto;

import com.projet.serveurmen2.model.MesureCarburant;

import java.time.Duration;
import java.time.Instant;

/**
 * Mesure de carburant du groupe electrogene restituee aux clients.
 * ageSecondes permet de juger la fraicheur de la mesure (dashboard).
 */
public record CarburantResponse(
        Long id,
        String deviceId,
        double niveauPourcent,
        Integer valeurBrute,
        Instant mesureAt,
        Instant receivedAt,
        Long ageSecondes) {

    public static CarburantResponse from(MesureCarburant m) {
        return new CarburantResponse(m.getId(), m.getDeviceId(), m.getNiveauPourcent(),
                m.getValeurBrute(), m.getMesureAt(), m.getReceivedAt(), null);
    }

    public static CarburantResponse from(MesureCarburant m, Instant maintenant) {
        long age = Duration.between(m.getMesureAt(), maintenant).getSeconds();
        if (age < 0) {
            age = 0; // horodatage legerement dans le futur (tolerance serveur)
        }
        return new CarburantResponse(m.getId(), m.getDeviceId(), m.getNiveauPourcent(),
                m.getValeurBrute(), m.getMesureAt(), m.getReceivedAt(), age);
    }
}

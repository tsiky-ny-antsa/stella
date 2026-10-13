package com.projet.serveurmen2.dto;

import com.projet.serveurmen2.model.AlimentationState;
import com.projet.serveurmen2.model.SourceAlimentation;

import java.time.Instant;

/**
 * Reponse au POST d'un changement de source. basculePrevue est renseignee
 * uniquement quand la source declaree est l'ONDULEUR (ligne temporaire) :
 * c'est l'instant ou, sans retour de la ligne fixe, la bascule vers la ligne
 * de secours doit se produire (capturedAt + delai de bascule du serveur).
 * L'ESP32 peut s'en servir pour verifier que son chrono local est aligne.
 */
public record SourceAlimentationResponse(
        Long id,
        String deviceId,
        String source,
        String motif,
        Instant capturedAt,
        Instant basculePrevue) {

    public static SourceAlimentationResponse from(AlimentationState s, Instant basculePrevue) {
        return new SourceAlimentationResponse(s.getId(), s.getDeviceId(), s.getSource().name(),
                s.getMotif(), s.getCapturedAt(), basculePrevue);
    }

    /** Calcule l'echeance de bascule si la source est l'onduleur, sinon null. */
    public static Instant echeanceBascule(SourceAlimentation source, Instant capturedAt, long delaiBasculeSecondes) {
        if (source == SourceAlimentation.ONDULEUR && capturedAt != null) {
            return capturedAt.plusSeconds(delaiBasculeSecondes);
        }
        return null;
    }
}

package com.projet.serveurmen2.dto;

import com.projet.serveurmen2.model.AlimentationState;

import java.time.Instant;

public record AlimentationStateResponse(
        Long id,
        String deviceId,
        String source,
        String motif,
        Instant capturedAt,
        Instant receivedAt) {

    public static AlimentationStateResponse from(AlimentationState s) {
        return new AlimentationStateResponse(s.getId(), s.getDeviceId(),
                s.getSource().name(), s.getMotif(), s.getCapturedAt(), s.getReceivedAt());
    }
}

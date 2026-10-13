package com.projet.serveurmen2.dto;

import com.projet.serveurmen2.model.LigneFixeState;

import java.time.Instant;

public record  LigneFixeResponse (Long id, String deviceId, boolean present,
                                 Instant capturedAt, Instant receivedAt) {
    public static LigneFixeResponse from(LigneFixeState s) {
        return new LigneFixeResponse(s.getId(), s.getDeviceId(), s.isPresent(),
                                     s.getCapturedAt(), s.getReceivedAt());
    }
}

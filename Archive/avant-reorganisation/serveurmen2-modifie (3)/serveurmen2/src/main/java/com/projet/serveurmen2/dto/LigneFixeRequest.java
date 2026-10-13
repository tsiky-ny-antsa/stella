package com.projet.serveurmen2.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record LigneFixeRequest (
        @NotNull Boolean present, Instant capturedAt
) {}

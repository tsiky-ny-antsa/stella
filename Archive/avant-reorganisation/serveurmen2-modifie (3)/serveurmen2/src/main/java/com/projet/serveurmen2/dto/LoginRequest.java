package com.projet.serveurmen2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps du POST /api/auth/login.
 * identifiant : le username OU l'email de l'utilisateur.
 */
public record LoginRequest(
        @NotBlank @Size(max = 128) String identifiant,
        @NotBlank @Size(max = 128) String password) {
}

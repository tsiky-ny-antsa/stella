package com.projet.serveurmen2.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corps du POST /api/users (creation d'un utilisateur par un ADMIN).
 * Contrairement a /api/auth/register, le role est choisi par l'admin
 * (ADMIN ou USER ; USER par defaut si absent).
 */
public record CreationUserRequest(
        @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String username,
        @NotBlank @Email @Size(max = 128) String email,
        @NotBlank @Size(min = 6, max = 128) String password,
        @Pattern(regexp = "ADMIN|USER", flags = Pattern.Flag.CASE_INSENSITIVE) String role) {

    /** Retourne le role demande, USER si absent. */
    public String roleOuDefaut() {
        return (role == null || role.isBlank()) ? "USER" : role.toUpperCase();
    }
}

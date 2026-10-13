package com.projet.serveurmen2.dto;

import com.projet.serveurmen2.model.User;

/**
 * Representation publique d'un utilisateur : le mot de passe (hache) n'est
 * JAMAIS inclus dans les reponses de l'API.
 */
public record UserResponse(Long id, String username, String email, String role) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getEmail(),
                u.getRole() != null ? u.getRole().name() : null);
    }
}

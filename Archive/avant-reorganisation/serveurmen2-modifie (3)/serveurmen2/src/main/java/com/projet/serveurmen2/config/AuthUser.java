package com.projet.serveurmen2.config;

/**
 * Identite de l'utilisateur authentifie, extraite du token JWT
 * et posee comme attribut de requete ("authUser") par JwtAuthFilter.
 * Les controleurs la recuperent avec @RequestAttribute("authUser").
 */
public record AuthUser(Long userId, String username, String role) {
    public boolean estAdmin() {
        return "ADMIN".equals(role);
    }
}

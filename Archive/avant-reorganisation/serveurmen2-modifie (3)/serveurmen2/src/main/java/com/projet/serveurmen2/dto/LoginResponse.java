package com.projet.serveurmen2.dto;

/**
 * Reponse du POST /api/auth/login (et du POST /api/users).
 * Le client stocke "token" et l'envoie ensuite dans l'en-tete :
 * Authorization: Bearer &lt;token&gt;
 */
public record LoginResponse(String token, String tokenType, long expireDansSecondes, UserResponse user) {
}

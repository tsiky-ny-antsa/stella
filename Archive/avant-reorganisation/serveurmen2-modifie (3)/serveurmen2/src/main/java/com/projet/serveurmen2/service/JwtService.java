package com.projet.serveurmen2.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import io.jsonwebtoken.io.Decoders;

/**
 * Generation et validation des tokens JWT de session.
 * Un token contient : le username (subject), l'id et le role de l'utilisateur.
 * Les clients l'envoient ensuite dans l'en-tete : Authorization: Bearer &lt;token&gt;
 */
@Service
public class JwtService {

    private final SecretKey cle;
    private final Duration dureeDeVie;

    public JwtService(@Value("${app.jwt.secret}") String secretBase64,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.cle = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64));
        this.dureeDeVie = Duration.ofMinutes(expirationMinutes);
    }

    /** Genere un token signe pour l'utilisateur donne. */
    public String generer(Long userId, String username, String role) {
        Date maintenant = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("role", role)
                .issuedAt(maintenant)
                .expiration(new Date(maintenant.getTime() + dureeDeVie.toMillis()))
                .signWith(cle)
                .compact();
    }

    /**
     * Valide la signature et l'expiration du token puis retourne ses claims.
     * Leve une JwtException si le token est invalide ou expire.
     */
    public Claims valider(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(cle)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Duree de vie configuree, en secondes (renvoyee au client apres le login). */
    public long expireDansSecondes() {
        return dureeDeVie.toSeconds();
    }
}

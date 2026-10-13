package com.projet.serveurmen2.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.projet.serveurmen2.service.JwtService;

import java.io.IOException;
import java.util.List;

import static com.projet.serveurmen2.config.ApiConstants.BASE_AUTH;
import static com.projet.serveurmen2.config.ApiConstants.BASE_ETATS;
import static com.projet.serveurmen2.config.ApiConstants.BASE_USERS;
import static com.projet.serveurmen2.config.ApiConstants.CLE_ERREUR;
import static com.projet.serveurmen2.config.ApiConstants.ROUTE_ME;

/**
 * Filtre d'authentification JWT.
 *
 * Zones protegees (401 sans token valide, 403 si role insuffisant) :
 *   - /api/etats/**      : consultation des etats -> tout utilisateur connecte (ADMIN ou USER)
 *   - /api/auth/me       : profil de l'utilisateur connecte
 *   - /api/users/**      : gestion des utilisateurs -> ADMIN uniquement
 *
 * Zones laissee ouvertes : /api/esp32/** (les ESP32 ne se connectent pas),
 * /api/auth/login, et tout le reste. (/api/auth/register est desactivee :
 * les comptes sont crees par un ADMIN via POST /api/users.)
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Prefixes protegees pour tout utilisateur connecte. */
    private static final List<String> PREFIXES_CONNECTES = List.of(
            BASE_ETATS,
            BASE_AUTH + ROUTE_ME);

    /** Prefixes reservees au role ADMIN. */
    private static final List<String> PREFIXES_ADMIN = List.of(
            BASE_USERS);

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Les preflights CORS n'ont jamais d'en-tete Authorization : ils passent toujours
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return !(protege(uri, PREFIXES_CONNECTES) || protege(uri, PREFIXES_ADMIN));
    }

    private static boolean protege(String uri, List<String> prefixes) {
        return prefixes.stream().anyMatch(p -> uri.equals(p) || uri.startsWith(p + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
                                    FilterChain chaine) throws ServletException, IOException {
        String uri = requete.getRequestURI();

        String entete = requete.getHeader("Authorization");
        if (entete == null || !entete.startsWith("Bearer ")) {
            refuser(reponse, HttpServletResponse.SC_UNAUTHORIZED, "non_authentifie",
                    "token manquant : envoyez l'en-tete Authorization: Bearer <token>");
            return;
        }

        Claims claims;
        try {
            claims = jwtService.valider(entete.substring(7));
        } catch (JwtException e) {
            refuser(reponse, HttpServletResponse.SC_UNAUTHORIZED, "non_authentifie",
                    "token invalide ou expire");
            return;
        }

        String role = claims.get("role", String.class);
        if (role == null) {
            refuser(reponse, HttpServletResponse.SC_UNAUTHORIZED, "non_authentifie",
                    "token sans role");
            return;
        }

        // Zones reservees a l'admin
        if (protege(uri, PREFIXES_ADMIN) && !"ADMIN".equals(role)) {
            refuser(reponse, HttpServletResponse.SC_FORBIDDEN, "acces_refuse",
                    "role ADMIN requis");
            return;
        }

        Long uid = claims.get("uid", Long.class);
        requete.setAttribute("authUser", new AuthUser(uid, claims.getSubject(), role));
        chaine.doFilter(requete, reponse);
    }

    /** Reponse JSON d'erreur homogene pour 401 / 403. */
    private static void refuser(HttpServletResponse reponse, int statut, String erreur, String details)
            throws IOException {
        reponse.setStatus(statut);
        reponse.setContentType("application/json;charset=UTF-8");
        reponse.getWriter().write("{\"erreur\":\"" + erreur + "\",\"details\":\"" + details + "\"}");
    }
}

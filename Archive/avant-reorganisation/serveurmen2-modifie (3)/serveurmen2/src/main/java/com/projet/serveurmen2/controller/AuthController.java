package com.projet.serveurmen2.controller;

import com.projet.serveurmen2.config.AuthUser;
import com.projet.serveurmen2.config.ApiConstants;
import com.projet.serveurmen2.dto.LoginRequest;
import com.projet.serveurmen2.dto.LoginResponse;
// Desactive (comptes crees par l'ADMIN uniquement) : import com.projet.serveurmen2.dto.RegisterRequest;
import com.projet.serveurmen2.dto.UserResponse;
import com.projet.serveurmen2.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints d'authentification utilises par la fenetre de login du client :
 *   POST /api/auth/login    : connexion -> token JWT + role
 *   GET  /api/auth/me       : profil de l'utilisateur connecte (token requis)
 * (La route register etait desactivee : les comptes sont crees par l'ADMIN
 *  via POST /api/users.)
 */
@RestController
@RequestMapping(ApiConstants.BASE_AUTH)
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    // -----------------------------------------------------------------
    // Desactive : les comptes sont desormais crees uniquement par un ADMIN
    // via POST /api/users. Pour reactiver l'inscription libre, decommenter
    // ce bloc ainsi que AuthService.enregistrer(), le DTO RegisterRequest,
    // l'import RegisterRequest ci-dessus et ROUTE_REGISTER dans ApiConstants.
    // -----------------------------------------------------------------
    /*
    // POST /api/auth/register : ouvert (le role est force a USER)
    @PostMapping(ApiConstants.ROUTE_REGISTER)
    public ResponseEntity<UserResponse> enregistrer(@Valid @RequestBody RegisterRequest req) {
        UserResponse cree = service.enregistrer(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(cree);
    }
    */

    // POST /api/auth/login : renvoie le token et le role (ADMIN ou USER)
    @PostMapping(ApiConstants.ROUTE_LOGIN)
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(service.login(req.identifiant(), req.password()));
    }

    // GET /api/auth/me : profil de l'utilisateur connecte (token requis)
    @GetMapping(ApiConstants.ROUTE_ME)
    public ResponseEntity<UserResponse> moi(@RequestAttribute("authUser") AuthUser auth) {
        UserResponse profil = service.profil(auth.userId());
        if (profil == null) {
            // token valide mais compte supprime entre-temps
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(profil);
    }
}

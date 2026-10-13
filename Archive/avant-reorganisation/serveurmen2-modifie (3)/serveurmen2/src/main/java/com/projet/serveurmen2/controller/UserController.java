package com.projet.serveurmen2.controller;

import com.projet.serveurmen2.config.AuthUser;
import com.projet.serveurmen2.config.ApiConstants;
import com.projet.serveurmen2.dto.CreationUserRequest;
import com.projet.serveurmen2.dto.UserResponse;
import com.projet.serveurmen2.exception.DoublonException;
import com.projet.serveurmen2.model.Role;
import com.projet.serveurmen2.model.User;
import com.projet.serveurmen2.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Gestion des utilisateurs - reservee au role ADMIN (verifie par JwtAuthFilter).
 *   GET    /api/users      : liste de tous les comptes (sans mot de passe)
 *   POST   /api/users      : creer un compte (USER ou ADMIN, au choix)
 *   DELETE /api/users/{id} : supprimer un compte (pas le sien)
 */
@RestController
@RequestMapping(ApiConstants.BASE_USERS)
public class UserController {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    // GET /api/users : liste des comptes
    @GetMapping
    public List<UserResponse> lister() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    // POST /api/users : creation avec role au choix (ADMIN ou USER)
    @PostMapping
    public ResponseEntity<UserResponse> creer(@Valid @RequestBody CreationUserRequest req) {
        if (users.existsByUsername(req.username())) {
            throw new DoublonException("nom d'utilisateur deja pris : " + req.username());
        }
        if (users.existsByEmail(req.email())) {
            throw new DoublonException("email deja pris : " + req.email());
        }
        User u = new User(req.username(), req.email(),
                encoder.encode(req.password()), Role.valueOf(req.roleOuDefaut()));
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(users.save(u)));
    }

    // DELETE /api/users/{id} : suppression d'un compte (impossible de se supprimer soi-meme)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> supprimer(@PathVariable Long id,
                                       @RequestAttribute("authUser") AuthUser auth) {
        if (!users.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        if (id.equals(auth.userId())) {
            return ResponseEntity.badRequest().body(Map.of(
                    ApiConstants.CLE_ERREUR, "suppression_interdite",
                    "details", "impossible de supprimer son propre compte"));
        }
        users.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

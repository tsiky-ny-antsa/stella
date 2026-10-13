package com.projet.serveurmen2.service;

import com.projet.serveurmen2.dto.LoginResponse;
// Desactive (comptes crees par l'ADMIN uniquement) : import com.projet.serveurmen2.dto.RegisterRequest;
import com.projet.serveurmen2.dto.UserResponse;
// Desactive (avec register) : import com.projet.serveurmen2.exception.DoublonException;
import com.projet.serveurmen2.exception.IdentifiantsIncorrectsException;
import com.projet.serveurmen2.model.Role;
import com.projet.serveurmen2.model.User;
import com.projet.serveurmen2.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logique d'authentification : login (username OU email) avec verification
 * BCrypt, emission du token JWT.
 * (L'enregistrement libre etait desactive : les comptes sont crees par
 *  l'ADMIN via POST /api/users.)
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwtService) {
        this.users = users;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    // -----------------------------------------------------------------
    // Desactive : les comptes sont desormais crees uniquement par un ADMIN
    // via POST /api/users (voir UserController.creer). A reactiver avec la
    // route /register du controleur, le DTO RegisterRequest et ROUTE_REGISTER.
    // -----------------------------------------------------------------
    // /** Creation d'un compte client standard (role force a USER). */
    // @Transactional
    // public UserResponse enregistrer(RegisterRequest req) {
    //     if (users.existsByUsername(req.username())) {
    //         throw new DoublonException("nom d'utilisateur deja pris : " + req.username());
    //     }
    //     if (users.existsByEmail(req.email())) {
    //         throw new DoublonException("email deja pris : " + req.email());
    //     }
    //     User u = new User(req.username(), req.email(), encoder.encode(req.password()), Role.USER);
    //     return UserResponse.from(users.save(u));
    // }

    /**
     * Login : identifiant = username OU email. Renvoie le token JWT + le profil.
     * Le message d'erreur est volontairement le meme pour "inconnu" et "mauvais mot de passe".
     */
    @Transactional(readOnly = true)
    public LoginResponse login(String identifiant, String motDePasse) {
        User u = users.findByUsernameOrEmail(identifiant, identifiant)
                .orElseThrow(IdentifiantsIncorrectsException::new);

        String hash = u.getPassword();
        if (hash == null || hash.isBlank() || !encoder.matches(motDePasse, hash)) {
            throw new IdentifiantsIncorrectsException();
        }

        String role = u.getRole() != null ? u.getRole().name() : Role.USER.name();
        String token = jwtService.generer(u.getId(), u.getUsername(), role);
        return new LoginResponse(token, "Bearer", jwtService.expireDansSecondes(), UserResponse.from(u));
    }

    /** Profil de l'utilisateur connecte (a partir de son token). */
    @Transactional(readOnly = true)
    public UserResponse profil(Long userId) {
        return users.findById(userId).map(UserResponse::from).orElse(null);
    }
}

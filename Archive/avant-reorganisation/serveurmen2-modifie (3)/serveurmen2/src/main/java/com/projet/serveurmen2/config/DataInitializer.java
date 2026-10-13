package com.projet.serveurmen2.config;

import com.projet.serveurmen2.model.Role;
import com.projet.serveurmen2.model.User;
import com.projet.serveurmen2.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Initialisation des comptes au demarrage :
 *   1. les anciens comptes sans role (avant l'ajout de la colonne) deviennent USER ;
 *   2. si aucun ADMIN n'existe, creation du compte admin defini dans
 *      application.properties (app.admin.*). CHANGEZ CE MOT DE PASSE EN PRODUCTION.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Value("${app.admin.username}")
    private String adminUsername;
    @Value("${app.admin.password}")
    private String adminPassword;
    @Value("${app.admin.email}")
    private String adminEmail;

    public DataInitializer(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 1. Comptes anterieurs a l'ajout de la colonne role -> USER par defaut
        users.findAll().stream()
                .filter(u -> u.getRole() == null)
                .forEach(u -> {
                    u.setRole(Role.USER);
                    users.save(u);
                    log.info("Ancien compte '{}' sans role -> USER", u.getUsername());
                });

        // 2. Garantir la presence d'un admin
        if (!users.existsByRole(Role.ADMIN)) {
            users.save(new User(adminUsername, adminEmail, encoder.encode(adminPassword), Role.ADMIN));
            log.warn("Aucun admin existant : compte '{}' cree avec le mot de passe de "
                    + "application.properties. CHANGEZ-LE EN PRODUCTION.", adminUsername);
        }
    }
}

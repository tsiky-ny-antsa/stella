package com.projet.serveurmen2.config;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppConfig {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    // Hachage des mots de passe utilisateurs (jamais stockes en clair)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS : autorise les clients web (navigateurs, applications hebergees ailleurs)
     * a consommer l'API. Les origines autorisees se reglent dans application.properties :
     * app.cors.allowed-origins=*            (tout autoriser, pratique en developpement)
     * app.cors.allowed-origins=https://monappli.example.com,http://localhost:3000
     */
    @Bean
    public WebMvcConfigurer corsConfigurer(@Value("${app.cors.allowed-origins:*}") String origines) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns(origines.split(","))
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .maxAge(3600);
            }
        };
    }
}

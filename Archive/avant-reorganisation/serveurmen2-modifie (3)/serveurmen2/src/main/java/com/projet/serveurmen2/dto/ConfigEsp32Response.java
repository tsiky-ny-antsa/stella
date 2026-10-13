package com.projet.serveurmen2.dto;

/**
 * Configuration centralisee que l'ESP32 lit au demarrage puis periodiquement
 * (GET /api/esp32/{deviceId}/config). Permet de changer les temporisations
 * (ex : passer de la phase raccourcie 5 min a la production 45 min) sans
 * reflasher l'ESP32. C'est aussi le futur canal du "forcage" (iteration suivante).
 */
public record ConfigEsp32Response(
        long delaiBasculeSecondes,
        long intervalleCarburantSecondes,
        double seuilAlerteCarburantPourcent,
        long intervalleRealerteSecondes) {}

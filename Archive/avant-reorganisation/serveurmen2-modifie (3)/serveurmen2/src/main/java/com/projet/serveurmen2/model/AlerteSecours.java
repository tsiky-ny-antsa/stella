package com.projet.serveurmen2.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

import static com.projet.serveurmen2.config.ApiConstants.DETAILS_ALERTE_LONGUEUR_MAX;
import static com.projet.serveurmen2.config.ApiConstants.DEVICE_ID_LONGUEUR_MAX;
import static com.projet.serveurmen2.config.ApiConstants.MESSAGE_ALERTE_LONGUEUR_MAX;
import static com.projet.serveurmen2.config.ApiConstants.TYPE_ALERTE_LONGUEUR_MAX;

/**
 * Alerte envoyee par l'ESP32, typiquement dans la fenetre de bascule :
 * la ligne de secours (groupe electrogene) n'est pas operationnelle alors que
 * la ligne fixe est absente et que l'onduleur tient l'appareil sur batterie.
 *
 * Resolution : geree par le SERVEUR (regles d'auto-resolution dans
 * AlimentationService) pour ne pas compliquer le code de l'ESP32 :
 * - reception d'une source GROUPE_ELECTROGENE -> SECOURS_INOPERATIONNEL resolues
 * - reception d'une source LIGNE_FIXE         -> toutes les alertes actives resolues
 * - mesure de carburant >= seuil              -> CARBURANT_BAS resolues
 */
@Entity
@Table(name = "alerte_secours")
public class AlerteSecours {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = DEVICE_ID_LONGUEUR_MAX)
    private String deviceId;

    // Ex : SECOURS_INOPERATIONNEL, CARBURANT_BAS (champ libre, extensible)
    @Column(name = "type", nullable = false, length = TYPE_ALERTE_LONGUEUR_MAX)
    private String type;

    @Column(name = "message", length = MESSAGE_ALERTE_LONGUEUR_MAX)
    private String message;

    // Details libres au format JSON (ex : {"carburantPourcent":4.0}), null si absents
    @Column(name = "details", length = DETAILS_ALERTE_LONGUEUR_MAX)
    private String details;

    @Column(name = "signalee_at", nullable = false)
    private Instant signaleeAt;

    // Null = alerte encore active
    @Column(name = "resolue_at")
    private Instant resolueAt;

    protected AlerteSecours() {}

    public AlerteSecours(String deviceId, String type, String message, String details, Instant signaleeAt) {
        this.deviceId = deviceId;
        this.type = type;
        this.message = message;
        this.details = details;
        this.signaleeAt = signaleeAt;
    }

    /** Passe l'alerte en resolue (sans effet si elle l'est deja). */
    public void resoudre(Instant instant) {
        if (resolueAt == null) {
            resolueAt = instant;
        }
    }

    public Long getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public String getDetails() { return details; }
    public Instant getSignaleeAt() { return signaleeAt; }
    public Instant getResolueAt() { return resolueAt; }
}

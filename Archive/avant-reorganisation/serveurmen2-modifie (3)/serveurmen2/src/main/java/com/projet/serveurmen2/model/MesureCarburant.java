package com.projet.serveurmen2.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

import static com.projet.serveurmen2.config.ApiConstants.DEVICE_ID_LONGUEUR_MAX;

/**
 * Niveau de carburant du groupe electrogene (ligne de secours), mesure par l'ESP32
 * sur une broche analogique et envoye periodiquement pour le dashboard client.
 * Seul le groupe electrogene possede un reservoir : l'onduleur (ligne temporaire)
 * fonctionne sur batterie, aucune mesure n'est attendue pour lui.
 */
@Entity
@Table(name = "mesure_carburant")
public class MesureCarburant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = DEVICE_ID_LONGUEUR_MAX)
    private String deviceId;

    @Column(name = "niveau_pourcent", nullable = false)
    private double niveauPourcent;

    // Valeur brute lue sur l'ADC (0..4095), utile pour etalonner le capteur
    @Column(name = "valeur_brute")
    private Integer valeurBrute;

    @Column(name = "mesure_at", nullable = false)
    private Instant mesureAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected MesureCarburant() {}

    public MesureCarburant(String deviceId, double niveauPourcent, Integer valeurBrute,
                           Instant mesureAt, Instant receivedAt) {
        this.deviceId = deviceId;
        this.niveauPourcent = niveauPourcent;
        this.valeurBrute = valeurBrute;
        this.mesureAt = mesureAt;
        this.receivedAt = receivedAt;
    }

    public Long getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public double getNiveauPourcent() { return niveauPourcent; }
    public Integer getValeurBrute() { return valeurBrute; }
    public Instant getMesureAt() { return mesureAt; }
    public Instant getReceivedAt() { return receivedAt; }
}

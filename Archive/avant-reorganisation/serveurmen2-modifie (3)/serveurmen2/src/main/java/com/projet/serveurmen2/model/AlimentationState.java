package com.projet.serveurmen2.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

import static com.projet.serveurmen2.config.ApiConstants.DEVICE_ID_LONGUEUR_MAX;
import static com.projet.serveurmen2.config.ApiConstants.MOTIF_LONGUEUR_MAX;

/**
 * Evenement de changement de source d'alimentation de l'appareil important.
 * Chaque ligne = la declaration par l'ESP32 de la source qui alimente l'appareil
 * a partir de capturedAt (timeline reconstituable par ordre chronologique).
 */
@Entity
@Table(name = "alimentation_state")
public class AlimentationState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = DEVICE_ID_LONGUEUR_MAX)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private SourceAlimentation source;

    // Cause de la transition : DEMARRAGE, COUPURE_LIGNE_FIXE, BASCULE_SECOURS, RETOUR_LIGNE_FIXE...
    // Champ libre (l'ESP32 peut declarer d'autres motifs, ex : forcage dans une iteration future).
    @Column(name = "motif", length = MOTIF_LONGUEUR_MAX)
    private String motif;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected AlimentationState() {}

    public AlimentationState(String deviceId, SourceAlimentation source, String motif,
                             Instant capturedAt, Instant receivedAt) {
        this.deviceId = deviceId;
        this.source = source;
        this.motif = motif;
        this.capturedAt = capturedAt;
        this.receivedAt = receivedAt;
    }

    public Long getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public SourceAlimentation getSource() { return source; }
    public String getMotif() { return motif; }
    public Instant getCapturedAt() { return capturedAt; }
    public Instant getReceivedAt() { return receivedAt; }
}

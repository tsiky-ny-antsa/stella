package com.projet.serveurmen2.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

import static com.projet.serveurmen2.config.ApiConstants.DEVICE_ID_LONGUEUR_MAX;

@Entity
@Table(name = "lignefixe_state")
public class LigneFixeState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //@CreationTimestamp
    //@Column(nullable = false, updatable = false)
    //private LocalDateTime date;

    @Column(name = "device_id", nullable = false, length = DEVICE_ID_LONGUEUR_MAX)
    private String deviceId;

    @Column(name = "present", nullable = false)
    private boolean present;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected LigneFixeState() {}

    public LigneFixeState(String deviceId, boolean present, Instant capturedAt, Instant receivedAt) {
        this.deviceId = deviceId;
        this.present = present;
        this.capturedAt = capturedAt;
        this.receivedAt = receivedAt;
    }

    public Long getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public boolean isPresent() { return  present; }
    public Instant getCapturedAt() { return capturedAt; }
    public Instant getReceivedAt() { return receivedAt; }
}

package com.projet.serveurmen2.controller;

import com.projet.serveurmen2.dto.AlerteRequest;
import com.projet.serveurmen2.dto.AlerteResponse;
import com.projet.serveurmen2.dto.CarburantRequest;
import com.projet.serveurmen2.dto.CarburantResponse;
import com.projet.serveurmen2.dto.ConfigEsp32Response;
import com.projet.serveurmen2.dto.SourceAlimentationRequest;
import com.projet.serveurmen2.dto.SourceAlimentationResponse;
import com.projet.serveurmen2.model.AlerteSecours;
import com.projet.serveurmen2.model.MesureCarburant;
import com.projet.serveurmen2.service.AlimentationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static com.projet.serveurmen2.config.ApiConstants.BASE_ESP32;
import static com.projet.serveurmen2.config.ApiConstants.DEVICE_ID_REGEX;
import static com.projet.serveurmen2.config.ApiConstants.ROUTE_ESP_ALERTES;
import static com.projet.serveurmen2.config.ApiConstants.ROUTE_ESP_CARBURANT;
import static com.projet.serveurmen2.config.ApiConstants.ROUTE_ESP_CONFIG;
import static com.projet.serveurmen2.config.ApiConstants.ROUTE_ESP_SOURCE;

/**
 * Routes d'ingestion ALIMENTATION, appelees par l'ESP32 (sans authentification,
 * comme /lignefixe). L'ESP32 y declare :
 * - la source qui alimente l'appareil (LIGNE_FIXE / ONDULEUR / GROUPE_ELECTROGENE)
 * - les alertes (ligne de secours non operationnelle, carburant bas...)
 * - le niveau de carburant du groupe electrogene (seul reservoir du systeme)
 * et lit la configuration centralisee (delai de bascule raccourci en phase actuelle,
 * intervalle de mesure du carburant, seuil d'alerte, repetition des alertes).
 */
@RestController
@RequestMapping(BASE_ESP32)
@Validated
public class Esp32AlimentationController {

    private final AlimentationService service;

    public Esp32AlimentationController(AlimentationService service) {
        this.service = service;
    }

    // POST : esp32 vers serveur - declaration de la source d'alimentation
    // Reponse : basculePrevue renseignee si source = ONDULEUR (capturedAt + delai serveur)
    @PostMapping(ROUTE_ESP_SOURCE)
    public ResponseEntity<SourceAlimentationResponse> declarerSource(
            @PathVariable @Pattern(regexp = DEVICE_ID_REGEX) String deviceId,
            @Valid @RequestBody SourceAlimentationRequest req) {
        var saved = service.enregistrerSource(deviceId, req.source(), req.motif(), req.capturedAt());
        Instant basculePrevue = SourceAlimentationResponse.echeanceBascule(
                saved.getSource(), saved.getCapturedAt(), service.delaiBasculeSecondes());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SourceAlimentationResponse.from(saved, basculePrevue));
    }

    // POST : esp32 vers serveur - alerte (secours non operationnel, carburant bas...)
    @PostMapping(ROUTE_ESP_ALERTES)
    public ResponseEntity<AlerteResponse> declarerAlerte(
            @PathVariable @Pattern(regexp = DEVICE_ID_REGEX) String deviceId,
            @Valid @RequestBody AlerteRequest req) {
        AlerteSecours saved = service.enregistrerAlerte(
                deviceId, req.type(), req.message(), req.details(), req.capturedAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(AlerteResponse.from(saved, req.details()));
    }

    // POST : esp32 vers serveur - niveau de carburant du groupe electrogene
    @PostMapping(ROUTE_ESP_CARBURANT)
    public ResponseEntity<CarburantResponse> declarerCarburant(
            @PathVariable @Pattern(regexp = DEVICE_ID_REGEX) String deviceId,
            @Valid @RequestBody CarburantRequest req) {
        MesureCarburant saved = service.enregistrerCarburant(
                deviceId, req.niveauPourcent(), req.valeurBrute(), req.capturedAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(CarburantResponse.from(saved));
    }

    // GET : configuration centralisee lue par l'ESP32 (au demarrage puis periodiquement)
    @GetMapping(ROUTE_ESP_CONFIG)
    public ConfigEsp32Response config() {
        return service.config();
    }
}

package com.projet.serveurmen2.controller;

import com.projet.serveurmen2.dto.LigneFixeRequest;
import com.projet.serveurmen2.dto.LigneFixeResponse;
import com.projet.serveurmen2.model.LigneFixeState;
import com.projet.serveurmen2.repository.LigneFixeStateRepository;
import com.projet.serveurmen2.service.LigneFixeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.projet.serveurmen2.config.ApiConstants.*;

@RestController
@RequestMapping(BASE_ESP32)
@Validated
public class LigneFixeController {

    private final LigneFixeService service;

    public LigneFixeController(LigneFixeService service) { this.service = service; }


    // POST : esp32 vers serveur
    @PostMapping(ROUTE_LIGNE_FIXE)
    public ResponseEntity<LigneFixeResponse> enregistrer(
            @PathVariable @Pattern(regexp = DEVICE_ID_REGEX) String deviceId,
            @Valid @RequestBody LigneFixeRequest req) {
        var saved = service.enregistrer(deviceId, req.present(), req.capturedAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(LigneFixeResponse.from(saved));
    }

    // Historique, du plus récent au plus ancien
    @GetMapping(ROUTE_LIGNE_FIXE)
    public List<LigneFixeResponse> historique(
            @PathVariable @Pattern(regexp = DEVICE_ID_REGEX) String deviceId,
            @RequestParam(defaultValue = LIMITE_PAR_DEFAUT_TEXTE) @Min(1) @Max(LIMITE_MAX) int limite) {
        return service.historique(deviceId, limite).stream().map(LigneFixeResponse::from).toList();
    }

    // Dernier état connu
    @GetMapping(ROUTE_LIGNE_FIXE_LATEST)
    public ResponseEntity<LigneFixeResponse> dernier(
            @PathVariable @Pattern(regexp = DEVICE_ID_REGEX) String deviceId) {
        return service.dernier(deviceId)
                .map(s -> ResponseEntity.ok(LigneFixeResponse.from(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    /*private final LigneFixeStateRepository ligneFixeStateRepository;

    public LigneFixeController(LigneFixeStateRepository userRepository) {
        this.ligneFixeStateRepository = userRepository;
    }

    @GetMapping
    public List<LigneFixeState> getAllUsers() {
        return ligneFixeStateRepository.findAll();
    }

    // POST : ajouter un utilisateur
    @PostMapping
    public LigneFixeState createUser(@RequestBody LigneFixeState ligneFixeState) {
        return ligneFixeStateRepository.save(ligneFixeState);
    }

    // SUPPRIMER — DELETE /api/produits/1
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        if (ligneFixeStateRepository.existsById(id)) {
            ligneFixeStateRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }*/
}

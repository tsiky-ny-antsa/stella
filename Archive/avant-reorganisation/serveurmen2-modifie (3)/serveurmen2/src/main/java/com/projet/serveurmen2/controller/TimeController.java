package com.projet.serveurmen2.controller;

import com.projet.serveurmen2.service.LigneFixeService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static com.projet.serveurmen2.config.ApiConstants.BASE_ESP32;
import static com.projet.serveurmen2.config.ApiConstants.ROUTE_TIME;

@RestController
@RequestMapping(BASE_ESP32)
public class TimeController {
    private static final String CLE_EPOCH = "epoch";
    private final LigneFixeService service;

    public TimeController(LigneFixeService service) { this.service = service; }

    // Source de temps pour l'esp32
    @GetMapping(ROUTE_TIME)
    public Map<String, Long> heure() {
        return Map.of(CLE_EPOCH, service.epochSecondeActuelle());
    }
}

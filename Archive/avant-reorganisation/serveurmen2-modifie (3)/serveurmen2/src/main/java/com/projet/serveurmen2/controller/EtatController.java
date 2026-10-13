package com.projet.serveurmen2.controller;

// Desactive (hors cahier des charges, iteration future) : import com.projet.serveurmen2.dto.AppareilResponse;
import com.projet.serveurmen2.dto.AlimentationActuelleResponse;
import com.projet.serveurmen2.dto.AlimentationStateResponse;
import com.projet.serveurmen2.dto.AlerteResponse;
import com.projet.serveurmen2.dto.CarburantResponse;
import com.projet.serveurmen2.dto.EtatActuelResponse;
import com.projet.serveurmen2.dto.LigneFixeResponse;
import com.projet.serveurmen2.dto.PageResponse;
// Desactive (hors cahier des charges, iteration future) : import com.projet.serveurmen2.dto.StatsResponse;
import com.projet.serveurmen2.exception.PeriodeInvalideException;
import com.projet.serveurmen2.model.AlimentationState;
import com.projet.serveurmen2.model.LigneFixeState;
import com.projet.serveurmen2.service.AlimentationService;
import com.projet.serveurmen2.service.LigneFixeService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

import static com.projet.serveurmen2.config.ApiConstants.BASE_ETATS;
import static com.projet.serveurmen2.config.ApiConstants.DEVICE_ID_REGEX;
// Desactive (iteration future) : import static com.projet.serveurmen2.config.ApiConstants.ROUTE_APPAREILS;
import static com.projet.serveurmen2.config.ApiConstants.ROUTE_ALIMENTATION;
import static com.projet.serveurmen2.config.ApiConstants.ROUTE_ALIMENTATION_HISTORIQUE;
import static com.projet.serveurmen2.config.ApiConstants.ROUTE_ALERTES;
import static com.projet.serveurmen2.config.ApiConstants.ROUTE_CARBURANT;
import static com.projet.serveurmen2.config.ApiConstants.ROUTE_ETAT_ACTUEL;
import static com.projet.serveurmen2.config.ApiConstants.ROUTE_HISTORIQUE;
import static com.projet.serveurmen2.config.ApiConstants.LIMITE_PAR_DEFAUT_TEXTE;
import static com.projet.serveurmen2.config.ApiConstants.LIMITE_MAX;
// Desactive (iteration future) : import static com.projet.serveurmen2.config.ApiConstants.ROUTE_STATS;
import static com.projet.serveurmen2.config.ApiConstants.TAILLE_PAGE_MAX;
import static com.projet.serveurmen2.config.ApiConstants.TAILLE_PAGE_PAR_DEFAUT_TEXTE;

/**
 * Routes de consultation reservees aux CLIENTS (applications web, mobiles, scripts).
 * Elles permettent de recuperer l'historique des etats et l'etat present
 * des appareils qui remontent leurs donnees via /api/esp32.
 */
@RestController
@RequestMapping(BASE_ETATS)
@Validated
public class EtatController {

    private final LigneFixeService service;
    private final AlimentationService serviceAlimentation;

    public EtatController(LigneFixeService service, AlimentationService serviceAlimentation) {
        this.service = service;
        this.serviceAlimentation = serviceAlimentation;
    }

    /* -----------------------------------------------------------------
     * Desactive : hors cahier des charges (un seul ESP32 pour le moment).
     * A reactiver dans une iteration future en decommentant ce bloc
     * (ainsi que LigneFixeService.appareils(), la requete findDeviceIds()
     * du repository, le DTO AppareilResponse et l'import de java.util.List).
     * -----------------------------------------------------------------
    // GET /api/etats/appareils : liste des appareils connus et leur dernier etat
    @GetMapping(ROUTE_APPAREILS)
    public List<AppareilResponse> appareils() {
        return service.appareils();
    }
     * -----------------------------------------------------------------
     */

    // GET /api/etats/{deviceId}/actuel : etat present + anciennete de la mesure
    @GetMapping(ROUTE_ETAT_ACTUEL)
    public ResponseEntity<EtatActuelResponse> etatActuel(
            @PathVariable @Pattern(regexp = DEVICE_ID_REGEX) String deviceId) {
        return service.etatActuel(deviceId)
                .map(s -> ResponseEntity.ok(EtatActuelResponse.from(s, service.maintenant())))
                .orElse(ResponseEntity.notFound().build());
    }

    // GET /api/etats/{deviceId}/historique : historique pagine et filtrable par periode
    // Exemples :
    //   .../historique
    //   .../historique?debut=2026-10-01T00:00:00Z&fin=2026-10-08T00:00:00Z&page=0&taille=100
    @GetMapping(ROUTE_HISTORIQUE)
    public PageResponse<LigneFixeResponse> historique(
            @PathVariable @Pattern(regexp = DEVICE_ID_REGEX) String deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fin,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = TAILLE_PAGE_PAR_DEFAUT_TEXTE) @Min(1) @Max(TAILLE_PAGE_MAX) int taille) {
        verifierPeriode(debut, fin);
        Page<LigneFixeState> pageEtat = service.pageHistorique(deviceId, debut, fin, page, taille);
        return PageResponse.from(pageEtat, LigneFixeResponse::from);
    }

    /* -----------------------------------------------------------------
     * Desactive : hors cahier des charges. A reactiver dans une iteration
     * future en decommentant ce bloc (ainsi que LigneFixeService.stats(),
     * les requetes chronologiques du repository, les DTOs StatsResponse /
     * CoupureResponse et les imports associes).
     * -----------------------------------------------------------------
    // GET /api/etats/{deviceId}/stats : statistiques sur une periode
    // Exemples :
    //   .../stats                                        (toute l'historique)
    //   .../stats?debut=2026-10-07T00:00:00Z             (depuis une date)
    //   .../stats?debut=2026-10-07T00:00:00Z&fin=2026-10-08T00:00:00Z
    @GetMapping(ROUTE_STATS)
    public StatsResponse stats(
            @PathVariable @Pattern(regexp = DEVICE_ID_REGEX) String deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fin) {
        verifierPeriode(debut, fin);
        return service.stats(deviceId, debut, fin);
    }
     * -----------------------------------------------------------------
     */

    // ------------------------------------------------------------------
    // Suivi de l'alimentation : sources, alertes, carburant (dashboard client)
    // ------------------------------------------------------------------

    // GET /api/etats/{deviceId}/alimentation : tableau de bord complet en une requete
    // (source actuelle, echeance de bascule, ligne fixe, carburant, alertes actives)
    @GetMapping(ROUTE_ALIMENTATION)
    public ResponseEntity<AlimentationActuelleResponse> alimentationActuelle(
            @PathVariable @Pattern(regexp = DEVICE_ID_REGEX) String deviceId) {
        return serviceAlimentation.alimentationActuelle(deviceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // GET /api/etats/{deviceId}/alimentation/historique : timeline des sources, paginee
    // Exemples :
    //   .../alimentation/historique
    //   .../alimentation/historique?debut=2026-10-01T00:00:00Z&page=0&taille=100
    @GetMapping(ROUTE_ALIMENTATION_HISTORIQUE)
    public PageResponse<AlimentationStateResponse> alimentationHistorique(
            @PathVariable @Pattern(regexp = DEVICE_ID_REGEX) String deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fin,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = TAILLE_PAGE_PAR_DEFAUT_TEXTE) @Min(1) @Max(TAILLE_PAGE_MAX) int taille) {
        verifierPeriode(debut, fin);
        Page<AlimentationState> pageEtat = serviceAlimentation.pageHistorique(deviceId, debut, fin, page, taille);
        return PageResponse.from(pageEtat, AlimentationStateResponse::from);
    }

    // GET /api/etats/{deviceId}/alertes : alertes du groupe electrogene
    // resolues : absent = toutes, false = actives seulement, true = resolues seulement
    @GetMapping(ROUTE_ALERTES)
    public List<AlerteResponse> alertes(
            @PathVariable @Pattern(regexp = DEVICE_ID_REGEX) String deviceId,
            @RequestParam(required = false) Boolean resolues,
            @RequestParam(defaultValue = LIMITE_PAR_DEFAUT_TEXTE) @Min(1) @Max(LIMITE_MAX) int limite) {
        return serviceAlimentation.alertes(deviceId, resolues, limite).stream()
                .map(a -> AlerteResponse.from(a, serviceAlimentation.detailsDe(a)))
                .toList();
    }

    // GET /api/etats/{deviceId}/carburant : serie temporelle prete pour un graphique
    // (les "limite" mesures les plus recentes, reordonnees du plus ancien au plus recent)
    @GetMapping(ROUTE_CARBURANT)
    public List<CarburantResponse> carburant(
            @PathVariable @Pattern(regexp = DEVICE_ID_REGEX) String deviceId,
            @RequestParam(defaultValue = LIMITE_PAR_DEFAUT_TEXTE) @Min(1) @Max(LIMITE_MAX) int limite) {
        return serviceAlimentation.historiqueCarburant(deviceId, limite).stream()
                .map(CarburantResponse::from)
                .toList();
    }

    // Rejet d'une periode incoherente (debut apres fin)
    private static void verifierPeriode(Instant debut, Instant fin) {
        if (debut != null && fin != null && debut.isAfter(fin)) {
            throw new PeriodeInvalideException("debut est posterieur a fin");
        }
    }
}

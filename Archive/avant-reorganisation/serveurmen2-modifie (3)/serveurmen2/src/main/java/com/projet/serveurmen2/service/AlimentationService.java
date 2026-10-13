package com.projet.serveurmen2.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import com.projet.serveurmen2.dto.AlimentationActuelleResponse;
import com.projet.serveurmen2.dto.ConfigEsp32Response;
import com.projet.serveurmen2.model.AlimentationState;
import com.projet.serveurmen2.model.AlerteSecours;
import com.projet.serveurmen2.model.MesureCarburant;
import com.projet.serveurmen2.model.SourceAlimentation;
import com.projet.serveurmen2.exception.ValeurInvalideException;
import com.projet.serveurmen2.repository.AlimentationStateRepository;
import com.projet.serveurmen2.repository.AlerteSecoursRepository;
import com.projet.serveurmen2.repository.LigneFixeStateRepository;
import com.projet.serveurmen2.repository.MesureCarburantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.projet.serveurmen2.config.ApiConstants.DETAILS_ALERTE_LONGUEUR_MAX;
import static com.projet.serveurmen2.config.ApiConstants.TOLERANCE_FUTUR_MINUTES;
import static com.projet.serveurmen2.config.ApiConstants.TYPE_CARBURANT_BAS;
import static com.projet.serveurmen2.config.ApiConstants.TYPE_SECOURS_INOPERATIONNEL;

/**
 * Suivi de la chaine d'alimentation de l'appareil important :
 *
 *   LIGNE_FIXE --coupure--> ONDULEUR (ligne temporaire) --delai ecoule + secours OK
 *                          --> GROUPE_ELECTROGENE (ligne de secours)
 *
 * L'ESP32 est le chef d'orchestre : il chronometre la coupure, verifie la ligne
 * de secours et bascule physiquement le relais. Le serveur ENREGISTRE les
 * evenements declares, auto-resout les alertes et expose l'etat aux clients.
 * Le delai de bascule (45 min en production, raccourci en phase actuelle) vit
 * dans application.properties et est diffuse a l'ESP32 via GET .../config.
 */
@Service
public class AlimentationService {

    private static final Duration TOLERANCE_FUTUR = Duration.ofMinutes(TOLERANCE_FUTUR_MINUTES);

    private final AlimentationStateRepository repoSource;
    private final AlerteSecoursRepository repoAlertes;
    private final MesureCarburantRepository repoCarburant;
    private final LigneFixeStateRepository repoLigneFixe;
    private final Clock clock;

    // Jackson 3 (tools.jackson) : uniquement pour serialiser/lire les details libres des alertes
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final long delaiBasculeSecondes;
    private final long intervalleCarburantSecondes;
    private final double seuilAlerteCarburantPourcent;
    private final long intervalleRealerteSecondes;

    public AlimentationService(AlimentationStateRepository repoSource,
                               AlerteSecoursRepository repoAlertes,
                               MesureCarburantRepository repoCarburant,
                               LigneFixeStateRepository repoLigneFixe,
                               Clock clock,
                               @Value("${app.alimentation.delai-bascule-secondes}") long delaiBasculeSecondes,
                               @Value("${app.alimentation.intervalle-carburant-secondes}") long intervalleCarburantSecondes,
                               @Value("${app.alimentation.seuil-alerte-carburant-pourcent}") double seuilAlerteCarburantPourcent,
                               @Value("${app.alimentation.intervalle-realerte-secondes}") long intervalleRealerteSecondes) {
        this.repoSource = repoSource;
        this.repoAlertes = repoAlertes;
        this.repoCarburant = repoCarburant;
        this.repoLigneFixe = repoLigneFixe;
        this.clock = clock;
        this.delaiBasculeSecondes = delaiBasculeSecondes;
        this.intervalleCarburantSecondes = intervalleCarburantSecondes;
        this.seuilAlerteCarburantPourcent = seuilAlerteCarburantPourcent;
        this.intervalleRealerteSecondes = intervalleRealerteSecondes;
    }

    // ------------------------------------------------------------------
    // Ingestion des evenements ESP32
    // ------------------------------------------------------------------

    @Transactional
    public AlimentationState enregistrerSource(String deviceId, String sourceTexte, String motif, Instant capturedAt) {
        SourceAlimentation source = parserSource(sourceTexte);
        Instant reception = clock.instant();
        Instant capture = choisirCapture(capturedAt, reception);

        AlimentationState saved = repoSource.save(new AlimentationState(deviceId, source, motif, capture, reception));

        // Auto-resolution des alertes (le serveur gere les IDs, pas l'ESP32) :
        // - le groupe electrogene alimente l'appareil -> il fonctionne : SECOURS_INOPERATIONNEL resolues
        // - retour de la ligne fixe -> retour a la normale : toutes les alertes resolues
        if (source == SourceAlimentation.LIGNE_FIXE) {
            for (AlerteSecours a : repoAlertes.findByDeviceIdAndResolueAtIsNull(deviceId)) {
                a.resoudre(reception);
            }
        } else if (source == SourceAlimentation.GROUPE_ELECTROGENE) {
            for (AlerteSecours a : repoAlertes.findByDeviceIdAndTypeAndResolueAtIsNull(deviceId, TYPE_SECOURS_INOPERATIONNEL)) {
                a.resoudre(reception);
            }
        }
        return saved;
    }

    @Transactional
    public AlerteSecours enregistrerAlerte(String deviceId, String type, String message, JsonNode details, Instant capturedAt) {
        Instant reception = clock.instant();
        Instant capture = choisirCapture(capturedAt, reception);
        String detailsJson = serialiserDetails(details);
        return repoAlertes.save(new AlerteSecours(deviceId, type.trim(), message, detailsJson, capture));
    }

    @Transactional
    public MesureCarburant enregistrerCarburant(String deviceId, double niveauPourcent, Integer valeurBrute, Instant capturedAt) {
        Instant reception = clock.instant();
        Instant capture = choisirCapture(capturedAt, reception);

        MesureCarburant saved = repoCarburant.save(
                new MesureCarburant(deviceId, niveauPourcent, valeurBrute, capture, reception));

        // Auto-resolution : le niveau est remonte au-dessus du seuil -> CARBURANT_BAS resolues
        if (niveauPourcent >= seuilAlerteCarburantPourcent) {
            for (AlerteSecours a : repoAlertes.findByDeviceIdAndTypeAndResolueAtIsNull(deviceId, TYPE_CARBURANT_BAS)) {
                a.resoudre(reception);
            }
        }
        return saved;
    }

    // ------------------------------------------------------------------
    // Consultations clients
    // ------------------------------------------------------------------

    /** Tableau de bord complet : source actuelle + echeance + ligne fixe + carburant + alertes actives. */
    @Transactional(readOnly = true)
    public Optional<AlimentationActuelleResponse> alimentationActuelle(String deviceId) {
        Optional<AlimentationState> dernier = repoSource.findFirstByDeviceIdOrderByCapturedAtDescIdDesc(deviceId);
        if (dernier.isEmpty()) {
            return Optional.empty();
        }
        AlimentationState etat = dernier.get();
        Instant maintenant = clock.instant();

        Boolean lignefixePresente = null;
        Instant lignefixeDepuis = null;
        var derniereLigneFixe = repoLigneFixe.findFirstByDeviceIdOrderByCapturedAtDesc(deviceId);
        if (derniereLigneFixe.isPresent()) {
            lignefixePresente = derniereLigneFixe.get().isPresent();
            lignefixeDepuis = derniereLigneFixe.get().getCapturedAt();
        }

        MesureCarburant carburant = repoCarburant.findFirstByDeviceIdOrderByMesureAtDescIdDesc(deviceId).orElse(null);

        List<AlimentationActuelleResponse.AlerteActive> actives = new ArrayList<>();
        for (AlerteSecours a : repoAlertes.findByDeviceIdAndResolueAtIsNullOrderBySignaleeAtDescIdDesc(
                deviceId, PageRequest.of(0, ALERTES_ACTIVES_MAX))) {
            actives.add(new AlimentationActuelleResponse.AlerteActive(
                    a.getId(), a.getType(), a.getMessage(), detailsDe(a), a.getSignaleeAt()));
        }

        return Optional.of(AlimentationActuelleResponse.from(
                etat, maintenant, delaiBasculeSecondes, lignefixePresente, lignefixeDepuis, carburant, actives));
    }

    /** Historique pagine des changements de source, du plus recent au plus ancien, filtres de periode optionnels. */
    @Transactional(readOnly = true)
    public Page<AlimentationState> pageHistorique(String deviceId, Instant debut, Instant fin, int page, int taille) {
        Pageable pageable = PageRequest.of(page, taille, Sort.by(Sort.Direction.DESC, "capturedAt", "id"));
        if (debut != null && fin != null) {
            return repoSource.findByDeviceIdAndCapturedAtGreaterThanEqualAndCapturedAtLessThanEqualOrderByCapturedAtDescIdDesc(
                    deviceId, debut, fin, pageable);
        }
        if (debut != null) {
            return repoSource.findByDeviceIdAndCapturedAtGreaterThanEqualOrderByCapturedAtDescIdDesc(deviceId, debut, pageable);
        }
        if (fin != null) {
            return repoSource.findByDeviceIdAndCapturedAtLessThanEqualOrderByCapturedAtDescIdDesc(deviceId, fin, pageable);
        }
        return repoSource.findByDeviceIdOrderByCapturedAtDescIdDesc(deviceId, pageable);
    }

    /**
     * Alertes d'un appareil, du plus recent au plus ancien.
     * resolues : null = toutes, true = resolues uniquement, false = actives uniquement.
     */
    @Transactional(readOnly = true)
    public List<AlerteSecours> alertes(String deviceId, Boolean resolues, int limite) {
        Pageable pageable = PageRequest.of(0, limite, Sort.by(Sort.Direction.DESC, "signaleeAt", "id"));
        if (resolues == null) {
            return repoAlertes.findByDeviceIdOrderBySignaleeAtDescIdDesc(deviceId, pageable);
        }
        return resolues
                ? repoAlertes.findByDeviceIdAndResolueAtIsNotNullOrderBySignaleeAtDescIdDesc(deviceId, pageable)
                : repoAlertes.findByDeviceIdAndResolueAtIsNullOrderBySignaleeAtDescIdDesc(deviceId, pageable);
    }

    /**
     * Serie temporelle du carburant, prete pour un graphique : les "limite" mesures
     * les plus recentes, reordonnees du plus ancien au plus recent.
     */
    @Transactional(readOnly = true)
    public List<MesureCarburant> historiqueCarburant(String deviceId, int limite) {
        List<MesureCarburant> mesures = new ArrayList<>(repoCarburant.findByDeviceIdOrderByMesureAtDescIdDesc(
                deviceId, PageRequest.of(0, limite, Sort.by(Sort.Direction.DESC, "mesureAt", "id"))));
        Collections.reverse(mesures);
        return mesures;
    }

    // ------------------------------------------------------------------
    // Configuration diffusee a l'ESP32
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ConfigEsp32Response config() {
        return new ConfigEsp32Response(delaiBasculeSecondes, intervalleCarburantSecondes,
                seuilAlerteCarburantPourcent, intervalleRealerteSecondes);
    }

    /** Delai de bascule en secondes (utilise par le controleur pour calculer basculePrevue). */
    public long delaiBasculeSecondes() {
        return delaiBasculeSecondes;
    }

    /** Instant serveur courant (UTC). */
    public Instant maintenant() {
        return clock.instant();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Analyse la source declaree (insensible a la casse) ; erreur 400 explicite sinon. */
    public static SourceAlimentation parserSource(String texte) {
        if (texte != null) {
            for (SourceAlimentation s : SourceAlimentation.values()) {
                if (s.name().equalsIgnoreCase(texte.trim())) {
                    return s;
                }
            }
        }
        throw new ValeurInvalideException(
                "source : valeurs acceptees = LIGNE_FIXE, ONDULEUR, GROUPE_ELECTROGENE");
    }

    /**
     * Choix de l'horodatage de capture : celui de l'ESP32 s'il est fourni et pas
     * aberrant (dans le futur au-dela de la tolerance), sinon l'heure serveur.
     * (Meme regle que LigneFixeService : l'ESP32 peut envoyer sans heure synchro.)
     */
    private Instant choisirCapture(Instant capture, Instant reception) {
        if (capture == null || capture.isAfter(reception.plus(TOLERANCE_FUTUR))) {
            return reception;
        }
        return capture;
    }

    /** Details JSON libres -> chaine stockee ; refus si trop volumineux. */
    private String serialiserDetails(JsonNode details) {
        if (details == null || details.isNull()) {
            return null;
        }
        String json = jsonMapper.writeValueAsString(details);
        if (json.length() > DETAILS_ALERTE_LONGUEUR_MAX) {
            throw new ValeurInvalideException(
                    "details : trop volumineux (max " + DETAILS_ALERTE_LONGUEUR_MAX + " caracteres)");
        }
        return json;
    }

    /** Details stockes en chaine -> JsonNode pour la reponse JSON (tolerant aux valeurs non JSON). */
    public JsonNode detailsDe(AlerteSecours alerte) {
        String details = alerte.getDetails();
        if (details == null || details.isBlank()) {
            return null;
        }
        try {
            return jsonMapper.readTree(details);
        } catch (Exception e) {
            return JsonNodeFactory.instance.textNode(details);
        }
    }

    /** Nombre maximal d'alertes actives renvoyees dans le tableau de bord. */
    private static final int ALERTES_ACTIVES_MAX = 50;
}

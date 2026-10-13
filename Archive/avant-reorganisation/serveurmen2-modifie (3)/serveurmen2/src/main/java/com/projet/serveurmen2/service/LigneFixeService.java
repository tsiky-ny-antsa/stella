package com.projet.serveurmen2.service;

// Desactive (iteration future) : import static com.projet.serveurmen2.config.ApiConstants.COUPURES_MAX_RENVOYEES;
import static com.projet.serveurmen2.config.ApiConstants.TOLERANCE_FUTUR_MINUTES;

// Desactive (iteration future) : import com.projet.serveurmen2.dto.AppareilResponse;
// Desactive (iteration future) : import com.projet.serveurmen2.dto.CoupureResponse;
// Desactive (iteration future) : import com.projet.serveurmen2.dto.StatsResponse;
import com.projet.serveurmen2.model.LigneFixeState;
import com.projet.serveurmen2.repository.LigneFixeStateRepository;

import java.time.*;
// Desactive (iteration future) : import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class LigneFixeService {
    private static final Duration TOLERANCE_FUTUR = Duration.ofMinutes(TOLERANCE_FUTUR_MINUTES);
    private static final int PREMIERE_PAGE = 0;

    private final LigneFixeStateRepository repo;
    private final Clock clock;

    public LigneFixeService(LigneFixeStateRepository repo,Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Transactional
    public LigneFixeState enregistrer(String deviceId, boolean present, Instant capturedAt) {
        Instant reception = clock.instant();
        return repo.save(new LigneFixeState(deviceId, present, choisirCapture(capturedAt, reception), reception));
    }

    private Instant choisirCapture(Instant capture, Instant reception) {
        if(capture == null || capture.isAfter(reception.plus(TOLERANCE_FUTUR))) {
            return  reception;
        }
        return capture;
    }

    @Transactional(readOnly = true)
    public List<LigneFixeState> historique(String deviceId, int limite) {
        return repo.findByDeviceId(deviceId, PageRequest.of(PREMIERE_PAGE, limite, Sort.by(Sort.Direction.DESC, "capturedAt")));
    }

    // --- Nouvelles methodes pour les clients (historique riche, etat present) ---

    /**
     * Historique pagine, du plus recent au plus ancien, avec filtres de periode optionnels.
     * debut et fin peuvent etre null (= sans borne).
     */
    @Transactional(readOnly = true)
    public Page<LigneFixeState> pageHistorique(String deviceId, Instant debut, Instant fin, int page, int taille) {
        Pageable pageable = PageRequest.of(page, taille, Sort.by(Sort.Direction.DESC, "capturedAt"));
        if (debut != null && fin != null) {
            return repo.findByDeviceIdAndCapturedAtGreaterThanEqualAndCapturedAtLessThanEqualOrderByCapturedAtDesc(
                    deviceId, debut, fin, pageable);
        }
        if (debut != null) {
            return repo.findByDeviceIdAndCapturedAtGreaterThanEqualOrderByCapturedAtDesc(deviceId, debut, pageable);
        }
        if (fin != null) {
            return repo.findByDeviceIdAndCapturedAtLessThanEqualOrderByCapturedAtDesc(deviceId, fin, pageable);
        }
        return repo.findByDeviceIdOrderByCapturedAtDesc(deviceId, pageable);
    }

    /** Etat present = dernier evenement enregistre pour l'appareil. */
    @Transactional(readOnly = true)
    public Optional<LigneFixeState> etatActuel(String deviceId) {
        return repo.findFirstByDeviceIdOrderByCapturedAtDesc(deviceId);
    }

    /** Instant serveur courant (UTC). */
    public Instant maintenant() {
        return clock.instant();
    }

    // -----------------------------------------------------------------
    // Desactive : hors cahier des charges (un seul ESP32 pour le moment).
    // A reactiver dans une iteration future, avec la route /appareils du
    // controleur, la requete findDeviceIds() du repository et le DTO
    // AppareilResponse.
    // -----------------------------------------------------------------
    // /** Liste des appareils connus avec leur dernier etat. */
    // @Transactional(readOnly = true)
    // public List<AppareilResponse> appareils() {
    //     List<AppareilResponse> resultats = new ArrayList<>();
    //     for (String deviceId : repo.findDeviceIds()) {
    //         repo.findFirstByDeviceIdOrderByCapturedAtDesc(deviceId)
    //                 .ifPresent(s -> resultats.add(AppareilResponse.from(s)));
    //     }
    //     return resultats;
    // }

    // -----------------------------------------------------------------
    // Desactive : hors cahier des charges. A reactiver dans une iteration
    // future, avec la route /{deviceId}/stats du controleur, les requetes
    // chronologiques du repository et les DTOs StatsResponse/CoupureResponse.
    //
    // Principe de calcul : l'ESP32 n'envoie que les CHANGEMENTS d'etat. Chaque
    // evenement ouvre un "segment" qui dure jusqu'a l'evenement suivant (ou
    // jusqu'a la fin de la fenetre pour le dernier). Les durees par etat
    // s'obtiennent en additionnant les segments. Les evenements de meme etat
    // consecutifs (doublons de renvoi) sont fusionnes.
    // -----------------------------------------------------------------
    // @Transactional(readOnly = true)
    // public StatsResponse stats(String deviceId, Instant debut, Instant fin) {
    //     Instant maintenant = clock.instant();
    //     Instant finEffective = (fin != null) ? fin : maintenant;
    //
    //     // Evenements de la fenetre, en ordre chronologique
    //     List<LigneFixeState> evts;
    //     if (debut != null) {
    //         evts = repo.findByDeviceIdAndCapturedAtGreaterThanEqualAndCapturedAtLessThanEqualOrderByCapturedAtAsc(
    //                 deviceId, debut, finEffective);
    //     } else {
    //         evts = repo.findByDeviceIdAndCapturedAtLessThanEqualOrderByCapturedAtAsc(deviceId, finEffective);
    //     }
    //
    //     // Etat initial au bord de la fenetre : dernier evenement avant "debut".
    //     // Permet de compter correctement une coupure deja en cours a l'entree de la fenetre.
    //     LigneFixeState etatInitial = null;
    //     if (debut != null) {
    //         etatInitial = repo.findFirstByDeviceIdAndCapturedAtLessThanOrderByCapturedAtDesc(deviceId, debut)
    //                 .orElse(null);
    //     }
    //
    //     // Bornes reelles de l'historique quand aucune periode n'est demandee (pour la reponse)
    //     Instant debutReponse = debut;
    //     if (debutReponse == null && !evts.isEmpty()) {
    //         debutReponse = evts.get(0).getCapturedAt();
    //     }
    //
    //     long nbCoupures = 0;
    //     long dureeCoupure = 0;
    //     long dureePresence = 0;
    //     long plusLongueCoupure = 0;
    //     long plusLonguePresence = 0;
    //     Instant coupureEnCoursDepuis = null;
    //     List<CoupureResponse> coupures = new ArrayList<>();
    //
    //     if (!evts.isEmpty() || etatInitial != null) {
    //         // Points retenus : etat initial (si connu) + evenements de la fenetre
    //         List<LigneFixeState> points = new ArrayList<>();
    //         if (etatInitial != null) {
    //             points.add(etatInitial);
    //         }
    //         points.addAll(evts);
    //
    //         // Fusion des evenements de meme etat consecutifs (doublons possibles lors des renvois)
    //         List<LigneFixeState> reduits = new ArrayList<>();
    //         for (LigneFixeState p : points) {
    //             if (reduits.isEmpty() || reduits.get(reduits.size() - 1).isPresent() != p.isPresent()) {
    //                 reduits.add(p);
    //             }
    //         }
    //
    //         // Balayage des segments d'etat
    //         for (int i = 0; i < reduits.size(); i++) {
    //             boolean etat = reduits.get(i).isPresent();
    //
    //             Instant debutSegment;
    //             if (i == 0) {
    //                 // Premier segment : demarre a l'evenement, borne par le debut de fenetre
    //                 debutSegment = reduits.get(0).getCapturedAt();
    //                 if (debut != null && debutSegment.isBefore(debut)) {
    //                     debutSegment = debut; // etat initial connu avant la fenetre
    //                 }
    //             } else {
    //                 debutSegment = reduits.get(i).getCapturedAt();
    //             }
    //
    //             Instant finSegment = (i + 1 < reduits.size())
    //                     ? reduits.get(i + 1).getCapturedAt()
    //                     : finEffective;
    //
    //             long duree = Math.max(0, Duration.between(debutSegment, finSegment).getSeconds());
    //
    //             if (etat) {
    //                 dureePresence += duree;
    //                 plusLonguePresence = Math.max(plusLonguePresence, duree);
    //             } else {
    //                 nbCoupures++;
    //                 dureeCoupure += duree;
    //                 plusLongueCoupure = Math.max(plusLongueCoupure, duree);
    //
    //                 boolean enCours = (fin == null) && (i == reduits.size() - 1);
    //                 coupures.add(new CoupureResponse(debutSegment, enCours ? null : finSegment, duree));
    //                 if (enCours) {
    //                     coupureEnCoursDepuis = debutSegment;
    //                 }
    //             }
    //         }
    //     }
    //
    //     long dureeObservee = dureePresence + dureeCoupure;
    //     Double tauxPresence = dureeObservee > 0
    //             ? Math.round(dureePresence * 1000.0 / dureeObservee) / 10.0
    //             : null;
    //
    //     // Liste des coupures tronquee aux plus recentes si trop longue
    //     boolean tronquees = false;
    //     List<CoupureResponse> coupuresLimitees = coupures;
    //     if (coupures.size() > COUPURES_MAX_RENVOYEES) {
    //         coupuresLimitees = coupures.subList(coupures.size() - COUPURES_MAX_RENVOYEES, coupures.size());
    //         tronquees = true;
    //     }
    //
    //     return new StatsResponse(
    //             deviceId,
    //             debutReponse,
    //             finEffective,
    //             evts.size(),
    //             nbCoupures,
    //             dureeCoupure,
    //             dureePresence,
    //             dureeObservee,
    //             tauxPresence,
    //             plusLongueCoupure,
    //             plusLonguePresence,
    //             coupureEnCoursDepuis,
    //             coupuresLimitees,
    //             tronquees);
    // }

    @Transactional(readOnly = true)
    public Optional<LigneFixeState> dernier(String deviceId) {
        return repo.findFirstByDeviceIdOrderByCapturedAtDesc(deviceId);
    }

    public long epochSecondeActuelle() {
        return clock.instant().getEpochSecond();
    }
}

package com.projet.serveurmen2.repository;

import com.projet.serveurmen2.model.LigneFixeState;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
// Desactive (iteration future, route stats) : import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LigneFixeStateRepository extends JpaRepository<LigneFixeState, Long> {

    // --- Consultations existantes (route ESP32) ---
    List<LigneFixeState> findByDeviceId(String deviceId, Pageable pageable);
    Optional<LigneFixeState> findFirstByDeviceIdOrderByCapturedAtDesc(String deviceId);

    // --- Historique pagine pour les clients (du plus recent au plus ancien) ---
    Page<LigneFixeState> findByDeviceIdOrderByCapturedAtDesc(String deviceId, Pageable pageable);
    Page<LigneFixeState> findByDeviceIdAndCapturedAtGreaterThanEqualOrderByCapturedAtDesc(
            String deviceId, Instant debut, Pageable pageable);
    Page<LigneFixeState> findByDeviceIdAndCapturedAtLessThanEqualOrderByCapturedAtDesc(
            String deviceId, Instant fin, Pageable pageable);
    Page<LigneFixeState> findByDeviceIdAndCapturedAtGreaterThanEqualAndCapturedAtLessThanEqualOrderByCapturedAtDesc(
            String deviceId, Instant debut, Instant fin, Pageable pageable);

    // --- Evenements en ordre chronologique (calcul des statistiques / coupures) ---
    // Desactive : hors cahier des charges (iteration future). A reactiver avec la route
    // stats (controleur + LigneFixeService.stats() + DTOs StatsResponse/CoupureResponse).
    // List<LigneFixeState> findByDeviceIdAndCapturedAtGreaterThanEqualAndCapturedAtLessThanEqualOrderByCapturedAtAsc(
    //         String deviceId, Instant debut, Instant fin);
    // List<LigneFixeState> findByDeviceIdAndCapturedAtGreaterThanEqualOrderByCapturedAtAsc(
    //         String deviceId, Instant debut);
    // List<LigneFixeState> findByDeviceIdAndCapturedAtLessThanEqualOrderByCapturedAtAsc(
    //         String deviceId, Instant fin);
    // List<LigneFixeState> findByDeviceIdOrderByCapturedAtAsc(String deviceId);
    //
    // // Dernier evenement strictement anterieur a une date (etat initial au bord d'une fenetre)
    // Optional<LigneFixeState> findFirstByDeviceIdAndCapturedAtLessThanOrderByCapturedAtDesc(
    //         String deviceId, Instant reference);
    //
    // // Premier / dernier evenement connu (bornes reelles de l'historique)
    // Optional<LigneFixeState> findFirstByDeviceIdOrderByCapturedAtAsc(String deviceId);
    //
    // // --- Liste des appareils connus (desactive : iteration future, route /appareils) ---
    // @Query("select s.deviceId from LigneFixeState s group by s.deviceId order by s.deviceId")
    // List<String> findDeviceIds();
}

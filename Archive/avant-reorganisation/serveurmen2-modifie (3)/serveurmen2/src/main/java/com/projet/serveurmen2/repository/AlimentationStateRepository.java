package com.projet.serveurmen2.repository;

import com.projet.serveurmen2.model.AlimentationState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface AlimentationStateRepository extends JpaRepository<AlimentationState, Long> {

    // --- Source actuelle (dernier evenement connu) ; tri secondaire par id pour ordre deterministe ---
    Optional<AlimentationState> findFirstByDeviceIdOrderByCapturedAtDescIdDesc(String deviceId);

    // --- Historique pagine pour les clients (du plus recent au plus ancien) ---
    Page<AlimentationState> findByDeviceIdOrderByCapturedAtDescIdDesc(String deviceId, Pageable pageable);
    Page<AlimentationState> findByDeviceIdAndCapturedAtGreaterThanEqualOrderByCapturedAtDescIdDesc(
            String deviceId, Instant debut, Pageable pageable);
    Page<AlimentationState> findByDeviceIdAndCapturedAtLessThanEqualOrderByCapturedAtDescIdDesc(
            String deviceId, Instant fin, Pageable pageable);
    Page<AlimentationState> findByDeviceIdAndCapturedAtGreaterThanEqualAndCapturedAtLessThanEqualOrderByCapturedAtDescIdDesc(
            String deviceId, Instant debut, Instant fin, Pageable pageable);
}

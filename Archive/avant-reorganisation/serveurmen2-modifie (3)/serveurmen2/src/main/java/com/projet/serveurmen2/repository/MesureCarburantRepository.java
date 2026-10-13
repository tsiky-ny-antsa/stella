package com.projet.serveurmen2.repository;

import com.projet.serveurmen2.model.MesureCarburant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MesureCarburantRepository extends JpaRepository<MesureCarburant, Long> {

    // --- Derniere mesure connue (dashboard client) ; tri secondaire par id pour ordre deterministe ---
    Optional<MesureCarburant> findFirstByDeviceIdOrderByMesureAtDescIdDesc(String deviceId);

    // --- Serie temporelle : les N plus recentes, reordonnees chronologiquement par le service ---
    List<MesureCarburant> findByDeviceIdOrderByMesureAtDescIdDesc(String deviceId, Pageable pageable);
}

package com.projet.serveurmen2.repository;

import com.projet.serveurmen2.model.AlerteSecours;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlerteSecoursRepository extends JpaRepository<AlerteSecours, Long> {

    // --- Liste des alertes (du plus recent au plus ancien, plafonnee par limite) ;
    // tri secondaire par id pour un ordre deterministe en cas d'horodatages identiques ---
    List<AlerteSecours> findByDeviceIdOrderBySignaleeAtDescIdDesc(String deviceId, Pageable pageable);
    List<AlerteSecours> findByDeviceIdAndResolueAtIsNullOrderBySignaleeAtDescIdDesc(String deviceId, Pageable pageable);
    List<AlerteSecours> findByDeviceIdAndResolueAtIsNotNullOrderBySignaleeAtDescIdDesc(String deviceId, Pageable pageable);

    // --- Auto-resolution (toutes les alertes actives, sans plafond) ---
    List<AlerteSecours> findByDeviceIdAndResolueAtIsNull(String deviceId);
    List<AlerteSecours> findByDeviceIdAndTypeAndResolueAtIsNull(String deviceId, String type);
}

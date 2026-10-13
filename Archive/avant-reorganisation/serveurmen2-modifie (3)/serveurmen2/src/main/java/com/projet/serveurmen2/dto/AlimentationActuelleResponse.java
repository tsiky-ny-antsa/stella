package com.projet.serveurmen2.dto;

import tools.jackson.databind.JsonNode;
import com.projet.serveurmen2.model.AlimentationState;
import com.projet.serveurmen2.model.MesureCarburant;
import com.projet.serveurmen2.model.SourceAlimentation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Tableau de bord complet de l'alimentation d'un appareil, en une seule requete
 * pour le dashboard client : source actuelle, echeance de bascule, etat de la
 * ligne fixe, dernier niveau de carburant du groupe electrogene et alertes actives.
 */
public record AlimentationActuelleResponse(
        String deviceId,
        String sourceActuelle,
        String motif,
        Instant depuis,
        long ageSourceSecondes,
        // Renseignes seulement si la source actuelle est l'ONDULEUR (ligne temporaire) :
        // la bascule vers la ligne de secours est alors attendue a cet instant.
        // secondesAvantBascule peut etre negatif si le delai est depasse
        // (bascule tardive en attente que le secours redevienne operationnel).
        Instant basculePrevue,
        Long secondesAvantBascule,
        // Dernier etat connu de la ligne fixe (null = aucune donnee lignefixe)
        Boolean lignefixePresente,
        Instant lignefixeDepuis,
        // Dernier niveau de carburant du groupe electrogene (null = aucune mesure)
        CarburantResponse dernierCarburant,
        List<AlerteActive> alertesActives) {

    /** Alerte encore non resolue, pour affichage urgent sur le dashboard. */
    public record AlerteActive(Long id, String type, String message, JsonNode details, Instant signaleeAt) {}

    public static AlimentationActuelleResponse from(
            AlimentationState dernierEtat,
            Instant maintenant,
            long delaiBasculeSecondes,
            Boolean lignefixePresente,
            Instant lignefixeDepuis,
            MesureCarburant dernierCarburant,
            List<AlerteActive> alertesActives) {

        long ageSource = Duration.between(dernierEtat.getCapturedAt(), maintenant).getSeconds();
        if (ageSource < 0) {
            ageSource = 0;
        }

        Instant basculePrevue = null;
        Long secondesAvant = null;
        if (dernierEtat.getSource() == SourceAlimentation.ONDULEUR) {
            basculePrevue = dernierEtat.getCapturedAt().plusSeconds(delaiBasculeSecondes);
            secondesAvant = Duration.between(maintenant, basculePrevue).getSeconds();
        }

        CarburantResponse carburant = (dernierCarburant != null)
                ? CarburantResponse.from(dernierCarburant, maintenant)
                : null;

        return new AlimentationActuelleResponse(
                dernierEtat.getDeviceId(),
                dernierEtat.getSource().name(),
                dernierEtat.getMotif(),
                dernierEtat.getCapturedAt(),
                ageSource,
                basculePrevue,
                secondesAvant,
                lignefixePresente,
                lignefixeDepuis,
                carburant,
                alertesActives);
    }
}

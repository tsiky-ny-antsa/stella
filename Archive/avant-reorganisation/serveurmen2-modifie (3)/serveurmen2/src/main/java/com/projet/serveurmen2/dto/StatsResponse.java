// =====================================================================
// DESACTIVE : hors cahier des charges (iteration future).
// Associe a la route GET /api/etats/{deviceId}/stats, actuellement
// commentee (EtatController, LigneFixeService.stats(), requetes
// chronologiques du repository, DTO CoupureResponse).
// Pour reactiver : decommenter tout ce fichier ainsi que les points
// listes ci-dessus.
// =====================================================================
// package com.projet.serveurmen2.dto;
//
// import java.time.Instant;
// import java.util.List;
//
// /**
//  * Statistiques d'un appareil sur une periode.
//  *
//  * Champs principaux :
//  * - nbEvenements           : nombre d'etats enregistres dans la periode
//  * - nbCoupures             : nombre de segments sans secteur (present = false)
//  * - dureeCoupureTotale...  : temps cumule sans secteur
//  * - dureePresenceTotale... : temps cumule avec secteur
//  * - dureeObservee...       : somme des deux (fenetre reellement couverte par des donnees)
//  * - tauxPresencePourcent   : 100 * dureePresence / dureeObservee (null si aucune donnee)
//  * - coupures               : detail des coupures (liste tronquee si trop longue)
//  */
// public record StatsResponse(
//         String deviceId,
//         Instant debut,
//         Instant fin,
//         long nbEvenements,
//         long nbCoupures,
//         long dureeCoupureTotaleSecondes,
//         long dureePresenceTotaleSecondes,
//         long dureeObserveeSecondes,
//         Double tauxPresencePourcent,
//         long plusLongueCoupureSecondes,
//         long plusLonguePresenceSecondes,
//         Instant coupureEnCoursDepuis,
//         List<CoupureResponse> coupures,
//         boolean coupuresTronquees) {
// }

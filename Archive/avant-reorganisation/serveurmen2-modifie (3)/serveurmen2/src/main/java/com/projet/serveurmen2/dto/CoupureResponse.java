// =====================================================================
// DESACTIVE : hors cahier des charges (iteration future).
// Associe a la route GET /api/etats/{deviceId}/stats, actuellement
// commentee (EtatController, LigneFixeService.stats(), requetes
// chronologiques du repository, DTO StatsResponse).
// Pour reactiver : decommenter tout ce fichier ainsi que les points
// listes ci-dessus.
// =====================================================================
// package com.projet.serveurmen2.dto;
//
// import java.time.Instant;
//
// /**
//  * Une coupure de la ligne fixe : debut, fin et duree.
//  * fin == null signifie : coupure encore en cours au moment de la requete.
//  */
// public record CoupureResponse(
//         Instant debut,
//         Instant fin,
//         long dureeSecondes) {
// }

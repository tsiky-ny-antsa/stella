// =====================================================================
// DESACTIVE : hors cahier des charges pour le moment (un seul ESP32).
// Associe a la route GET /api/etats/appareils, actuellement commentee
// (EtatController, LigneFixeService.appareils(), findDeviceIds() du
// repository). Pour reactiver dans une iteration future : decommenter
// tout ce fichier ainsi que les points listes ci-dessus.
// =====================================================================
// package com.projet.serveurmen2.dto;
//
// import com.projet.serveurmen2.model.LigneFixeState;
//
// import java.time.Instant;
//
// /**
//  * Resume d'un appareil connu : identifiant + dernier etat enregistre.
//  * Utilise par la route /api/etats/appareils.
//  */
// public record AppareilResponse(
//         String deviceId,
//         boolean present,
//         Instant capturedAt,
//         Instant receivedAt) {
//
//     public static AppareilResponse from(LigneFixeState s) {
//         return new AppareilResponse(s.getDeviceId(), s.isPresent(), s.getCapturedAt(), s.getReceivedAt());
//     }
// }

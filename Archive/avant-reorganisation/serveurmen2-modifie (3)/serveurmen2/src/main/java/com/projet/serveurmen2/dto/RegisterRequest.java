// =====================================================================
// DESACTIVE : l'inscription libre (POST /api/auth/register) a ete
// commentee — les comptes sont crees uniquement par un ADMIN via
// POST /api/users. Pour reactiver : decommenter ce fichier ainsi que
// AuthController.enregistrer(), AuthService.enregistrer(),
// ROUTE_REGISTER (ApiConstants) et les imports associes.
// =====================================================================
// package com.projet.serveurmen2.dto;
//
// import jakarta.validation.constraints.Email;
// import jakarta.validation.constraints.NotBlank;
// import jakarta.validation.constraints.Pattern;
// import jakarta.validation.constraints.Size;
//
// /**
//  * Corps du POST /api/auth/register (creation d'un compte client standard).
//  * Le role n'est PAS accepte ici : tout auto-enregistrement cree un USER.
//  * Les admins se creent via POST /api/users (reserve ADMIN).
//  */
// public record RegisterRequest(
//         @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String username,
//         @NotBlank @Email @Size(max = 128) String email,
//         @NotBlank @Size(min = 6, max = 128) String password) {
// }

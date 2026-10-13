package com.example.myapplication;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client HTTP de l'application vers le serveur Spring Boot "serveurmen2"
 * (installé sur le PC de l'utilisateur, port 8081, SQLite, JWT).
 *
 * CONTRAT RÉEL DU SERVEUR (voir CONTRAT_API.txt) :
 *   POST  /api/auth/login                       {identifiant, password} → {token, user{role}}
 *   GET   /api/auth/me                          → profil (Bearer)
 *   GET   /api/users                            → [ {id,username,email,role} ]   (ADMIN, RESPONSABLE)
 *   POST  /api/users                            → création de compte             (ADMIN, RESPONSABLE*)
 *   DELETE /api/users/{id}                      → suppression de compte          (ADMIN)
 *   GET   /api/etats/{deviceId}/actuel          → {present, capturedAt, ...}
 *   GET   /api/etats/{deviceId}/historique      → {contenu:[...], ...}
 *   GET   /api/etats/{deviceId}/alimentation    → tableau de bord complet
 *   GET   /api/etats/{deviceId}/alimentation/historique → timeline des sources
 *   GET   /api/etats/{deviceId}/alertes         → [ alertes ]
 *   GET   /api/etats/{deviceId}/carburant       → [ mesures ]
 *
 * Rôles (trois niveaux, décision équipe client) :
 *   ADMIN = tout ; RESPONSABLE = consultation + créer USER/RESPONSABLE
 *   (ni ADMIN ni suppression) ; USER = consultation seule.
 *   (*) Anti-élévation serveur : seul un ADMIN peut créer un compte ADMIN.
 *
 * Authentification : toutes les requêtes protégées envoient l'en-tête
 * "Authorization: Bearer <token>" reçu du login. Les réponses d'erreur du
 * serveur sont des JSON {erreur, details} avec le code HTTP adéquat
 * (400/401/403/404/409). En cas d'échec réseau, l'app bascule en mode démo.
 */
public final class ApiClient {

    /** Adresse par défaut (placeholder) — modifiable dans l'app, écran de connexion. */
    public static final String URL_BASE_DEFAUT = "http://192.168.1.100:8081";

    /** Appareil par défaut interrogé (ESP32) — modifiable dans l'app. */
    public static final String DEVICE_ID_DEFAUT = "esp32-01";

    /** Délais courts : si le PC est éteint, on bascule vite en mode démo. */
    private static final int TIMEOUT_MS = 2500;

    private static volatile String urlBase = URL_BASE_DEFAUT;
    private static volatile String deviceId = DEVICE_ID_DEFAUT;
    private static volatile String token = null;   // JWT reçu du login

    private static final ExecutorService EXEC = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Callback exécuté sur le thread principal. code = -1 si échec réseau. */
    public interface ApiCallback {
        void onSuccess(int code, String body);
        void onError(int code, String message);
    }

    /**
     * Écouteur de session expirée : appelé sur le thread principal quand le
     * serveur renvoie 401 pour une requête PROTEGÉE (token absent, invalide ou
     * expiré). Le 401 du LOGIN n'est PAS concerné (ce sont des identifiants
     * incorrects, pas une session expirée). Les écrans l'enregistrent dans
     * onCreate pour rediriger vers l'écran de connexion, et le retirent dans
     * onDestroy (évite les fuites d'activité).
     */
    public interface OnSessionExpiree {
        void onSessionExpiree();
    }

    private static volatile OnSessionExpiree onSessionExpiree = null;

    /** Enregistre (ou retire avec null) l'écouteur de session expirée. */
    public static void setOnSessionExpiree(OnSessionExpiree l) { onSessionExpiree = l; }

    private ApiClient() { }

    // ------------------------------------------------------------------
    // CONFIGURATION (modifiable dans l'app, écran de connexion)
    // ------------------------------------------------------------------
    public static void setUrlBase(String url) {
        if (url == null) return;
        url = url.trim();
        if (url.isEmpty()) url = URL_BASE_DEFAUT;
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        urlBase = url;
    }

    public static String getUrlBase() { return urlBase; }

    public static void setDeviceId(String id) {
        if (id == null) return;
        id = id.trim();
        deviceId = id.isEmpty() ? DEVICE_ID_DEFAUT : id;
    }

    public static String getDeviceId() { return deviceId; }

    public static void setToken(String t) { token = (t == null || t.isEmpty()) ? null : t; }
    public static String getToken() { return token; }

    // ------------------------------------------------------------------
    // AUTHENTIFICATION & COMPTES
    // ------------------------------------------------------------------

    /** POST /api/auth/login — identifiant = username OU email. */
    public static void login(String identifiant, String password, ApiCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("identifiant", identifiant);
            json.put("password", password);
            request("api/auth/login", "POST", json.toString(), cb);
        } catch (Exception e) {
            postErreur(cb, -1, "Erreur interne");
        }
    }

    /** GET /api/auth/me — profil du compte connecté (sert à l'auto-reconnexion). */
    public static void profil(ApiCallback cb) {
        request("api/auth/me", "GET", null, cb);
    }

    /** GET /api/users — liste des comptes (ADMIN et RESPONSABLE). */
    public static void listeUsers(ApiCallback cb) {
        request("api/users", "GET", null, cb);
    }

    /**
     * POST /api/users — création d'un compte (ADMIN et RESPONSABLE).
     * role = "USER" | "RESPONSABLE" | "ADMIN" ; absent → USER.
     * Anti-élévation serveur : seul un ADMIN peut créer un ADMIN (403 sinon).
     */
    public static void creerUtilisateur(String username, String email, String password,
                                        String role, ApiCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("username", username);
            json.put("email", email);
            json.put("password", password);
            if (role != null && !role.isEmpty()) json.put("role", role);
            request("api/users", "POST", json.toString(), cb);
        } catch (Exception e) {
            postErreur(cb, -1, "Erreur interne");
        }
    }

    /** DELETE /api/users/{id} — suppression d'un compte (ADMIN uniquement, 403 sinon). */
    public static void supprimerUtilisateur(long id, ApiCallback cb) {
        request("api/users/" + id, "DELETE", null, cb);
    }

    // ------------------------------------------------------------------
    // ÉTATS DU SYSTÈME (routes /api/etats/{deviceId})
    // ------------------------------------------------------------------

    /** GET /api/etats/{deviceId}/alimentation — tableau de bord complet. */
    public static void alimentation(ApiCallback cb) {
        request("api/etats/" + deviceId + "/alimentation", "GET", null, cb);
    }

    /** GET /api/etats/{deviceId}/historique — historique de la ligne fixe (paginé). */
    public static void historiqueLigneFixe(int taille, ApiCallback cb) {
        request("api/etats/" + deviceId + "/historique?page=0&taille=" + taille,
                "GET", null, cb);
    }

    /** GET /api/etats/{deviceId}/alimentation/historique — timeline des sources (paginée). */
    public static void historiqueSources(int taille, ApiCallback cb) {
        request("api/etats/" + deviceId + "/alimentation/historique?page=0&taille=" + taille,
                "GET", null, cb);
    }

    /**
     * Interroge le serveur et applique les données réelles à PowerSystem.
     * Séquence : alimentation (dashboard) → historique ligne → timeline sources.
     * Si la 1re requête échoue (serveur éteint), on bascule en mode démo et
     * onDone est appelé immédiatement. onDone est TOUJOURS appelé à la fin.
     */
    public static void refreshPowerSystem(Runnable onDone) {
        alimentation(new ApiCallback() {
            @Override
            public void onSuccess(int code, String body) {
                try {
                    JSONObject o = new JSONObject(body);
                    Boolean lignePresente = o.isNull("lignefixePresente")
                            ? null : o.optBoolean("lignefixePresente");

                    Double carburant = null;
                    String carburantAt = null;
                    JSONObject car = o.optJSONObject("dernierCarburant");
                    if (car != null && !car.isNull("niveauPourcent")) {
                        carburant = car.optDouble("niveauPourcent");
                        carburantAt = PowerSystem.dateLocale(car.optString("mesureAt", null));
                    }

                    List<String> alertes = new ArrayList<>();
                    JSONArray tab = o.optJSONArray("alertesActives");
                    if (tab != null) {
                        for (int i = 0; i < tab.length(); i++) {
                            JSONObject a = tab.getJSONObject(i);
                            String ligne = a.optString("type", "ALERTE")
                                    + " — " + a.optString("message", "");
                            String quand = PowerSystem.dateLocale(a.optString("signaleeAt", null));
                            if (quand != null) ligne += " (" + quand + ")";
                            alertes.add(ligne);
                        }
                    }

                    PowerSystem.applyServeurAlimentation(
                            o.optString("sourceActuelle", null),
                            o.optString("motif", null),
                            PowerSystem.dateLocale(o.optString("depuis", null)),
                            o.isNull("ageSourceSecondes") ? null : o.optLong("ageSourceSecondes"),
                            o.isNull("secondesAvantBascule") ? null : o.optLong("secondesAvantBascule"),
                            lignePresente,
                            PowerSystem.dateLocale(o.optString("lignefixeDepuis", null)),
                            carburant, carburantAt, alertes);

                    chargerHistoriques(() -> {
                        if (onDone != null) onDone.run();
                    });
                } catch (Exception e) {
                    PowerSystem.setErreurServeur("réponse illisible");
                    if (onDone != null) onDone.run();
                }
            }

            @Override
            public void onError(int code, String message) {
                PowerSystem.setErreurServeur(message);
                if (onDone != null) onDone.run();
            }
        });
    }

    /** Charge les deux historiques (tolérants aux échecs individuels). */
    private static void chargerHistoriques(Runnable onDone) {
        historiqueLigneFixe(20, new ApiCallback() {
            @Override
            public void onSuccess(int code, String body) {
                List<String> lignes = new ArrayList<>();
                try {
                    JSONArray contenu = new JSONObject(body).optJSONArray("contenu");
                    if (contenu != null) {
                        for (int i = 0; i < contenu.length(); i++) {
                            JSONObject e = contenu.getJSONObject(i);
                            lignes.add(PowerSystem.dateLocale(e.optString("capturedAt", null))
                                    + " — Ligne fixe "
                                    + (e.optBoolean("present") ? "présente" : "ABSENTE (coupure)"));
                        }
                    }
                } catch (Exception ignored) { }
                PowerSystem.setHistoriqueLigne(lignes);
                chargerTimeline(onDone);
            }

            @Override
            public void onError(int code, String message) {
                chargerTimeline(onDone);
            }
        });
    }

    private static void chargerTimeline(Runnable onDone) {
        historiqueSources(20, new ApiCallback() {
            @Override
            public void onSuccess(int code, String body) {
                List<String> lignes = new ArrayList<>();
                try {
                    JSONArray contenu = new JSONObject(body).optJSONArray("contenu");
                    if (contenu != null) {
                        for (int i = 0; i < contenu.length(); i++) {
                            JSONObject e = contenu.getJSONObject(i);
                            String motif = e.optString("motif", "");
                            lignes.add(PowerSystem.dateLocale(e.optString("capturedAt", null))
                                    + " — Source : "
                                    + PowerSystem.sourceLibelle(e.optString("source", ""))
                                    + (motif.isEmpty() ? "" : " (" + motif + ")"));
                        }
                    }
                } catch (Exception ignored) { }
                PowerSystem.setTimelineSources(lignes);
                if (onDone != null) onDone.run();
            }

            @Override
            public void onError(int code, String message) {
                if (onDone != null) onDone.run();
            }
        });
    }

    // ------------------------------------------------------------------
    // AIDES
    // ------------------------------------------------------------------

    /** Extrait un message lisible d'une réponse d'erreur {erreur, details}. */
    public static String messageErreur(String body, String defaut) {
        try {
            JSONObject o = new JSONObject(body);
            String erreur = o.optString("erreur", "");
            String details = o.optString("details", "");
            if (!erreur.isEmpty() && !details.isEmpty()) return erreur + " : " + details;
            if (!erreur.isEmpty()) return erreur;
            if (!details.isEmpty()) return details;
        } catch (Exception ignored) { }
        return defaut;
    }

    // ------------------------------------------------------------------
    // MOTEUR HTTP (HttpURLConnection natif — aucune dépendance externe)
    // ------------------------------------------------------------------

    private static void request(String cheminAvecQuery, String method,
                                String jsonBody, ApiCallback cb) {
        EXEC.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlBase + "/" + cheminAvecQuery);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestMethod(method);
                conn.setUseCaches(false);
                conn.setRequestProperty("Accept", "application/json");
                String t = token;
                if (t != null) conn.setRequestProperty("Authorization", "Bearer " + t);

                if (jsonBody != null) {
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setDoOutput(true);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                    }
                }

                int code = conn.getResponseCode();
                InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                StringBuilder sb = new StringBuilder();
                if (is != null) {
                    try (BufferedReader r = new BufferedReader(
                            new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = r.readLine()) != null) sb.append(line);
                    }
                }
                String response = sb.toString();
                if (code >= 200 && code < 300) {
                    MAIN.post(() -> cb.onSuccess(code, response));
                } else {
                    String msg = code == 401 ? "Session expirée ou token invalide"
                            : code == 403 ? "Accès refusé (rôle insuffisant)"
                            : messageErreur(response, "Erreur " + code);
                    MAIN.post(() -> {
                        // Session expirée sur une requête protégée (PAS le login) :
                        // on prévient l'écran courant pour qu'il renvoie au login.
                        if (code == 401 && !cheminAvecQuery.startsWith("api/auth/login")
                                && onSessionExpiree != null) {
                            onSessionExpiree.onSessionExpiree();
                        }
                        cb.onError(code, msg);
                    });
                }
            } catch (Exception e) {
                postErreur(cb, -1, "Serveur inaccessible (vérifiez l'adresse, "
                        + "le Wi-Fi et que le serveur est démarré)");
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static void postErreur(ApiCallback cb, int code, String message) {
        MAIN.post(() -> cb.onError(code, message));
    }
}

package mg.stella.mobile.auth;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import mg.stella.mobile.BuildConfig;

/**
 * Session locale : jeton d'accès, profil, URL serveur, équipement sélectionné, dernier état en cache.
 * Aucun secret d'appareil ni de backend n'est stocké. Le rôle stocké ne sert qu'à l'affichage :
 * les permissions sont toujours vérifiées par le backend.
 */
public class SessionManager {
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_OPERATEUR = "OPERATEUR";
    public static final String ROLE_LECTURE = "LECTURE";

    private static final String PREFS = "stella_session";
    private final SharedPreferences p;

    public SessionManager(Context ctx) {
        p = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getBaseUrl() {
        return p.getString("base_url", BuildConfig.DEFAULT_BASE_URL);
    }

    public void setBaseUrl(String url) {
        String u = url == null ? "" : url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (!u.isEmpty()) p.edit().putString("base_url", u).apply();
    }

    public void saveLogin(String token, long expiresInSeconds, JSONObject user) {
        long expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000L;
        p.edit()
                .putString("token", token)
                .putLong("expires_at", expiresAt)
                .putString("username", user == null ? null : user.optString("username", null))
                .putString("display_name", user == null ? null : user.optString("display_name", null))
                .putString("role", user == null ? null : user.optString("role", null))
                .remove("device_id")
                .remove("status_cache")
                .apply();
    }

    public boolean isLoggedIn() {
        return getToken() != null && System.currentTimeMillis() < p.getLong("expires_at", 0L);
    }

    public String getToken() { return p.getString("token", null); }
    public String getUsername() { return p.getString("username", null); }
    public String getDisplayName() {
        String d = p.getString("display_name", null);
        return d != null ? d : getUsername();
    }
    public String getRole() { return p.getString("role", null); }

    public boolean isAdmin() { return ROLE_ADMIN.equals(getRole()); }

    /** Affichage seulement : le backend reste l'autorité. */
    public boolean canCommand() {
        return ROLE_ADMIN.equals(getRole()) || ROLE_OPERATEUR.equals(getRole());
    }

    public boolean canAckAlerts() { return canCommand(); }

    public String getDeviceId() { return p.getString("device_id", null); }
    public void setDeviceId(String id) { p.edit().putString("device_id", id).apply(); }

    public void saveStatusCache(String json) { p.edit().putString("status_cache", json).apply(); }
    public String getStatusCache() { return p.getString("status_cache", null); }

    /** Déconnexion : efface jeton, profil et cache ; conserve l'URL serveur. */
    public void clear() {
        p.edit()
                .remove("token").remove("expires_at").remove("username")
                .remove("display_name").remove("role").remove("device_id").remove("status_cache")
                .apply();
    }
}

package com.example.myapplication;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

/**
 * Écran de connexion. Interroge le serveur Spring Boot du PC :
 *   POST /api/auth/login {identifiant, password} → {token, user{role}}
 * (identifiant = nom d'utilisateur OU email). Rôle renvoyé par le serveur
 * (trois niveaux, décision équipe client) :
 *   ADMIN → tableau de bord complet + gestion des comptes ;
 *   RESPONSABLE → tableau de bord + gestion partielle des comptes ;
 *   USER → consultation (lecture seule).
 * Si un token de session existe déjà et est encore valide (GET /api/auth/me),
 * l'application se reconnecte automatiquement. Si le serveur est éteint,
 * repli sur les comptes de démonstration locaux.
 */
public class MainActivity extends AppCompatActivity {

    /** Clé d'extra pour transmettre le nom d'utilisateur aux dashboards. */
    public static final String EXTRA_USERNAME = "extra_username";

    /** Clé d'extra pour transmettre le rôle (code serveur) aux dashboards. */
    public static final String EXTRA_ROLE = "extra_role";

    /** Extra posé quand on revient ici après une session expirée (401). */
    public static final String EXTRA_SESSION_EXPIREE = "extra_session_expiree";

    /** Préférences locales (adresse serveur, deviceId, session). */
    private static final String PREFS = "energy_command";

    private TextInputLayout tilIdentifiant, tilPassword;
    private TextInputEditText etIdentifiant, etPassword;
    private MaterialButton btnLogin;
    private ImageButton btnToggleTheme;
    private TextView tvServeur;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Liaison des vues
        tilIdentifiant = findViewById(R.id.tilIdentifiant);
        tilPassword = findViewById(R.id.tilPassword);
        etIdentifiant = findViewById(R.id.etIdentifiant);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnToggleTheme = findViewById(R.id.btnToggleTheme);
        tvServeur = findViewById(R.id.tvServeur);

        // Configuration serveur mémorisée du dernier lancement
        ApiClient.setUrlBase(prefs().getString("url_serveur", ApiClient.URL_BASE_DEFAUT));
        ApiClient.setDeviceId(prefs().getString("device_id", ApiClient.DEVICE_ID_DEFAUT));
        ApiClient.setToken(prefs().getString("token", null));
        tvServeur.setOnClickListener(v -> showServeurDialog());

        // Retour après une session expirée (401 sur une requête protégée)
        if (getIntent().getBooleanExtra(EXTRA_SESSION_EXPIREE, false)) {
            Toast.makeText(this, R.string.msg_session_expiree, Toast.LENGTH_LONG).show();
        } else if (ApiClient.getToken() != null) {
            // Auto-reconnexion : token mémorisé encore valide ? (GET /api/auth/me)
            verifierTokenExistant();
        }

        // Bascule jour / nuit : l'activité est recréée automatiquement
        btnToggleTheme.setOnClickListener(v -> {
            if (isNightMode()) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            }
        });

        // Connexion : validation puis navigation vers l'interface du rôle
        btnLogin.setOnClickListener(v -> attemptLogin());
        // Touche « OK / Terminé » du clavier sur le mot de passe = connexion
        // (le bouton peut être recouvert par le clavier pendant la saisie)
        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            attemptLogin();
            return true;
        });
    }

    /**
     * Token mémorisé au démarrage : vérifié auprès du serveur (GET /api/auth/me).
     * Valide → navigation directe vers l'interface du rôle. Invalide/expiré →
     * simple nettoyage (le formulaire reste utilisable, sans message alarmant).
     */
    private void verifierTokenExistant() {
        btnLogin.setEnabled(false);
        ApiClient.profil(new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(int code, String body) {
                btnLogin.setEnabled(true);
                try {
                    JSONObject o = new JSONObject(body);
                    String role = o.optString("role", User.ROLE_USER);
                    String username = o.optString("username", "");
                    if (!username.isEmpty()) navigate(role, username);
                } catch (Exception ignored) { }
            }

            @Override
            public void onError(int code, String message) {
                btnLogin.setEnabled(true);
                if (code == 401) {
                    // Token expiré/invalide : on le retire, l'utilisateur se reconnecte
                    ApiClient.setToken(null);
                    prefs().edit().remove("token").apply();
                }
                // autre erreur (serveur éteint…) : formulaire normal, sans blocage
            }
        });
    }

    private void attemptLogin() {
        tilIdentifiant.setError(null);
        tilPassword.setError(null);

        String identifiant = text(etIdentifiant);
        String password = text(etPassword);

        boolean valid = true;
        if (TextUtils.isEmpty(identifiant)) {
            tilIdentifiant.setError(getString(R.string.err_identifiant_requis));
            valid = false;
        }
        if (TextUtils.isEmpty(password)) {
            tilPassword.setError(getString(R.string.err_mdp_requis));
            valid = false;
        }
        if (!valid) return;

        // Verrou anti double-clic : une seule requête de login à la fois
        btnLogin.setEnabled(false);

        // 1) Tente le serveur réel (POST /api/auth/login)
        ApiClient.login(identifiant, password, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(int code, String body) {
                btnLogin.setEnabled(true);
                try {
                    JSONObject o = new JSONObject(body);
                    JSONObject user = o.optJSONObject("user");
                    String role = user != null
                            ? user.optString("role", User.ROLE_USER) : User.ROLE_USER;
                    String username = user != null
                            ? user.optString("username", identifiant) : identifiant;

                    // Session mémorisée : token JWT + profil
                    prefs().edit()
                            .putString("token", o.optString("token", null))
                            .putString("session_username", username)
                            .putString("session_role", role)
                            .putString("session_email",
                                    user != null ? user.optString("email", "") : "")
                            .apply();
                    ApiClient.setToken(o.optString("token", null));

                    Toast.makeText(MainActivity.this,
                            R.string.msg_connexion_ok, Toast.LENGTH_SHORT).show();
                    navigate(role, username);
                } catch (Exception e) {
                    useLocalFallback();
                }
            }

            @Override
            public void onError(int code, String message) {
                btnLogin.setEnabled(true);
                if (code == 401) {
                    // Serveur joignable mais identifiants refusés
                    tilPassword.setError(getString(R.string.err_identifiants));
                    Toast.makeText(MainActivity.this,
                            R.string.msg_identifiants_invalides, Toast.LENGTH_LONG).show();
                } else {
                    // 2) Serveur éteint / injoignable → comptes de démonstration
                    useLocalFallback();
                }
            }
        });
    }

    /** Repli : authentification sur les comptes de démo embarqués. */
    private void useLocalFallback() {
        User user = UserStore.authenticateParIdentifiant(
                text(etIdentifiant), text(etPassword));
        if (user == null) {
            tilPassword.setError(getString(R.string.err_identifiants));
            Toast.makeText(this, R.string.msg_mode_demo, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, R.string.msg_mode_demo, Toast.LENGTH_SHORT).show();
        navigate(user.role, user.username);
    }

    /** Ouvre l'interface correspondant au rôle renvoyé par le serveur (ou la démo). */
    private void navigate(String role, String username) {
        Intent intent;
        if (User.estAdmin(role) || User.estResponsable(role)) {
            // ADMIN : dashboard complet + gestion des comptes (tout) ;
            // RESPONSABLE : même tableau de bord, gestion partielle côté écran
            // de comptes (pas de suppression, pas de création d'ADMIN — le
            // serveur renverrait 403). Le rôle est transmis pour adapter l'écran.
            intent = new Intent(this, AdminDashboardActivity.class);
            intent.putExtra(EXTRA_ROLE, role);
        } else {
            // USER (serveur, consultation seule) et comptes de démo restants
            intent = new Intent(this, ObservationActivity.class);
            intent.putExtra(EXTRA_ROLE, role);
        }
        intent.putExtra(EXTRA_USERNAME, username);
        startActivity(intent);
        finish(); // empêche le retour au formulaire avec le bouton Back
    }

    /**
     * Boîte de dialogue : adresse du serveur + deviceId de l'ESP32 à observer.
     * URL vide = valeur par défaut. Le téléphone et le PC doivent être sur le
     * même réseau Wi-Fi (serveur Spring Boot sur le port 8081).
     */
    private void showServeurDialog() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        wrap.setPadding(pad, pad / 2, pad, 0);

        final EditText inputUrl = new EditText(this);
        inputUrl.setSingleLine(true);
        inputUrl.setText(ApiClient.getUrlBase());
        inputUrl.setHint(R.string.hint_url_serveur);
        wrap.addView(inputUrl);

        final EditText inputDevice = new EditText(this);
        inputDevice.setSingleLine(true);
        inputDevice.setText(ApiClient.getDeviceId());
        inputDevice.setHint(R.string.hint_device_id);
        wrap.addView(inputDevice);

        new AlertDialog.Builder(this)
                .setTitle(R.string.titre_serveur)
                .setMessage(R.string.msg_aide_serveur)
                .setView(wrap)
                .setPositiveButton(R.string.btn_enregistrer, (d, w) -> {
                    String url = inputUrl.getText().toString().trim();
                    String device = inputDevice.getText().toString().trim();
                    if (!url.isEmpty()
                            && !url.startsWith("http://") && !url.startsWith("https://")) {
                        Toast.makeText(this, R.string.err_url_invalide, Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (url.isEmpty()) url = ApiClient.URL_BASE_DEFAUT;
                    if (device.isEmpty()) device = ApiClient.DEVICE_ID_DEFAUT;

                    prefs().edit()
                            .putString("url_serveur", url)
                            .putString("device_id", device)
                            .apply();
                    ApiClient.setUrlBase(url);
                    ApiClient.setDeviceId(device);
                    updateServeurLabel();
                    Toast.makeText(this, R.string.msg_url_enregistree, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateServeurLabel() {
        tvServeur.setText(getString(R.string.serveur_format,
                ApiClient.getUrlBase(), ApiClient.getDeviceId()));
    }

    private android.content.SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private String text(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private boolean isNightMode() {
        int uiMode = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Met à jour l'icône du bouton et l'adresse serveur affichée
        btnToggleTheme.setImageResource(isNightMode()
                ? R.drawable.ic_moon
                : R.drawable.ic_sun);
        updateServeurLabel();
    }
}

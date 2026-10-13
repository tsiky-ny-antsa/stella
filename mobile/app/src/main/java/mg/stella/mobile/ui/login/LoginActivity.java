package mg.stella.mobile.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;
import org.json.JSONObject;

import mg.stella.mobile.BuildConfig;
import mg.stella.mobile.R;
import mg.stella.mobile.api.ApiClient;
import mg.stella.mobile.api.ApiException;
import mg.stella.mobile.auth.SessionManager;
import mg.stella.mobile.ui.ErrorMessages;
import mg.stella.mobile.ui.MainActivity;
import mg.stella.mobile.util.Json;

/** Connexion identifiant / mot de passe. Le bouton est désactivé pendant la requête. */
public class LoginActivity extends AppCompatActivity {
    public static final String EXTRA_EXPIRED = "expired";

    private SessionManager session;
    private ApiClient api;
    private TextInputEditText username, password, server;
    private TextView error;
    private MaterialButton button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        api = ApiClient.get(this);

        if (session.isLoggedIn() && !getIntent().getBooleanExtra(EXTRA_EXPIRED, false)) {
            openMain();
            return;
        }

        setContentView(R.layout.activity_login);
        username = findViewById(R.id.login_username);
        password = findViewById(R.id.login_password);
        server = findViewById(R.id.login_server);
        error = findViewById(R.id.login_error);
        button = findViewById(R.id.login_button);

        // URL serveur modifiable en développement seulement (BuildConfig.ALLOW_SERVER_EDIT).
        if (BuildConfig.ALLOW_SERVER_EDIT) {
            findViewById(R.id.login_server_layout).setVisibility(View.VISIBLE);
            server.setText(session.getBaseUrl());
        }
        if (getIntent().getBooleanExtra(EXTRA_EXPIRED, false)) {
            showError(getString(R.string.err_session_expired));
        }

        button.setOnClickListener(v -> attemptLogin());
        password.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                attemptLogin();
                return true;
            }
            return false;
        });
    }

    private void attemptLogin() {
        String u = username.getText() == null ? "" : username.getText().toString().trim();
        String p = password.getText() == null ? "" : password.getText().toString();
        if (u.isEmpty() || p.isEmpty()) {
            showError(getString(R.string.login_empty_fields));
            return;
        }
        if (BuildConfig.ALLOW_SERVER_EDIT && server.getText() != null) {
            session.setBaseUrl(server.getText().toString());
        }
        setLoading(true);
        JSONObject body = new JSONObject();
        try {
            body.put("username", u);
            body.put("password", p);
        } catch (JSONException e) {
            setLoading(false);
            return;
        }
        api.postPublic("/auth/login", body, new ApiClient.Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject r) {
                String token = Json.str(r, "access_token");
                if (token == null) {
                    setLoading(false);
                    showError(getString(R.string.err_bad_response));
                    return;
                }
                session.saveLogin(token, r.optLong("expires_in", 3600L), r.optJSONObject("user"));
                loadDevicesThenOpen();
            }

            @Override public void onError(ApiException e) {
                setLoading(false);
                showError(ErrorMessages.of(LoginActivity.this, e));
            }
        });
    }

    /** Après connexion : profil (réponse login) puis liste des équipements, comme décrit dans DESIGN_MOBILE. */
    private void loadDevicesThenOpen() {
        api.get("/devices", new ApiClient.Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject r) {
                JSONObject d = Json.items(r).optJSONObject(0);
                if (d != null) {
                    String id = Json.str(d, "device_id");
                    if (id == null) id = Json.str(d, "id");
                    if (id != null) session.setDeviceId(id);
                }
                openMain();
            }

            @Override public void onError(ApiException e) {
                // Le tableau de bord réessaiera : l'échec de cette précharge ne bloque pas l'entrée.
                openMain();
            }
        });
    }

    private void setLoading(boolean loading) {
        button.setEnabled(!loading);
        button.setText(loading ? R.string.login_loading : R.string.login_button);
        if (loading) error.setVisibility(View.GONE);
    }

    private void showError(String msg) {
        error.setText(msg);
        error.setVisibility(View.VISIBLE);
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}

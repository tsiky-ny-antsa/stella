package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Interface 2 : consultation en LECTURE SEULE (comptes USER du serveur,
 * "clients en consultation seule" — décision équipe client). Voit : source
 * d'alimentation, ligne fixe JIRAMA, carburant, alertes et historiques.
 * Ne peut RIEN commander : aucun bouton de commande n'est présent.
 */
public class ObservationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_observation);

        String username = getIntent().getStringExtra(MainActivity.EXTRA_USERNAME);
        if (username == null || username.isEmpty()) username = "client";
        ((TextView) findViewById(R.id.tvWelcome))
                .setText(getString(R.string.welcome_format, username));

        findViewById(R.id.btnLogout).setOnClickListener(v -> deconnecter(false));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Session expirée (token JWT dépassé, 401) → retour à l'écran de connexion
        ApiClient.setOnSessionExpiree(() -> deconnecter(true));
        // Reste informé en direct : données réelles du serveur si disponible
        ApiClient.refreshPowerSystem(() -> AffichageDashboard.rafraichir(this));
        AffichageDashboard.rafraichir(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ApiClient.setOnSessionExpiree(null);
    }

    /**
     * Déconnexion : volontaire (logout, sessionExpiree = false) ou forcée
     * (token JWT expiré côté serveur, sessionExpiree = true → message affiché).
     */
    private void deconnecter(boolean sessionExpiree) {
        ApiClient.setToken(null);
        ApiClient.setOnSessionExpiree(null);
        getSharedPreferences("energy_command", MODE_PRIVATE).edit()
                .remove("token").remove("session_role")
                .remove("session_username").remove("session_email").apply();
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra(MainActivity.EXTRA_SESSION_EXPIREE, sessionExpiree);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}

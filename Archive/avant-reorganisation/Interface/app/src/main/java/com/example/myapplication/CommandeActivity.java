package com.example.myapplication;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

/**
 * Interface 3 : commande du groupe électrogène — écran de DÉMONSTRATION,
 * accessible depuis le tableau de bord ADMIN et RESPONSABLE.
 *
 * IMPORTANT — cohérence avec le serveur "serveurmen2" :
 * la bascule ONDULEUR → GROUPE_ELECTROGENE est AUTOMATIQUE côté serveur
 * (délai sans retour JIRAMA, l'ESP32 lit /api/esp32/{id}/config). Il n'existe
 * pas encore de route de commande manuelle (FORCAGE_ = itération future).
 * Donc :
 *   - MODE SERVEUR (données réelles) : le bouton est DÉSACTIVÉ, une note
 *     explique que la bascule est automatique ;
 *   - MODE DÉMO (serveur éteint) : le bouton fonctionne localement.
 */
public class CommandeActivity extends AppCompatActivity {

    private MaterialButton btnActionGroupe;
    private TextView tvNoteCommande;
    private String username = "utilisateur";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_commande);

        String extra = getIntent().getStringExtra(MainActivity.EXTRA_USERNAME);
        if (extra != null && !extra.isEmpty()) username = extra;
        ((TextView) findViewById(R.id.tvWelcome))
                .setText(getString(R.string.welcome_format, username));

        btnActionGroupe = findViewById(R.id.btnActionGroupe);
        tvNoteCommande = findViewById(R.id.tvNoteCommande);

        // Commande de DÉMONSTRATION : un clic = démarrer si arrêté, arrêter si en marche
        btnActionGroupe.setOnClickListener(v -> {
            if (PowerSystem.isModeServeur()) return; // sécurité : jamais en mode serveur
            boolean demarrer = !PowerSystem.isGroupeDemoEnMarche();
            PowerSystem.commanderGroupeDemo(demarrer, username);
            Toast.makeText(this,
                    demarrer ? R.string.msg_groupe_demarre : R.string.msg_groupe_arrete,
                    Toast.LENGTH_SHORT).show();
            rafraichir();
        });

        findViewById(R.id.btnLogout).setOnClickListener(v -> deconnecter(false));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Session expirée (token JWT dépassé, 401) → retour à l'écran de connexion
        ApiClient.setOnSessionExpiree(() -> deconnecter(true));
        // Données RÉELLES des capteurs si le serveur répond
        ApiClient.refreshPowerSystem(this::rafraichir);
        rafraichir();
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

    private void rafraichir() {
        boolean modeServeur = PowerSystem.isModeServeur();

        // ---- Source d'alimentation actuelle ----
        TextView tvSource = findViewById(R.id.tvSource);
        String source = PowerSystem.getSourceActuelle();
        if (source == null) {
            // Démo : l'état du groupe de démonstration tient lieu de source
            tvSource.setText(PowerSystem.isGroupeDemoEnMarche()
                    ? PowerSystem.sourceLibelle("GROUPE_ELECTROGENE")
                    : PowerSystem.sourceLibelle("LIGNE_FIXE"));
            tvSource.setTextColor(ContextCompat.getColor(this,
                    PowerSystem.isGroupeDemoEnMarche()
                            ? R.color.couleur_principale : R.color.couleur_succes));
        } else {
            tvSource.setText(PowerSystem.sourceLibelle(source));
            int couleur;
            switch (source) {
                case "LIGNE_FIXE":         couleur = R.color.couleur_succes; break;
                case "ONDULEUR":           couleur = R.color.couleur_accent; break;
                case "GROUPE_ELECTROGENE": couleur = R.color.couleur_principale; break;
                default:                   couleur = R.color.couleur_texte; break;
            }
            tvSource.setTextColor(ContextCompat.getColor(this, couleur));
        }

        TextView tvSourceDepuis = findViewById(R.id.tvSourceDepuis);
        if (source == null) {
            tvSourceDepuis.setVisibility(View.GONE);
        } else {
            tvSourceDepuis.setVisibility(View.VISIBLE);
            String depuis = PowerSystem.getSourceDepuis();
            tvSourceDepuis.setText(depuis == null
                    ? getString(R.string.source_reelle)
                    : getString(R.string.depuis_format, depuis));
        }

        ((TextView) findViewById(R.id.tvDernierEnvoi)).setText(PowerSystem.labelSource(this));

        // ---- Bouton de commande : actif en démo, désactivé en mode serveur ----
        boolean groupeEnMarche = modeServeur
                ? "GROUPE_ELECTROGENE".equals(source)
                : PowerSystem.isGroupeDemoEnMarche();
        btnActionGroupe.setText(groupeEnMarche ? R.string.btn_arreter : R.string.btn_demarrer);
        btnActionGroupe.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this,
                        groupeEnMarche ? R.color.couleur_erreur : R.color.couleur_succes)));
        btnActionGroupe.setEnabled(!modeServeur);
        btnActionGroupe.setAlpha(modeServeur ? 0.5f : 1f);
        tvNoteCommande.setVisibility(modeServeur ? View.VISIBLE : View.GONE);

        // ---- Historique (serveur si connecté, sinon démo) ----
        LinearLayout container = findViewById(R.id.llHistorique);
        container.removeAllViews();
        for (String ligne : PowerSystem.historiqueAffiche()) {
            TextView tv = (TextView) getLayoutInflater()
                    .inflate(R.layout.item_log, container, false);
            tv.setText(ligne);
            container.addView(tv);
        }
    }
}

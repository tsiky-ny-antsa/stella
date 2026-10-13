package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Interface 1 : tableau de bord des rôles ADMIN et RESPONSABLE du serveur.
 *
 * ADMIN       : accès complet — source d'alimentation, ligne JIRAMA, carburant,
 *               alertes, historiques, gestion des utilisateurs (créer tous les
 *               rôles, supprimer) et écran de commande de démonstration.
 * RESPONSABLE : le même tableau de bord, avec une gestion PARTIELLE des comptes
 *               (créer des USER et RESPONSABLE uniquement — la suppression et
 *               la création d'ADMIN sont refusées par le serveur, 403).
 * Le rôle arrive par extra depuis l'écran de connexion et est transmis à
 * UserManagementActivity pour adapter l'écran de gestion des comptes.
 */
public class AdminDashboardActivity extends AppCompatActivity {

    private String role = User.ROLE_ADMIN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        String extraRole = getIntent().getStringExtra(MainActivity.EXTRA_ROLE);
        if (User.estResponsable(extraRole)) role = User.ROLE_RESPONSABLE;

        String username = getIntent().getStringExtra(MainActivity.EXTRA_USERNAME);
        if (username == null || username.isEmpty()) username = "admin";
        ((TextView) findViewById(R.id.tvWelcome))
                .setText(getString(R.string.welcome_format, username));

        // Titre adapté au rôle (Energy Command — Admin / — Responsable)
        ((TextView) findViewById(R.id.tvTitle)).setText(User.estResponsable(role)
                ? R.string.titre_responsable
                : R.string.titre_admin);

        findViewById(R.id.btnLogout).setOnClickListener(v -> deconnecter(false));
        findViewById(R.id.btnGoCommande).setOnClickListener(v ->
                startActivity(new Intent(this, CommandeActivity.class)));
        // Gestion des comptes : ADMIN (tout) et RESPONSABLE (partiel, sans
        // suppression ni création d'ADMIN) — le serveur filtre de toute façon.
        findViewById(R.id.btnGestionUsers).setOnClickListener(v -> {
            Intent i = new Intent(this, UserManagementActivity.class);
            i.putExtra(MainActivity.EXTRA_ROLE, role);
            startActivity(i);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Session expirée (token JWT dépassé, 401) → retour à l'écran de connexion.
        // Enregistré ici : c'est TOUJOURS l'écran visible qui écoute.
        ApiClient.setOnSessionExpiree(() -> deconnecter(true));
        // Données RÉELLES du serveur (alimentation, historiques) puis affichage
        ApiClient.refreshPowerSystem(() -> AffichageDashboard.rafraichir(this));
        AffichageDashboard.rafraichir(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Évite les fuites : l'écouteur ne doit pas survivre à l'écran
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

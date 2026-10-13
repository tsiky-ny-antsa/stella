package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Gestion des utilisateurs — branchée sur le serveur réel, ADAPTÉE AU RÔLE :
 *
 *   ADMIN       : GET/POST/DELETE /api/users — crée USER, RESPONSABLE et ADMIN,
 *                 supprime des comptes (pas le sien).
 *   RESPONSABLE : GET/POST /api/users — crée uniquement USER et RESPONSABLE ;
 *                 la suppression est masquée et la création d'ADMIN est cachée
 *                 (anti-élévation : le serveur renverrait 403).
 *
 * Codes serveur renvoyés : 201 créé, 400 champ invalide, 403 rôle insuffisant
 * (anti-élévation / suppression par RESPONSABLE), 409 doublon.
 * Les mots de passe sont hachés côté serveur (BCrypt) ; jamais renvoyés.
 */
public class UserManagementActivity extends AppCompatActivity {

    /** Codes de rôle acceptés à la création, ADMIN comme RESPONSABLE. */
    private static final String[] CODES_ADMIN =
            {User.ROLE_USER, User.ROLE_RESPONSABLE, User.ROLE_ADMIN};
    /** Le RESPONSABLE ne peut pas créer d'ADMIN (anti-élévation serveur). */
    private static final String[] CODES_RESPONSABLE =
            {User.ROLE_USER, User.ROLE_RESPONSABLE};

    private TextInputLayout tilNewUsername, tilNewEmail, tilNewPassword;
    private TextInputEditText etNewUsername, etNewEmail, etNewPassword;
    private Spinner spinnerRole;
    private MaterialButton btnAddUser;

    /** Rôle du compte connecté (extra) : ADMIN (défaut) ou RESPONSABLE. */
    private String roleCourant = User.ROLE_ADMIN;
    /** Codes de rôle affichés par le spinner (dépend du rôle courant). */
    private String[] codesRoles = CODES_ADMIN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_management);

        String extraRole = getIntent().getStringExtra(MainActivity.EXTRA_ROLE);
        roleCourant = User.estResponsable(extraRole)
                ? User.ROLE_RESPONSABLE : User.ROLE_ADMIN;
        codesRoles = User.estResponsable(roleCourant) ? CODES_RESPONSABLE : CODES_ADMIN;

        tilNewUsername = findViewById(R.id.tilNewUsername);
        tilNewEmail = findViewById(R.id.tilNewEmail);
        tilNewPassword = findViewById(R.id.tilNewPassword);
        etNewUsername = findViewById(R.id.etNewUsername);
        etNewEmail = findViewById(R.id.etNewEmail);
        etNewPassword = findViewById(R.id.etNewPassword);
        spinnerRole = findViewById(R.id.spinnerRole);
        btnAddUser = findViewById(R.id.btnAddUser);

        // Rôles proposés selon le compte connecté (contrat serveur + anti-élévation)
        int tableauLibelles = User.estResponsable(roleCourant)
                ? R.array.roles_responsable : R.array.roles_admin;
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, tableauLibelles, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRole.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnAddUser.setOnClickListener(v -> addUser());

        loadUsers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Session expirée (token JWT dépassé, 401) → retour à l'écran de connexion
        ApiClient.setOnSessionExpiree(() -> deconnecter(true));
        loadUsers();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ApiClient.setOnSessionExpiree(null);
    }

    /**
     * Déconnexion : volontaire ou forcée (session expirée côté serveur).
     * Même logique que les autres écrans (retour à l'écran de connexion).
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

    private void addUser() {
        tilNewUsername.setError(null);
        tilNewEmail.setError(null);
        tilNewPassword.setError(null);

        String username = txt(etNewUsername);
        String email = txt(etNewEmail);
        String password = txt(etNewPassword);
        // Le Spinner affiche des libellés lisibles ; on envoie le CODE au serveur
        int pos = spinnerRole.getSelectedItemPosition();
        String role = (pos >= 0 && pos < codesRoles.length)
                ? codesRoles[pos] : User.ROLE_USER;

        boolean valid = true;
        // Contraintes du serveur : [A-Za-z0-9_-]{3,64}
        if (TextUtils.isEmpty(username) || !username.matches("[A-Za-z0-9_-]{3,64}")) {
            tilNewUsername.setError(getString(R.string.err_username_invalide)); valid = false;
        }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilNewEmail.setError(getString(R.string.err_email_invalide)); valid = false;
        }
        if (password.length() < 6) {
            tilNewPassword.setError(getString(R.string.err_mdp_court)); valid = false;
        }
        if (!valid) return;

        btnAddUser.setEnabled(false);
        ApiClient.creerUtilisateur(username, email, password, role,
                new ApiClient.ApiCallback() {
                    @Override
                    public void onSuccess(int code, String body) {
                        btnAddUser.setEnabled(true);
                        Toast.makeText(UserManagementActivity.this,
                                getString(R.string.msg_compte_cree_role, roleLabel(role)),
                                Toast.LENGTH_LONG).show();
                        etNewUsername.setText("");
                        etNewEmail.setText("");
                        etNewPassword.setText("");
                        loadUsers();
                    }

                    @Override
                    public void onError(int code, String message) {
                        btnAddUser.setEnabled(true);
                        if (code == 409) {
                            // Doublon : username ou email déjà pris
                            Toast.makeText(UserManagementActivity.this,
                                    getString(R.string.msg_doublon, message),
                                    Toast.LENGTH_LONG).show();
                        } else if (code == 400) {
                            Toast.makeText(UserManagementActivity.this,
                                    getString(R.string.msg_champ_invalide, message),
                                    Toast.LENGTH_LONG).show();
                        } else if (code == 403) {
                            // Anti-élévation (RESPONSABLE qui crée un ADMIN) ou
                            // rôle devenu insuffisant : message du serveur affiché
                            Toast.makeText(UserManagementActivity.this,
                                    getString(R.string.msg_creation_refusee, message),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(UserManagementActivity.this,
                                    R.string.err_serveur_injoignable, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void loadUsers() {
        ApiClient.listeUsers(new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(int code, String body) {
                try {
                    JSONArray users = new JSONArray(body);
                    LinearLayout container = findViewById(R.id.llUsers);
                    TextView vide = findViewById(R.id.tvUsersVide);
                    container.setVisibility(View.VISIBLE);
                    container.removeAllViews();
                    if (users.length() == 0) {
                        vide.setVisibility(View.VISIBLE);
                        vide.setText(R.string.aucun_compte);
                        return;
                    }
                    vide.setVisibility(View.GONE);
                    for (int i = 0; i < users.length(); i++) {
                        JSONObject u = users.getJSONObject(i);
                        View item = getLayoutInflater()
                                .inflate(R.layout.item_user, container, false);
                        ((TextView) item.findViewById(R.id.tvUserLigne)).setText(
                                u.optString("username") + " — "
                                        + roleLabel(u.optString("role")) + "\n"
                                        + u.optString("email"));
                        MaterialButton btnSuppr =
                                item.findViewById(R.id.btnSupprimerUser);
                        // Suppression réservée à l'ADMIN (serveur : 403 sinon) :
                        // le bouton est masqué pour un RESPONSABLE.
                        if (User.peutSupprimerComptes(roleCourant)) {
                            btnSuppr.setOnClickListener(v ->
                                    confirmerSuppression(u.optLong("id", -1),
                                            u.optString("username")));
                            btnSuppr.setVisibility(View.VISIBLE);
                        } else {
                            btnSuppr.setVisibility(View.GONE);
                        }
                        container.addView(item);
                    }
                } catch (Exception e) {
                    // Réponse inattendue : on l'affiche au lieu de rester figé
                    afficherErreurListe(getString(R.string.err_reponse_inattendue));
                }
            }

            @Override
            public void onError(int code, String message) {
                afficherErreurListe(code == 403
                        ? getString(R.string.err_admin_responsable_requis)
                        : getString(R.string.err_serveur_injoignable));
            }
        });
    }

    /** Affiche l'erreur dans la zone de liste (contenu vidé, message visible). */
    private void afficherErreurListe(String message) {
        findViewById(R.id.llUsers).setVisibility(View.GONE);
        TextView vide = findViewById(R.id.tvUsersVide);
        vide.setVisibility(View.VISIBLE);
        vide.setText(message);
    }

    /** Confirmation puis DELETE /api/users/{id} (ADMIN uniquement). */
    private void confirmerSuppression(long id, String username) {
        if (id < 0) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.titre_suppression)
                .setMessage(getString(R.string.msg_confirmer_suppression, username))
                .setPositiveButton(R.string.btn_supprimer, (d, w) -> supprimer(id))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void supprimer(long id) {
        ApiClient.supprimerUtilisateur(id, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(int code, String body) {
                Toast.makeText(UserManagementActivity.this,
                        R.string.msg_compte_supprime, Toast.LENGTH_SHORT).show();
                loadUsers();
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(UserManagementActivity.this,
                        getString(R.string.msg_suppression_refusee, message),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Libellé lisible d'un code de rôle serveur (USER / RESPONSABLE / ADMIN). */
    private String roleLabel(String role) {
        if (User.ROLE_ADMIN.equals(role)) return getString(R.string.role_admin_court);
        if (User.ROLE_RESPONSABLE.equals(role)) return getString(R.string.role_responsable_court);
        if (User.ROLE_USER.equals(role)) return getString(R.string.role_user_court);
        return role;
    }

    private String txt(TextInputEditText f) {
        return f.getText() == null ? "" : f.getText().toString().trim();
    }
}

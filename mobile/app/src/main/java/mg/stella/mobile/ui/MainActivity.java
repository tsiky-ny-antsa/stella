package mg.stella.mobile.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import mg.stella.mobile.R;
import mg.stella.mobile.auth.SessionManager;
import mg.stella.mobile.ui.commands.CommandsFragment;
import mg.stella.mobile.ui.dashboard.DashboardFragment;
import mg.stella.mobile.ui.events.EventsFragment;
import mg.stella.mobile.ui.login.LoginActivity;
import mg.stella.mobile.ui.settings.SettingsFragment;

/** Barre basse : Vue d'ensemble, Commandes, Événements, Paramètres. */
public class MainActivity extends AppCompatActivity {
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            goToLogin();
            return;
        }
        setContentView(R.layout.activity_main);
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            Fragment f;
            int id = item.getItemId();
            if (id == R.id.nav_commands) f = new CommandsFragment();
            else if (id == R.id.nav_events) f = new EventsFragment();
            else if (id == R.id.nav_settings) f = new SettingsFragment();
            else f = new DashboardFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, f).commit();
            return true;
        });
        if (savedInstanceState == null) nav.setSelectedItemId(R.id.nav_overview);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!session.isLoggedIn()) goToLogin(); // jeton expiré pendant que l'app était en arrière-plan
    }

    private void goToLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.putExtra(LoginActivity.EXTRA_EXPIRED, true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}

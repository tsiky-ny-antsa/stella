package mg.stella.mobile.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import mg.stella.mobile.R;
import mg.stella.mobile.api.ApiClient;
import mg.stella.mobile.api.ApiException;
import mg.stella.mobile.auth.SessionManager;
import mg.stella.mobile.ui.login.LoginActivity;
import mg.stella.mobile.util.Json;

/** Socle des écrans : session, client API, rafraîchissement périodique, erreurs communes. */
public abstract class BaseFragment extends Fragment {
    protected static final long REFRESH_MS = 10_000L;

    protected SessionManager session;
    protected ApiClient api;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!isAdded()) return;
            refresh();
            ticker.postDelayed(this, REFRESH_MS);
        }
    };

    public interface DeviceCallback {
        void ready(String deviceId);
        void failed(String message);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        session = new SessionManager(context);
        api = ApiClient.get(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        ticker.removeCallbacks(tick);
        ticker.post(tick);
    }

    @Override
    public void onPause() {
        super.onPause();
        ticker.removeCallbacks(tick);
    }

    protected abstract void refresh();

    protected int color(int res) {
        return ContextCompat.getColor(requireContext(), res);
    }

    protected int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    protected void toast(String msg) {
        if (isAdded()) Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
    }

    /**
     * Session expirée (401) : efface la session et retourne à la connexion.
     * @return true si l'erreur a été traitée ainsi (l'appelant doit alors s'arrêter).
     */
    protected boolean handleUnauthorized(ApiException e) {
        if (!e.isUnauthorized()) return false;
        session.clear();
        Intent i = new Intent(requireContext(), LoginActivity.class);
        i.putExtra(LoginActivity.EXTRA_EXPIRED, true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        requireActivity().finish();
        return true;
    }

    /** Résout l'équipement courant (premier de GET /devices) puis appelle ready. */
    protected void withDevice(final DeviceCallback cb) {
        String id = session.getDeviceId();
        if (id != null) {
            cb.ready(id);
            return;
        }
        api.get("/devices", new ApiClient.Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject r) {
                if (!isAdded()) return;
                JSONArray a = Json.items(r);
                JSONObject d = a.length() > 0 ? a.optJSONObject(0) : null;
                String did = d == null ? null : Json.str(d, "device_id");
                if (did == null && d != null) did = Json.str(d, "id");
                if (did == null) {
                    cb.failed(getString(R.string.err_no_device));
                    return;
                }
                session.setDeviceId(did);
                cb.ready(did);
            }

            @Override public void onError(ApiException e) {
                if (!isAdded() || handleUnauthorized(e)) return;
                cb.failed(ErrorMessages.of(requireContext(), e));
            }
        });
    }
}

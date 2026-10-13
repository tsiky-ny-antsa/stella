package mg.stella.mobile.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import mg.stella.mobile.R;
import mg.stella.mobile.api.ApiClient;
import mg.stella.mobile.api.ApiException;
import mg.stella.mobile.model.CommandInfo;
import mg.stella.mobile.model.DeviceStatus;
import mg.stella.mobile.model.EventItem;
import mg.stella.mobile.ui.BaseFragment;
import mg.stella.mobile.util.Dates;
import mg.stella.mobile.util.Json;
import mg.stella.mobile.util.Labels;

/**
 * Vue d'ensemble. Ordre : connexion/fraîcheur, alimentation, état global, temps restant (mesure valide
 * uniquement), alertes, commande en cours, derniers événements.
 */
public class DashboardFragment extends BaseFragment {
    private TextView banner, updated, connText, lastSeen, stateText, remainingText, faultText,
            activeCmdText, alertsText, eventsText;
    private TextView vJirama, vUps, vGe, vAts, vSafety;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        banner = v.findViewById(R.id.banner);
        updated = v.findViewById(R.id.updated);
        connText = v.findViewById(R.id.conn_text);
        lastSeen = v.findViewById(R.id.last_seen);
        stateText = v.findViewById(R.id.state_text);
        remainingText = v.findViewById(R.id.remaining_text);
        faultText = v.findViewById(R.id.fault_text);
        activeCmdText = v.findViewById(R.id.active_cmd_text);
        alertsText = v.findViewById(R.id.alerts_text);
        eventsText = v.findViewById(R.id.events_text);

        LinearLayout power = v.findViewById(R.id.power_container);
        vJirama = addRow(power, R.string.src_jirama);
        vUps = addRow(power, R.string.src_ups);
        vGe = addRow(power, R.string.src_ge);
        vAts = addRow(power, R.string.src_ats);
        vSafety = addRow(power, R.string.src_safety);

        v.findViewById(R.id.btn_refresh).setOnClickListener(x -> refresh());
        alertsText.setText(R.string.loading);
        stateText.setText(R.string.loading);
    }

    private TextView addRow(LinearLayout parent, int labelRes) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        row.setLayoutParams(lp);
        TextView label = new TextView(requireContext());
        label.setText(labelRes);
        label.setTextSize(12);
        label.setTextColor(color(R.color.text_secondary));
        TextView value = new TextView(requireContext());
        value.setTextSize(16);
        value.setTextColor(color(R.color.text_primary));
        row.addView(label);
        row.addView(value);
        parent.addView(row);
        return value;
    }

    @Override
    protected void refresh() {
        withDevice(new DeviceCallback() {
            @Override public void ready(String id) {
                loadStatus(id);
                loadAlerts();
                loadEvents(id);
                loadActiveCommand(id);
            }

            @Override public void failed(String message) {
                if (!isAdded()) return;
                showCachedOrOffline();
                updated.setText(message);
            }
        });
    }

    private void loadStatus(String id) {
        api.get("/devices/" + id + "/status", new ApiClient.Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject r) {
                if (!isAdded()) return;
                session.saveStatusCache(r.toString());
                render(DeviceStatus.fromJson(r), false);
            }

            @Override public void onError(ApiException e) {
                if (!isAdded() || handleUnauthorized(e)) return;
                showCachedOrOffline();
            }
        });
    }

    /** Hors ligne : on montre le dernier état connu, clairement étiqueté comme ancien. */
    private void showCachedOrOffline() {
        String cache = session.getStatusCache();
        if (cache != null) {
            try {
                render(DeviceStatus.fromJson(new JSONObject(cache)), true);
                return;
            } catch (Exception ignored) { /* cache illisible : on retombe sur l'état vide */ }
        }
        banner.setText(R.string.banner_offline);
        banner.setVisibility(View.VISIBLE);
        stateText.setText(R.string.unknown);
        stateText.setTextColor(color(R.color.status_warning));
    }

    private void render(DeviceStatus s, boolean fromCache) {
        long now = System.currentTimeMillis();
        boolean stale = fromCache || s.isStale(now);

        if (fromCache) {
            banner.setText(R.string.banner_offline);
            banner.setVisibility(View.VISIBLE);
        } else if (stale) {
            banner.setText(R.string.banner_stale);
            banner.setVisibility(View.VISIBLE);
        } else {
            banner.setVisibility(View.GONE);
        }
        updated.setText(getString(R.string.last_update, Dates.format(s.capturedAt)));

        if (fromCache || "OFFLINE".equals(s.connStatus)) {
            connText.setText(R.string.conn_offline);
            connText.setTextColor(color(R.color.status_critical));
        } else if (s.isOnline()) {
            connText.setText(R.string.conn_online);
            connText.setTextColor(color(R.color.status_ok));
        } else {
            connText.setText(R.string.conn_unknown);
            connText.setTextColor(color(R.color.status_warning));
        }
        lastSeen.setText(getString(R.string.last_seen, Dates.format(s.lastSeen)));

        stateText.setText(Labels.state(requireContext(), s.state));
        stateText.setTextColor(color(stale ? R.color.status_warning : R.color.text_primary));

        if (!fromCache && s.hasValidRemaining(now)) {
            int sec = s.remainingSeconds;
            remainingText.setText(getString(R.string.dash_remaining, sec / 60, sec % 60));
            remainingText.setVisibility(View.VISIBLE);
        } else {
            remainingText.setVisibility(View.GONE);
        }

        if (s.hasFault()) {
            String detail = s.faultMessage != null ? s.faultMessage : s.faultCode;
            faultText.setText(getString(R.string.safety_fault, detail));
            faultText.setTextColor(color(R.color.status_critical));
            faultText.setVisibility(View.VISIBLE);
        } else {
            faultText.setVisibility(View.GONE);
        }

        renderSources(s, stale);
    }

    private void renderSources(DeviceStatus s, boolean stale) {
        // JIRAMA
        if (s.jiramaPresent == null) setValue(vJirama, getString(R.string.unknown), R.color.status_warning, stale);
        else if (s.jiramaPresent) setValue(vJirama, getString(R.string.jirama_present), R.color.status_ok, stale);
        else setValue(vJirama, getString(R.string.jirama_absent), R.color.status_warning, stale);

        // UPS
        if (s.upsLowBattery == null) setValue(vUps, getString(R.string.unknown), R.color.status_warning, stale);
        else if (s.upsLowBattery) setValue(vUps, getString(R.string.ups_low), R.color.status_critical, stale);
        else setValue(vUps, getString(R.string.ups_ok), R.color.status_ok, stale);

        // GE : on n'affiche actif/stable que sur état confirmé par le contrôleur.
        if (s.geDetected == null && s.geStable == null && s.geRunning == null) {
            setValue(vGe, getString(R.string.unknown), R.color.status_warning, stale);
        } else if (Boolean.TRUE.equals(s.geRunning)) {
            setValue(vGe, getString(R.string.ge_running), R.color.status_ok, stale);
        } else if (Boolean.TRUE.equals(s.geStable)) {
            setValue(vGe, getString(R.string.ge_stable), R.color.status_ok, stale);
        } else if (Boolean.TRUE.equals(s.geDetected)) {
            setValue(vGe, getString(R.string.ge_detected), R.color.status_warning, stale);
        } else {
            setValue(vGe, getString(R.string.ge_absent), R.color.text_primary, stale);
        }

        // ATS
        if (s.atsPosition == null) setValue(vAts, getString(R.string.unknown), R.color.status_warning, stale);
        else if ("JIRAMA".equals(s.atsPosition)) setValue(vAts, getString(R.string.ats_jirama), R.color.text_primary, stale);
        else if ("GE".equals(s.atsPosition)) setValue(vAts, getString(R.string.ats_ge), R.color.text_primary, stale);
        else setValue(vAts, getString(R.string.ats_other, s.atsPosition), R.color.status_warning, stale);

        // Sécurités
        StringBuilder sb = new StringBuilder();
        int colorRes = R.color.status_ok;
        if (Boolean.TRUE.equals(s.emergencyStop)) {
            sb.append(getString(R.string.safety_emergency));
            colorRes = R.color.status_critical;
        }
        if (Boolean.TRUE.equals(s.geFaultInput)) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(getString(R.string.safety_fault, s.faultMessage != null ? s.faultMessage : "GE"));
            colorRes = R.color.status_critical;
        }
        if (Boolean.TRUE.equals(s.manualSelector)) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(getString(R.string.safety_manual));
            if (colorRes != R.color.status_critical) colorRes = R.color.status_warning;
        }
        if (sb.length() == 0) {
            if (s.emergencyStop == null || s.manualSelector == null) {
                sb.append(getString(R.string.unknown));
                colorRes = R.color.status_warning;
            } else {
                sb.append(getString(R.string.safety_ok));
            }
        }
        setValue(vSafety, sb.toString(), colorRes, stale);
    }

    /** Données périmées : la couleur d'état est atténuée pour ne pas suggérer une valeur fiable. */
    private void setValue(TextView tv, String text, int colorRes, boolean stale) {
        tv.setText(text);
        tv.setTextColor(color(stale ? R.color.text_secondary : colorRes));
    }

    private void loadAlerts() {
        api.get("/alerts?status=OPEN&limit=50", new ApiClient.Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject r) {
                if (!isAdded()) return;
                int n = Json.items(r).length();
                alertsText.setText(getString(R.string.dash_alerts, n));
                alertsText.setTextColor(color(n > 0 ? R.color.status_critical : R.color.status_ok));
            }

            @Override public void onError(ApiException e) {
                if (!isAdded() || handleUnauthorized(e)) return;
                alertsText.setText(R.string.none_value);
                alertsText.setTextColor(color(R.color.text_secondary));
            }
        });
    }

    private void loadEvents(String id) {
        api.get("/devices/" + id + "/events?limit=3", new ApiClient.Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject r) {
                if (!isAdded()) return;
                JSONArray a = Json.items(r);
                if (a.length() == 0) {
                    eventsText.setText(R.string.ev_empty);
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o == null) continue;
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append(EventItem.fromJson(o).line());
                }
                eventsText.setText(sb.toString());
            }

            @Override public void onError(ApiException e) {
                if (!isAdded() || handleUnauthorized(e)) return;
                eventsText.setText(R.string.none_value);
            }
        });
    }

    private void loadActiveCommand(String id) {
        api.get("/devices/" + id + "/commands?limit=5", new ApiClient.Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject r) {
                if (!isAdded()) return;
                JSONArray a = Json.items(r);
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o == null) continue;
                    CommandInfo c = CommandInfo.fromJson(o);
                    if (!c.isTerminal()) {
                        String mode = null;
                        JSONObject p = o.optJSONObject("parameters");
                        if (p != null) mode = Json.str(p, "mode");
                        activeCmdText.setText(getString(R.string.dash_active_command,
                                Labels.commandType(requireContext(), c.type, mode),
                                Labels.commandStatus(requireContext(), c.status)));
                        activeCmdText.setVisibility(View.VISIBLE);
                        return;
                    }
                }
                activeCmdText.setVisibility(View.GONE);
            }

            @Override public void onError(ApiException e) {
                if (!isAdded() || handleUnauthorized(e)) return;
                activeCmdText.setVisibility(View.GONE);
            }
        });
    }
}

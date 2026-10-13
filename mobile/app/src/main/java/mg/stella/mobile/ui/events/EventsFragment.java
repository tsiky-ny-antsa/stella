package mg.stella.mobile.ui.events;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import mg.stella.mobile.R;
import mg.stella.mobile.api.ApiClient;
import mg.stella.mobile.api.ApiException;
import mg.stella.mobile.model.EventItem;
import mg.stella.mobile.ui.BaseFragment;
import mg.stella.mobile.ui.ErrorMessages;
import mg.stella.mobile.util.Json;

/**
 * Alertes ouvertes (avec acquittement) et chronologie des événements, filtrable par sévérité.
 * L'acquittement d'une alerte ne réinitialise pas un défaut physique (commande RESET_FAULT distincte).
 */
public class EventsFragment extends BaseFragment {
    private static final String[] SEVERITIES = {"", "INFO", "WARNING", "CRITICAL"};

    private TextView banner, updated;
    private LinearLayout alertsContainer, eventsContainer;
    private Spinner filter;
    private final List<EventItem> allEvents = new ArrayList<>();
    private boolean eventsLoaded;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_events, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        banner = v.findViewById(R.id.banner);
        updated = v.findViewById(R.id.updated);
        alertsContainer = v.findViewById(R.id.alerts_container);
        eventsContainer = v.findViewById(R.id.events_container);
        filter = v.findViewById(R.id.severity_filter);

        String[] labels = {getString(R.string.ev_filter_all), "INFO", "WARNING", "CRITICAL"};
        filter.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels));
        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View view, int pos, long id) {
                if (eventsLoaded) renderEvents();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        addInfo(alertsContainer, getString(R.string.loading));
        addInfo(eventsContainer, getString(R.string.loading));
    }

    @Override
    protected void refresh() {
        loadAlerts();
        withDevice(new DeviceCallback() {
            @Override public void ready(String id) { loadEvents(id); }

            @Override public void failed(String message) {
                if (!isAdded()) return;
                showOffline(message);
            }
        });
    }

    private void showOffline(String message) {
        banner.setText(message);
        banner.setVisibility(View.VISIBLE);
    }

    private void loadAlerts() {
        api.get("/alerts?status=OPEN&limit=50", new ApiClient.Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject r) {
                if (!isAdded()) return;
                banner.setVisibility(View.GONE);
                alertsContainer.removeAllViews();
                JSONArray a = Json.items(r);
                if (a.length() == 0) {
                    addInfo(alertsContainer, getString(R.string.ev_alerts_empty));
                    return;
                }
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o != null) addAlertRow(EventItem.fromJson(o));
                }
            }

            @Override public void onError(ApiException e) {
                if (!isAdded() || handleUnauthorized(e)) return;
                showOffline(ErrorMessages.of(requireContext(), e));
            }
        });
    }

    private void addAlertRow(final EventItem alert) {
        TextView t = new TextView(requireContext());
        t.setText(alert.line());
        t.setTextSize(13);
        t.setTextColor(color("CRITICAL".equals(alert.severity) ? R.color.status_critical : R.color.status_warning));
        t.setPadding(0, dp(8), 0, dp(4));
        alertsContainer.addView(t);
        if (session.canAckAlerts() && alert.id != null) {
            MaterialButton b = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            b.setText(R.string.ev_ack);
            b.setMinHeight(dp(48));
            b.setOnClickListener(x -> {
                b.setEnabled(false);
                api.post("/alerts/" + alert.id + "/ack", new JSONObject(), new ApiClient.Callback<JSONObject>() {
                    @Override public void onSuccess(JSONObject r) {
                        if (!isAdded()) return;
                        toast(getString(R.string.ev_ack_done));
                        refresh();
                    }

                    @Override public void onError(ApiException e) {
                        if (!isAdded() || handleUnauthorized(e)) return;
                        b.setEnabled(true);
                        toast(ErrorMessages.of(requireContext(), e));
                    }
                });
            });
            alertsContainer.addView(b);
        }
    }

    private void loadEvents(String id) {
        api.get("/devices/" + id + "/events?limit=50", new ApiClient.Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject r) {
                if (!isAdded()) return;
                allEvents.clear();
                JSONArray a = Json.items(r);
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o != null) allEvents.add(EventItem.fromJson(o));
                }
                eventsLoaded = true;
                renderEvents();
            }

            @Override public void onError(ApiException e) {
                if (!isAdded() || handleUnauthorized(e)) return;
                showOffline(ErrorMessages.of(requireContext(), e));
            }
        });
    }

    private void renderEvents() {
        eventsContainer.removeAllViews();
        String wanted = SEVERITIES[Math.max(0, filter.getSelectedItemPosition())];
        int shown = 0;
        for (EventItem e : allEvents) {
            if (!wanted.isEmpty() && !wanted.equals(e.severity)) continue;
            TextView t = new TextView(requireContext());
            t.setText(e.line());
            t.setTextSize(13);
            t.setTextColor(color("CRITICAL".equals(e.severity) ? R.color.status_critical
                    : "WARNING".equals(e.severity) ? R.color.status_warning : R.color.text_primary));
            t.setPadding(0, dp(8), 0, dp(8));
            eventsContainer.addView(t);
            View divider = new View(requireContext());
            divider.setBackgroundColor(color(R.color.border));
            divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            eventsContainer.addView(divider);
            shown++;
        }
        if (shown == 0) addInfo(eventsContainer, getString(R.string.ev_empty));
    }

    private void addInfo(LinearLayout parent, String text) {
        parent.removeAllViews();
        TextView t = new TextView(requireContext());
        t.setText(text);
        t.setTextColor(color(R.color.text_secondary));
        t.setPadding(0, dp(8), 0, dp(8));
        parent.addView(t);
    }
}

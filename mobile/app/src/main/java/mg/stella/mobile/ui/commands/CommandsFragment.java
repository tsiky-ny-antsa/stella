package mg.stella.mobile.ui.commands;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import mg.stella.mobile.R;
import mg.stella.mobile.api.ApiClient;
import mg.stella.mobile.api.ApiException;
import mg.stella.mobile.model.CommandInfo;
import mg.stella.mobile.model.DeviceStatus;
import mg.stella.mobile.ui.BaseFragment;
import mg.stella.mobile.ui.ErrorMessages;
import mg.stella.mobile.util.Dates;
import mg.stella.mobile.util.Json;
import mg.stella.mobile.util.Labels;

/**
 * Commandes physiques. Règles (DESIGN_MOBILE / API_FASTAPI_MOBILE) :
 *  - confirmation avant envoi ;
 *  - request_id idempotent : un nouvel appui après un échec réseau réutilise le même identifiant ;
 *  - la progression vient du statut serveur ; « Terminée » n'est affichée que pour SUCCEEDED ;
 *  - boutons désactivés si données périmées/inconnues, rôle insuffisant, arrêt d'urgence ou mode manuel
 *    (confort d'interface seulement : le backend reste l'autorité).
 */
public class CommandsFragment extends BaseFragment {
    private static final long TRACK_INTERVAL_MS = 2000L;
    private static final long TRACK_TIMEOUT_MS = 120_000L;

    private static final class Spec {
        final String type, mode;
        final int labelRes, confirmRes;
        Spec(String type, String mode, int labelRes, int confirmRes) {
            this.type = type; this.mode = mode; this.labelRes = labelRes; this.confirmRes = confirmRes;
        }
        String key() { return mode == null ? type : type + ":" + mode; }
    }

    private final Spec[] specs = {
            new Spec("START_GE", null, R.string.cmd_start_ge, R.string.cmd_confirm_start),
            new Spec("STOP_GE", null, R.string.cmd_stop_ge, R.string.cmd_confirm_stop),
            new Spec("TRANSFER_TO_GE", null, R.string.cmd_transfer_ge, R.string.cmd_confirm_transfer_ge),
            new Spec("TRANSFER_TO_JIRAMA", null, R.string.cmd_transfer_jirama, R.string.cmd_confirm_transfer_jirama),
            new Spec("RESET_FAULT", null, R.string.cmd_reset_fault, R.string.cmd_confirm_reset_fault),
            new Spec("SET_MODE", "AUTO", R.string.cmd_mode_auto, R.string.cmd_confirm_mode_auto),
            new Spec("SET_MODE", "REMOTE", R.string.cmd_mode_remote, R.string.cmd_confirm_mode_remote),
    };

    private final List<MaterialButton> buttons = new ArrayList<>();
    private final Map<String, String> pendingIds = new HashMap<>();
    private final Handler trackHandler = new Handler(Looper.getMainLooper());

    private TextView banner, updated, disabledReason, progressStatus, progressDetail, historyText;
    private View progressCard;

    private String deviceId;
    private DeviceStatus status;
    private boolean statusFromCache;
    private boolean sending;
    private CommandInfo tracked;
    private String trackedMode;
    private long trackStart;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_commands, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        banner = v.findViewById(R.id.banner);
        updated = v.findViewById(R.id.updated);
        disabledReason = v.findViewById(R.id.disabled_reason);
        progressStatus = v.findViewById(R.id.progress_status);
        progressDetail = v.findViewById(R.id.progress_detail);
        historyText = v.findViewById(R.id.history_text);
        progressCard = (View) v.findViewById(R.id.card_progress).getParent();
        progressCard.setVisibility(View.GONE);

        LinearLayout container = v.findViewById(R.id.buttons_container);
        for (final Spec s : specs) {
            MaterialButton btn = new MaterialButton(requireContext());
            btn.setText(s.labelRes);
            btn.setMinHeight(dp(48));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(x -> confirmThenSend(s));
            container.addView(btn);
            buttons.add(btn);
        }
        updateButtons();
    }

    @Override
    public void onPause() {
        super.onPause();
        trackHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (tracked != null && !tracked.isTerminal()) startTracking();
    }

    @Override
    protected void refresh() {
        withDevice(new DeviceCallback() {
            @Override public void ready(String id) {
                deviceId = id;
                api.get("/devices/" + id + "/status", new ApiClient.Callback<JSONObject>() {
                    @Override public void onSuccess(JSONObject r) {
                        if (!isAdded()) return;
                        session.saveStatusCache(r.toString());
                        status = DeviceStatus.fromJson(r);
                        statusFromCache = false;
                        updated.setText(getString(R.string.last_update, Dates.format(status.capturedAt)));
                        updateButtons();
                    }

                    @Override public void onError(ApiException e) {
                        if (!isAdded() || handleUnauthorized(e)) return;
                        // Hors ligne : aucune commande ne doit être présentée comme envoyable.
                        statusFromCache = true;
                        updateButtons();
                    }
                });
                if (tracked == null || tracked.isTerminal()) loadHistory();
            }

            @Override public void failed(String message) {
                if (!isAdded()) return;
                statusFromCache = true;
                updated.setText(message);
                updateButtons();
            }
        });
    }

    /** Calcule la raison de désactivation (null = commandes disponibles). */
    private String disabledReasonText() {
        long now = System.currentTimeMillis();
        if (!session.canCommand()) return getString(R.string.cmd_disabled_role);
        if (status == null || statusFromCache || status.isStale(now)) return getString(R.string.cmd_disabled_stale);
        if (Boolean.TRUE.equals(status.emergencyStop)) return getString(R.string.cmd_disabled_emergency);
        if (Boolean.TRUE.equals(status.manualSelector)) return getString(R.string.cmd_disabled_manual);
        if (status.emergencyStop == null || status.manualSelector == null) return getString(R.string.cmd_disabled_unknown);
        if (sending || (tracked != null && !tracked.isTerminal())) return getString(R.string.cmd_disabled_busy);
        return null;
    }

    private void updateButtons() {
        if (!isAdded()) return;
        String reason = disabledReasonText();
        boolean enabled = reason == null;
        for (MaterialButton b : buttons) b.setEnabled(enabled);
        if (reason == null) {
            disabledReason.setVisibility(View.GONE);
        } else {
            disabledReason.setText(reason);
            disabledReason.setVisibility(View.VISIBLE);
        }
        if (statusFromCache) {
            banner.setText(R.string.banner_offline);
            banner.setVisibility(View.VISIBLE);
        } else if (status != null && status.isStale(System.currentTimeMillis())) {
            banner.setText(R.string.banner_stale);
            banner.setVisibility(View.VISIBLE);
        } else {
            banner.setVisibility(View.GONE);
        }
    }

    private void confirmThenSend(final Spec s) {
        if (deviceId == null || disabledReasonText() != null) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.cmd_confirm_title)
                .setMessage(getString(s.labelRes) + "\n\n" + getString(s.confirmRes))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.cmd_confirm_yes, (d, w) -> send(s))
                .show();
    }

    private void send(final Spec s) {
        String rid = pendingIds.get(s.key());
        if (rid == null) {
            rid = UUID.randomUUID().toString();
            pendingIds.put(s.key(), rid);
        }
        JSONObject body = new JSONObject();
        try {
            body.put("type", s.type);
            body.put("request_id", rid);
            body.put("reason", getString(R.string.cmd_reason_default));
            if (s.mode != null) body.put("parameters", new JSONObject().put("mode", s.mode));
        } catch (JSONException e) {
            return;
        }
        sending = true;
        trackedMode = s.mode;
        updateButtons();
        progressCard.setVisibility(View.VISIBLE);
        progressStatus.setText(R.string.cmd_sent_pending);
        progressStatus.setTextColor(color(R.color.text_secondary));
        progressDetail.setText("");

        api.post("/devices/" + deviceId + "/commands", body, new ApiClient.Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject r) {
                if (!isAdded()) return;
                sending = false;
                pendingIds.remove(s.key());
                tracked = CommandInfo.fromJson(r);
                if (tracked.type == null) tracked.type = s.type;
                showProgress();
                updateButtons();
                startTracking();
                loadHistory();
            }

            @Override public void onError(ApiException e) {
                if (!isAdded() || handleUnauthorized(e)) return;
                sending = false;
                // Erreur réseau : on garde le request_id pour qu'un nouvel essai ne crée pas de doublon.
                if (!e.isNetwork()) pendingIds.remove(s.key());
                tracked = null;
                progressStatus.setText(ErrorMessages.of(requireContext(), e));
                progressStatus.setTextColor(color(R.color.status_critical));
                progressDetail.setText(e.isNetwork() ? getString(R.string.cmd_error_retry) : "");
                updateButtons();
            }
        });
    }

    private void showProgress() {
        if (tracked == null) return;
        progressCard.setVisibility(View.VISIBLE);
        String text = Labels.commandType(requireContext(), tracked.type, trackedMode) + " — "
                + Labels.commandStatus(requireContext(), tracked.status);
        progressStatus.setText(text);
        int c;
        if ("SUCCEEDED".equals(tracked.status)) c = R.color.status_ok;
        else if (tracked.isTerminal()) c = R.color.status_critical;
        else c = R.color.status_warning;
        progressStatus.setTextColor(color(c));
        progressDetail.setText(tracked.message != null ? tracked.message : "");
    }

    private void startTracking() {
        trackHandler.removeCallbacksAndMessages(null);
        trackStart = System.currentTimeMillis();
        trackHandler.postDelayed(trackRunnable, TRACK_INTERVAL_MS);
    }

    private final Runnable trackRunnable = new Runnable() {
        @Override public void run() {
            if (!isAdded() || tracked == null || tracked.id == null || tracked.isTerminal()) return;
            if (System.currentTimeMillis() - trackStart > TRACK_TIMEOUT_MS) {
                progressDetail.setText(R.string.cmd_tracking_stopped);
                tracked = null;
                updateButtons();
                return;
            }
            api.get("/commands/" + tracked.id, new ApiClient.Callback<JSONObject>() {
                @Override public void onSuccess(JSONObject r) {
                    if (!isAdded() || tracked == null) return;
                    CommandInfo fresh = CommandInfo.fromJson(r);
                    if (fresh.type == null) fresh.type = tracked.type;
                    if (fresh.id == null) fresh.id = tracked.id;
                    tracked = fresh;
                    showProgress();
                    if (tracked.isTerminal()) {
                        updateButtons();
                        loadHistory();
                        refresh();
                    } else {
                        trackHandler.postDelayed(trackRunnable, TRACK_INTERVAL_MS);
                    }
                }

                @Override public void onError(ApiException e) {
                    if (!isAdded() || handleUnauthorized(e)) return;
                    // Perte réseau : on réessaie jusqu'au délai maximal de suivi.
                    trackHandler.postDelayed(trackRunnable, TRACK_INTERVAL_MS);
                }
            });
        }
    };

    private void loadHistory() {
        if (deviceId == null) return;
        api.get("/devices/" + deviceId + "/commands?limit=20", new ApiClient.Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject r) {
                if (!isAdded()) return;
                JSONArray a = Json.items(r);
                if (a.length() == 0) {
                    historyText.setText(R.string.cmd_history_empty);
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o == null) continue;
                    CommandInfo c = CommandInfo.fromJson(o);
                    String mode = null;
                    JSONObject p = o.optJSONObject("parameters");
                    if (p != null) mode = Json.str(p, "mode");
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(Dates.formatIso(c.createdAt)).append(" — ")
                            .append(Labels.commandType(requireContext(), c.type, mode)).append(" — ")
                            .append(Labels.commandStatus(requireContext(), c.status));
                }
                historyText.setText(sb.toString());
            }

            @Override public void onError(ApiException e) {
                if (!isAdded() || handleUnauthorized(e)) return;
                historyText.setText(R.string.none_value);
            }
        });
    }
}

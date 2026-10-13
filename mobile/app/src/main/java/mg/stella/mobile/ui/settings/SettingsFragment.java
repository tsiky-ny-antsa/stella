package mg.stella.mobile.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import mg.stella.mobile.BuildConfig;
import mg.stella.mobile.R;
import mg.stella.mobile.api.ApiClient;
import mg.stella.mobile.api.ApiException;
import mg.stella.mobile.ui.BaseFragment;
import mg.stella.mobile.ui.login.LoginActivity;
import mg.stella.mobile.util.Dates;
import mg.stella.mobile.util.Json;

/**
 * Compte, serveur (modifiable en développement seulement), infos équipement, section ADMIN (squelette P1)
 * et déconnexion.
 */
public class SettingsFragment extends BaseFragment {
    private TextView updated, deviceText, firmwareText, deviceSeenText;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        updated = v.findViewById(R.id.updated);
        deviceText = v.findViewById(R.id.device_text);
        firmwareText = v.findViewById(R.id.firmware_text);
        deviceSeenText = v.findViewById(R.id.device_seen_text);

        ((TextView) v.findViewById(R.id.account_text)).setText(session.getDisplayName());
        ((TextView) v.findViewById(R.id.role_text)).setText(getString(R.string.set_role, session.getRole()));
        ((TextView) v.findViewById(R.id.server_text)).setText(session.getBaseUrl());

        if (BuildConfig.ALLOW_SERVER_EDIT) {
            final TextInputEditText edit = v.findViewById(R.id.server_edit);
            v.findViewById(R.id.server_layout).setVisibility(View.VISIBLE);
            v.findViewById(R.id.server_save).setVisibility(View.VISIBLE);
            edit.setText(session.getBaseUrl());
            v.findViewById(R.id.server_save).setOnClickListener(x -> {
                if (edit.getText() != null) session.setBaseUrl(edit.getText().toString());
                ((TextView) v.findViewById(R.id.server_text)).setText(session.getBaseUrl());
                toast(getString(R.string.set_server_saved));
            });
        }

        // Administration réservée à ADMIN (affichage ; le backend contrôle les droits).
        View adminCard = (View) v.findViewById(R.id.card_admin).getParent();
        adminCard.setVisibility(session.isAdmin() ? View.VISIBLE : View.GONE);

        v.findViewById(R.id.btn_logout).setOnClickListener(x -> {
            session.clear();
            Intent i = new Intent(requireContext(), LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            requireActivity().finish();
        });
    }

    @Override
    protected void refresh() {
        withDevice(new DeviceCallback() {
            @Override public void ready(final String id) {
                api.get("/devices/" + id, new ApiClient.Callback<JSONObject>() {
                    @Override public void onSuccess(JSONObject r) {
                        if (!isAdded()) return;
                        String name = Json.str(r, "name");
                        deviceText.setText(name != null ? name + " (" + id + ")" : id);
                        String fw = Json.str(r, "firmware_version");
                        firmwareText.setText(getString(R.string.set_firmware, fw != null ? fw : getString(R.string.unknown)));
                        String seen = Json.str(r, "last_seen_at");
                        if (seen == null) {
                            JSONObject c = r.optJSONObject("connection");
                            seen = Json.str(c, "last_seen_at");
                        }
                        deviceSeenText.setText(getString(R.string.last_seen, Dates.formatIso(seen)));
                    }

                    @Override public void onError(ApiException e) {
                        if (!isAdded() || handleUnauthorized(e)) return;
                        deviceText.setText(id);
                        firmwareText.setText(R.string.none_value);
                        deviceSeenText.setText(R.string.none_value);
                    }
                });
            }

            @Override public void failed(String message) {
                if (!isAdded()) return;
                deviceText.setText(message);
            }
        });
    }
}

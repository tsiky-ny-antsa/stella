package mg.stella.mobile.model;

import org.json.JSONObject;

import java.util.Date;

import mg.stella.mobile.util.Dates;
import mg.stella.mobile.util.Json;

/**
 * État rapporté par le contrôleur (GET /devices/{id}/status).
 * Les champs inconnus restent null : jamais transformés en false.
 */
public class DeviceStatus {
    /** Au-delà de ce délai sans mesure, l'état est présenté comme périmé. */
    public static final long STALE_AFTER_MS = 120_000L;

    public String deviceId;
    public String connStatus;
    public Date lastSeen;
    public boolean serverStale;
    public String state;
    public String controlMode;
    public Boolean jiramaPresent, geFaultInput, emergencyStop, manualSelector, upsLowBattery;
    public String atsPosition;
    public Boolean geDetected, geStable, geRunning;
    public Integer remainingSeconds;
    public String faultCode, faultMessage;
    public Date capturedAt;

    public static DeviceStatus fromJson(JSONObject o) {
        DeviceStatus s = new DeviceStatus();
        s.deviceId = Json.str(o, "device_id");
        JSONObject c = o.optJSONObject("connection");
        if (c != null) {
            s.connStatus = Json.str(c, "status");
            s.lastSeen = Dates.parse(Json.str(c, "last_seen_at"));
            s.serverStale = Boolean.TRUE.equals(Json.bool(c, "stale"));
        }
        s.state = Json.str(o, "state");
        s.controlMode = Json.str(o, "control_mode");
        JSONObject in = o.optJSONObject("inputs");
        if (in != null) {
            s.jiramaPresent = Json.bool(in, "jirama_present");
            s.upsLowBattery = Json.bool(in, "ups_low_battery");
            s.atsPosition = Json.str(in, "ats_position");
            s.geFaultInput = Json.bool(in, "ge_fault");
            s.emergencyStop = Json.bool(in, "emergency_stop");
            s.manualSelector = Json.bool(in, "manual_selector");
        }
        JSONObject ge = o.optJSONObject("ge");
        if (ge != null) {
            s.geDetected = Json.bool(ge, "detected");
            s.geStable = Json.bool(ge, "stable");
            s.geRunning = Json.bool(ge, "running");
        }
        JSONObject w = o.optJSONObject("waiting");
        if (w != null) s.remainingSeconds = Json.integer(w, "remaining_seconds");
        JSONObject f = o.optJSONObject("fault");
        if (f != null) {
            s.faultCode = Json.str(f, "code");
            s.faultMessage = Json.str(f, "message");
        }
        s.capturedAt = Dates.parse(Json.str(o, "captured_at"));
        return s;
    }

    public boolean isOnline() { return "ONLINE".equals(connStatus); }

    /** Périmé si le serveur le dit, si l'appareil n'est pas en ligne ou si la mesure est trop ancienne/absente. */
    public boolean isStale(long nowMs) {
        if (serverStale || !isOnline() || capturedAt == null) return true;
        return nowMs - capturedAt.getTime() > STALE_AFTER_MS;
    }

    /** Le temps restant n'est affiché que s'il vient d'une mesure valide et récente. */
    public boolean hasValidRemaining(long nowMs) {
        return remainingSeconds != null && remainingSeconds >= 0 && !isStale(nowMs);
    }

    public boolean hasFault() {
        return faultCode != null && !"NONE".equals(faultCode);
    }
}

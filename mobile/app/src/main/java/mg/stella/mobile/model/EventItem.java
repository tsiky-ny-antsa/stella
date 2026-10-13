package mg.stella.mobile.model;

import org.json.JSONObject;

import mg.stella.mobile.util.Dates;
import mg.stella.mobile.util.Json;

/** Événement ou alerte. Les noms de champs exacts ne sont pas figés par le contrat : lecture tolérante. */
public class EventItem {
    public String id, severity, type, source, message, time, status;

    public static EventItem fromJson(JSONObject o) {
        EventItem e = new EventItem();
        e.id = Json.str(o, "id");
        if (e.id == null) e.id = Json.str(o, "alert_id");
        e.severity = Json.str(o, "severity");
        e.type = Json.str(o, "type");
        e.source = Json.str(o, "source");
        e.message = Json.str(o, "message");
        e.status = Json.str(o, "status");
        String t = Json.str(o, "occurred_at");
        if (t == null) t = Json.str(o, "created_at");
        if (t == null) t = Json.str(o, "timestamp");
        e.time = t;
        return e;
    }

    public String line() {
        StringBuilder sb = new StringBuilder();
        sb.append(Dates.formatIso(time));
        if (severity != null) sb.append("  [").append(severity).append(']');
        if (source != null) sb.append(' ').append(source);
        sb.append('\n');
        if (message != null) sb.append(message);
        else if (type != null) sb.append(type);
        return sb.toString();
    }
}

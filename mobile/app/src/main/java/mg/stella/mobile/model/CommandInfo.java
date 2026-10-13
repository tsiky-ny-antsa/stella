package mg.stella.mobile.model;

import org.json.JSONObject;

import mg.stella.mobile.util.Json;

/** Commande suivie (réponse 202 puis GET /commands/{id}). */
public class CommandInfo {
    public String id, type, status, message, createdAt;

    public static CommandInfo fromJson(JSONObject o) {
        CommandInfo c = new CommandInfo();
        c.id = Json.str(o, "command_id");
        if (c.id == null) c.id = Json.str(o, "id");
        c.type = Json.str(o, "type");
        c.status = Json.str(o, "status");
        c.createdAt = Json.str(o, "created_at");
        String m = Json.str(o, "reject_reason");
        if (m == null) m = Json.str(o, "result_message");
        if (m == null) m = Json.str(o, "message");
        c.message = m;
        return c;
    }

    public boolean isTerminal() {
        return "SUCCEEDED".equals(status) || "REJECTED".equals(status) || "FAILED".equals(status)
                || "EXPIRED".equals(status) || "CANCELLED".equals(status);
    }
}

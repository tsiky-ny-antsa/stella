package mg.stella.mobile.util;

import org.json.JSONArray;
import org.json.JSONObject;

/** Accès JSON tolérant : une valeur absente ou null reste null (jamais convertie en false/0). */
public final class Json {
    private Json() {}

    public static String str(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return null;
        return o.optString(key, null);
    }

    public static Boolean bool(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return null;
        return o.optBoolean(key);
    }

    public static Integer integer(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return null;
        return o.optInt(key);
    }

    /** Liste d'éléments : accepte {"items":[...]} (tableau racine enveloppé par le client) ou {"data":[...]}. */
    public static JSONArray items(JSONObject o) {
        if (o == null) return new JSONArray();
        JSONArray a = o.optJSONArray("items");
        if (a == null) a = o.optJSONArray("data");
        return a != null ? a : new JSONArray();
    }
}

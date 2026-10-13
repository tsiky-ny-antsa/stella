package mg.stella.mobile.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mg.stella.mobile.auth.SessionManager;

/**
 * Client HTTP unique vers FastAPI (Doc/API_FASTAPI_MOBILE.md).
 * Les réponses sont toujours livrées sur le thread principal.
 * Un tableau JSON à la racine est enveloppé en {"items":[...]}.
 */
public class ApiClient {
    public interface Callback<T> {
        void onSuccess(T result);
        void onError(ApiException error);
    }

    private static ApiClient instance;

    public static synchronized ApiClient get(Context ctx) {
        if (instance == null) instance = new ApiClient(ctx.getApplicationContext());
        return instance;
    }

    private final SessionManager session;
    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());

    private ApiClient(Context appCtx) {
        this.session = new SessionManager(appCtx);
    }

    public void get(String path, Callback<JSONObject> cb) { request("GET", path, null, true, cb); }
    public void post(String path, JSONObject body, Callback<JSONObject> cb) { request("POST", path, body, true, cb); }
    public void postPublic(String path, JSONObject body, Callback<JSONObject> cb) { request("POST", path, body, false, cb); }

    public void request(final String method, final String path, final JSONObject body,
                        final boolean auth, final Callback<JSONObject> cb) {
        io.execute(() -> {
            try {
                final JSONObject result = execute(method, path, body, auth);
                main.post(() -> cb.onSuccess(result));
            } catch (final ApiException e) {
                main.post(() -> cb.onError(e));
            }
        });
    }

    private JSONObject execute(String method, String path, JSONObject body, boolean auth) throws ApiException {
        HttpURLConnection c = null;
        try {
            URL url = new URL(session.getBaseUrl() + path);
            c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("Accept", "application/json");
            String token = session.getToken();
            if (auth && token != null) c.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = read(is);
            if (code >= 200 && code < 300) return parse(text);
            throw ApiException.fromHttp(code, text);
        } catch (ApiException e) {
            throw e;
        } catch (JSONException e) {
            throw new ApiException(0, ApiException.BAD_RESPONSE, e.getMessage());
        } catch (IOException e) {
            throw ApiException.network(e);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static JSONObject parse(String text) throws JSONException {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return new JSONObject();
        if (t.startsWith("[")) {
            JSONObject wrap = new JSONObject();
            wrap.put("items", new JSONArray(t));
            return wrap;
        }
        return new JSONObject(t);
    }

    private static String read(InputStream is) throws IOException {
        if (is == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
        is.close();
        return out.toString("UTF-8");
    }
}

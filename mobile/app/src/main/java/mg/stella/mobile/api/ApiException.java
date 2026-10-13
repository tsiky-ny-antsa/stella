package mg.stella.mobile.api;

import org.json.JSONObject;

/** Erreur API normalisée (format d'erreur du contrat : {"error":{code,message,details,request_id}}). */
public class ApiException extends Exception {
    public static final String NETWORK = "NETWORK_ERROR";
    public static final String BAD_RESPONSE = "BAD_RESPONSE";

    private final int httpStatus;
    private final String code;

    public ApiException(int httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public int getHttpStatus() { return httpStatus; }
    public String getCode() { return code; }
    public boolean isNetwork() { return NETWORK.equals(code); }
    public boolean isUnauthorized() { return httpStatus == 401; }
    public boolean isForbidden() { return httpStatus == 403; }

    public static ApiException network(Exception cause) {
        return new ApiException(0, NETWORK, cause.getMessage());
    }

    public static ApiException fromHttp(int status, String body) {
        String code = "HTTP_" + status;
        String message = null;
        try {
            JSONObject root = new JSONObject(body);
            JSONObject err = root.optJSONObject("error");
            if (err != null) {
                if (!err.isNull("code")) code = err.optString("code", code);
                if (!err.isNull("message")) message = err.optString("message", null);
            }
        } catch (Exception ignored) {
            // corps non JSON : on garde le code HTTP
        }
        return new ApiException(status, code, message);
    }
}

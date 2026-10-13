package mg.stella.mobile.ui;

import android.content.Context;

import mg.stella.mobile.R;
import mg.stella.mobile.api.ApiException;

/** Messages d'erreur compréhensibles pour l'opérateur. */
public final class ErrorMessages {
    private ErrorMessages() {}

    public static String of(Context c, ApiException e) {
        if (e.isNetwork()) return c.getString(R.string.err_network);
        if (e.isUnauthorized()) {
            return "INVALID_CREDENTIALS".equals(e.getCode())
                    ? c.getString(R.string.err_invalid_credentials)
                    : c.getString(R.string.err_session_expired);
        }
        if (e.isForbidden()) return c.getString(R.string.err_forbidden);
        if (ApiException.BAD_RESPONSE.equals(e.getCode())) return c.getString(R.string.err_bad_response);
        if (e.getHttpStatus() == 422) return c.getString(R.string.err_validation);
        String m = e.getMessage();
        return c.getString(R.string.err_generic, m != null ? m : e.getCode());
    }
}

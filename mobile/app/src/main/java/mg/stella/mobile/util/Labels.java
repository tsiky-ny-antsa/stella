package mg.stella.mobile.util;

import android.content.Context;

import mg.stella.mobile.R;

/** Codes serveur -> textes français centralisés dans strings.xml. Code inconnu : affiché tel quel. */
public final class Labels {
    private Labels() {}

    public static String state(Context c, String code) {
        if (code == null) return c.getString(R.string.unknown);
        switch (code) {
            case "NORMAL": return c.getString(R.string.st_normal);
            case "JIRAMA_OUTAGE": case "OUTAGE": return c.getString(R.string.st_jirama_outage);
            case "WAITING": return c.getString(R.string.st_waiting);
            case "GE_STARTING": case "STARTING_GE": return c.getString(R.string.st_ge_starting);
            case "GE_STABLE": return c.getString(R.string.st_ge_stable);
            case "TRANSFERRING": return c.getString(R.string.st_transferring);
            case "GE_POWER": case "ON_GE": return c.getString(R.string.st_ge_power);
            case "RETURNING": case "RETURN_TO_JIRAMA": return c.getString(R.string.st_returning);
            case "FAULT": return c.getString(R.string.st_fault);
            case "MANUAL": case "MANUAL_LOCKOUT": return c.getString(R.string.st_manual);
            default: return code;
        }
    }

    public static String commandStatus(Context c, String code) {
        if (code == null) return c.getString(R.string.unknown);
        switch (code) {
            case "QUEUED": return c.getString(R.string.cs_queued);
            case "DELIVERED": return c.getString(R.string.cs_delivered);
            case "ACCEPTED": return c.getString(R.string.cs_accepted);
            case "RUNNING": return c.getString(R.string.cs_running);
            case "SUCCEEDED": return c.getString(R.string.cs_succeeded);
            case "REJECTED": return c.getString(R.string.cs_rejected);
            case "FAILED": return c.getString(R.string.cs_failed);
            case "EXPIRED": return c.getString(R.string.cs_expired);
            case "CANCELLED": return c.getString(R.string.cs_cancelled);
            default: return code;
        }
    }

    public static String commandType(Context c, String type, String mode) {
        if (type == null) return c.getString(R.string.unknown);
        switch (type) {
            case "START_GE": return c.getString(R.string.cmd_start_ge);
            case "STOP_GE": return c.getString(R.string.cmd_stop_ge);
            case "TRANSFER_TO_GE": return c.getString(R.string.cmd_transfer_ge);
            case "TRANSFER_TO_JIRAMA": return c.getString(R.string.cmd_transfer_jirama);
            case "RESET_FAULT": return c.getString(R.string.cmd_reset_fault);
            case "SET_MODE":
                return "REMOTE".equals(mode) ? c.getString(R.string.cmd_mode_remote)
                        : "AUTO".equals(mode) ? c.getString(R.string.cmd_mode_auto) : type;
            default: return type;
        }
    }
}

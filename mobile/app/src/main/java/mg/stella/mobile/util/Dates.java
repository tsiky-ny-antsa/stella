package mg.stella.mobile.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Dates ISO 8601 UTC du contrat API <-> affichage local. */
public final class Dates {
    private Dates() {}

    /** Retourne null si la valeur est absente ou illisible (on n'invente jamais une date). */
    public static Date parse(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        String s = iso.trim();
        s = s.replaceAll("\\.\\d+", "");          // retire les fractions de seconde
        s = s.replace("+00:00", "Z");
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            f.setLenient(false);
            return f.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    public static String format(Date d) {
        if (d == null) return "—";
        SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE);
        f.setTimeZone(TimeZone.getDefault());
        return f.format(d);
    }

    public static String formatIso(String iso) {
        return format(parse(iso));
    }
}

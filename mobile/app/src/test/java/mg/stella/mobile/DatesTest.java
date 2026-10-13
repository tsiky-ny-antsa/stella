package mg.stella.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import mg.stella.mobile.util.Dates;

public class DatesTest {
    @Test public void parsesUtcIso() {
        assertNotNull(Dates.parse("2026-10-12T16:20:00Z"));
        assertEquals(1791822000000L, Dates.parse("2026-10-12T16:20:00Z").getTime());
    }

    @Test public void ignoresFractionsAndOffsetZero() {
        assertEquals(Dates.parse("2026-10-12T16:20:00Z"), Dates.parse("2026-10-12T16:20:00.123456+00:00"));
    }

    @Test public void invalidOrMissingGivesNull() {
        assertNull(Dates.parse(null));
        assertNull(Dates.parse(""));
        assertNull(Dates.parse("hier"));
    }
}

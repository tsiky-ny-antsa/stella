package mg.stella.mobile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import mg.stella.mobile.model.DeviceStatus;
import mg.stella.mobile.util.Dates;

public class DeviceStatusTest {
    private static final String SAMPLE = "{\"device_id\":\"ge-ctrl-01\","
            + "\"connection\":{\"status\":\"ONLINE\",\"last_seen_at\":\"2026-10-12T16:20:00Z\",\"stale\":false},"
            + "\"state\":\"WAITING\",\"control_mode\":\"AUTO\","
            + "\"inputs\":{\"jirama_present\":false,\"ge_present\":false,\"ups_low_battery\":null,"
            + "\"ats_position\":\"JIRAMA\",\"ge_fault\":false,\"emergency_stop\":false,\"manual_selector\":false},"
            + "\"ge\":{\"detected\":false,\"stable\":false,\"running\":false},"
            + "\"waiting\":{\"remaining_seconds\":820},"
            + "\"fault\":{\"code\":\"NONE\",\"message\":null},"
            + "\"captured_at\":\"2026-10-12T16:20:00Z\"}";

    @Test public void unknownSensorStaysNullNotFalse() throws Exception {
        DeviceStatus s = DeviceStatus.fromJson(new JSONObject(SAMPLE));
        assertNull(s.upsLowBattery);
        assertFalse(s.jiramaPresent);
    }

    @Test public void freshStatusIsNotStale() throws Exception {
        DeviceStatus s = DeviceStatus.fromJson(new JSONObject(SAMPLE));
        long t = Dates.parse("2026-10-12T16:20:30Z").getTime();
        assertFalse(s.isStale(t));
        assertTrue(s.hasValidRemaining(t));
    }

    @Test public void oldStatusIsStaleAndHidesRemainingTime() throws Exception {
        DeviceStatus s = DeviceStatus.fromJson(new JSONObject(SAMPLE));
        long t = Dates.parse("2026-10-12T16:30:00Z").getTime();
        assertTrue(s.isStale(t));
        assertFalse(s.hasValidRemaining(t));
    }

    @Test public void noFaultWhenCodeNone() throws Exception {
        assertFalse(DeviceStatus.fromJson(new JSONObject(SAMPLE)).hasFault());
    }
}

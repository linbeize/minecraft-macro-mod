package de.linbei.macro;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChEventsTest {
    // Trimmed real response from https://api.soopy.dev/skyblock/chevents/get
    private static final String SAMPLE = """
            {"success":true,"data":{"event_datas":{
              "CRYSTAL_HOLLOWS":{"BETTER_TOGETHER":{"starts_at_min":1795143638211.0,"starts_at_max":1795144135854.0,
                  "ends_at_min":1795144718211.0,"ends_at_max":1795145215854.0,"lobby_count":26}},
              "DWARVEN_MINES":{"MITHRIL_GOURMAND":{"starts_at_min":1795144643908.0,"starts_at_max":1795144660408.0,
                  "ends_at_min":1795145243908.0,"ends_at_max":1795145760408.0,"lobby_count":31},
                "DOUBLE_POWDER":{"starts_at_min":1795143271090.0,"starts_at_max":1795143314186.0,
                  "ends_at_min":1795144171090.0,"ends_at_max":1795144214186.0,"lobby_count":7}},
              "MINESHAFT":{}},
              "running_events":{},"total_lobbys":{},"update_in":60000.0,"curr_time":1795144176119.0}}
            """;

    @Test
    void parsesEventsSortedByAreaThenStart() {
        long interval = ChEvents.parse(SAMPLE);
        assertEquals(60_000, interval);

        ChEvents.Snapshot snap = ChEvents.snapshot();
        List<ChEvents.Event> ev = snap.events();
        assertEquals(3, ev.size());
        assertEquals(new ChEvents.Event("CRYSTAL_HOLLOWS", "BETTER_TOGETHER", 1795143638211L, 1795145215854L, 26), ev.get(0));
        assertEquals("DOUBLE_POWDER", ev.get(1).id());
        assertEquals("MITHRIL_GOURMAND", ev.get(2).id());

        // Clock offset maps local time onto the API's clock.
        assertTrue(Math.abs(snap.now() - 1795144176119L) < 5_000);
    }

    @Test
    void rejectsUnsuccessfulResponse() {
        assertThrows(IllegalStateException.class, () -> ChEvents.parse("{\"success\":false}"));
    }

    @Test
    void formatting() {
        assertEquals("Double Powder", ChEvents.pretty("DOUBLE_POWDER"));
        assertEquals("Crystal Hollows", ChEvents.pretty("CRYSTAL_HOLLOWS"));
        assertEquals("42s", ChEvents.duration(42_900));
        assertEquals("12m 05s", ChEvents.duration(725_000));
        assertEquals("1h 02m", ChEvents.duration(3_720_000));
        assertEquals("0s", ChEvents.duration(-5));
    }
}

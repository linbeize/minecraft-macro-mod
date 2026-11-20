package de.linbei.macro;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hypixel SkyBlock mining events (Dwarven Mines / Crystal Hollows / Mineshaft), from
 * https://api.soopy.dev/skyblock/chevents/get. Polled in the background only while the overlay
 * is enabled and the player is in a world; the API says how long to wait (update_in).
 */
public final class ChEvents {
    private static final URI URL = URI.create("https://api.soopy.dev/skyblock/chevents/get");
    private static final long DEFAULT_INTERVAL_MS = 60_000;
    private static final long RETRY_MS = 30_000;
    private static final long MIN_INTERVAL_MS = 15_000;

    /** One event in one area. Times are API (server) epoch millis. */
    public record Event(String area, String id, long startsAt, long endsAt, int lobbies) {}

    /** Immutable snapshot; {@code clockOffset} = server time - local time at fetch. */
    public record Snapshot(List<Event> events, long clockOffset) {
        public long now() {
            return System.currentTimeMillis() + clockOffset;
        }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static volatile Snapshot snapshot;
    private static volatile boolean failed;
    private static volatile boolean inFlight;
    private static volatile long nextFetchAt;

    private ChEvents() {}

    public static Snapshot snapshot() {
        return snapshot;
    }

    public static boolean failed() {
        return failed;
    }

    /** Called every client tick; starts a request when one is due. */
    public static void tick(boolean active) {
        if (!active || inFlight || System.currentTimeMillis() < nextFetchAt) return;
        inFlight = true;
        HttpRequest req = HttpRequest.newBuilder(URL)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "client-utilities-mod (+https://github.com/linbeize/minecraft-macro-mod)")
                .GET()
                .build();
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((resp, err) -> {
            long interval = RETRY_MS;
            try {
                if (err != null) throw err;
                if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode());
                interval = parse(resp.body());
                failed = false;
            } catch (Throwable t) {
                failed = true;
                MacroModClient.LOGGER.debug("CH events fetch failed: {}", t.toString());
            } finally {
                nextFetchAt = System.currentTimeMillis() + interval;
                inFlight = false;
            }
        });
    }

    /** Fetch on the next tick (e.g. right after enabling). */
    public static void refreshSoon() {
        if (snapshot == null) nextFetchAt = 0;
    }

    /** Parses an API response into a new snapshot; returns the delay until the next poll. */
    static long parse(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("success") || !root.get("success").getAsBoolean()) throw new IllegalStateException("success=false");
        JsonObject data = root.getAsJsonObject("data");

        List<Event> events = new ArrayList<>();
        JsonObject areas = data.getAsJsonObject("event_datas");
        for (Map.Entry<String, JsonElement> area : areas.entrySet()) {
            if (!area.getValue().isJsonObject()) continue;
            for (Map.Entry<String, JsonElement> ev : area.getValue().getAsJsonObject().entrySet()) {
                if (!ev.getValue().isJsonObject()) continue;
                JsonObject o = ev.getValue().getAsJsonObject();
                if (!o.has("starts_at_min") || !o.has("ends_at_max")) continue;
                events.add(new Event(area.getKey(), ev.getKey(),
                        num(o, "starts_at_min"), num(o, "ends_at_max"),
                        o.has("lobby_count") ? o.get("lobby_count").getAsInt() : 0));
            }
        }
        events.sort(Comparator.comparing(Event::area).thenComparingLong(Event::startsAt));

        long serverNow = data.has("curr_time") ? num(data, "curr_time") : System.currentTimeMillis();
        snapshot = new Snapshot(List.copyOf(events), serverNow - System.currentTimeMillis());

        long interval = data.has("update_in") ? num(data, "update_in") : DEFAULT_INTERVAL_MS;
        return Math.max(MIN_INTERVAL_MS, interval);
    }

    private static long num(JsonObject o, String key) {
        return (long) o.get(key).getAsDouble();
    }

    /** "DOUBLE_POWDER" -> "Double Powder". */
    public static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    /** "1h 02m", "12m 05s", "42s". */
    public static String duration(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 3600) return String.format(Locale.ROOT, "%dh %02dm", s / 3600, s % 3600 / 60);
        if (s >= 60) return String.format(Locale.ROOT, "%dm %02ds", s / 60, s % 60);
        return s + "s";
    }
}

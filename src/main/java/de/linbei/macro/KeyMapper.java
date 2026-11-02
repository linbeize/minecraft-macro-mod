package de.linbei.macro;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Maps script key names to the game's key mappings. Names are resolved against the mapping
 * (not the physical key), so scripts keep working when the player rebinds controls.
 */
public final class KeyMapper {
    private static final Map<String, Function<Options, KeyMapping>> KEYS = new HashMap<>();
    /** Canonical names shown to the user (aliases are accepted but not listed). */
    private static final Set<String> CANONICAL = new TreeSet<>();

    static {
        reg(o -> o.keyUp, "W", "FORWARD");
        reg(o -> o.keyLeft, "A", "LEFT_STRAFE");
        reg(o -> o.keyDown, "S", "BACK");
        reg(o -> o.keyRight, "D", "RIGHT_STRAFE");
        reg(o -> o.keyJump, "SPACE", "JUMP");
        reg(o -> o.keyShift, "SHIFT", "LSHIFT", "SNEAK");
        reg(o -> o.keySprint, "CTRL", "LCTRL", "SPRINT");
        reg(o -> o.keyDrop, "Q", "DROP");
        reg(o -> o.keyInventory, "E", "INVENTORY");
        reg(o -> o.keySwapOffhand, "F", "SWAP");
        reg(o -> o.keyAttack, "LMB", "MOUSELEFT", "ATTACK");
        reg(o -> o.keyUse, "RMB", "MOUSERIGHT", "USE");
        reg(o -> o.keyPickItem, "MMB", "MOUSEMIDDLE", "PICK");
        for (int i = 0; i < 9; i++) {
            final int slot = i;
            reg(o -> o.keyHotbarSlots[slot], String.valueOf(i + 1), "NUM " + (i + 1), "NUM" + (i + 1));
        }
    }

    private KeyMapper() {}

    private static void reg(Function<Options, KeyMapping> getter, String canonical, String... aliases) {
        KEYS.put(canonical, getter);
        CANONICAL.add(canonical);
        for (String a : aliases) KEYS.put(a, getter);
    }

    public static String normalize(String raw) {
        return raw.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    public static boolean isKnown(String raw) {
        return KEYS.containsKey(normalize(raw));
    }

    /** @return the mapping, or {@code null} if the name is unknown */
    public static KeyMapping resolve(Options options, String raw) {
        Function<Options, KeyMapping> f = KEYS.get(normalize(raw));
        return f == null ? null : f.apply(options);
    }

    public static Set<String> knownNames() {
        return CANONICAL;
    }
}

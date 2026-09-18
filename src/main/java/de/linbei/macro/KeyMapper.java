package de.linbei.macro;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.Locale;

public final class KeyMapper {
    private KeyMapper() {}

    public static KeyMapping resolve(String raw) {
        Minecraft c = Minecraft.getInstance();
        if (c == null) return null;
        String k = raw.trim().toUpperCase(Locale.ROOT);

        return switch (k) {
            case "W" -> c.options.keyUp;
            case "A" -> c.options.keyLeft;
            case "S" -> c.options.keyDown;
            case "D" -> c.options.keyRight;
            case "SPACE" -> c.options.keyJump;
            case "SHIFT", "LSHIFT" -> c.options.keyShift;
            case "CTRL", "LCTRL" -> c.options.keySprint;
            case "NUM 1", "1" -> c.options.keyHotbarSlots[0];
            case "NUM 2", "2" -> c.options.keyHotbarSlots[1];
            case "NUM 3", "3" -> c.options.keyHotbarSlots[2];
            case "NUM 4", "4" -> c.options.keyHotbarSlots[3];
            case "NUM 5", "5" -> c.options.keyHotbarSlots[4];
            case "NUM 6", "6" -> c.options.keyHotbarSlots[5];
            case "NUM 7", "7" -> c.options.keyHotbarSlots[6];
            case "NUM 8", "8" -> c.options.keyHotbarSlots[7];
            case "NUM 9", "9" -> c.options.keyHotbarSlots[8];
            default -> null;
        };
    }
}

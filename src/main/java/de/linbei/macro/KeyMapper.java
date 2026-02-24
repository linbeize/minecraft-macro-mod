package de.linbei.macro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import java.util.Locale;

public final class KeyMapper {
    private KeyMapper() {}

    public static KeyBinding resolve(String raw) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null) return null;
        String k = raw.trim().toUpperCase(Locale.ROOT);

        return switch (k) {
            case "W" -> c.options.forwardKey;
            case "A" -> c.options.leftKey;
            case "S" -> c.options.backKey;
            case "D" -> c.options.rightKey;
            case "SPACE" -> c.options.jumpKey;
            case "SHIFT", "LSHIFT" -> c.options.sneakKey;
            case "CTRL", "LCTRL" -> c.options.sprintKey;
            case "NUM 1", "1" -> c.options.hotbarKeys[0];
            case "NUM 2", "2" -> c.options.hotbarKeys[1];
            case "NUM 3", "3" -> c.options.hotbarKeys[2];
            case "NUM 4", "4" -> c.options.hotbarKeys[3];
            case "NUM 5", "5" -> c.options.hotbarKeys[4];
            case "NUM 6", "6" -> c.options.hotbarKeys[5];
            case "NUM 7", "7" -> c.options.hotbarKeys[6];
            case "NUM 8", "8" -> c.options.hotbarKeys[7];
            case "NUM 9", "9" -> c.options.hotbarKeys[8];
            default -> null;
        };
    }
}

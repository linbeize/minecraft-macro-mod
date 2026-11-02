package de.linbei.macro.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Minecraft 26.2: screen handling moved to {@code Minecraft.gui}; key codes are still GLFW codes. */
public final class Compat {
    /** Old settings.properties stored GLFW key codes; only meaningful while the game uses GLFW codes. */
    public static final boolean LEGACY_GLFW_KEYCODES = true;

    private Compat() {}

    public static Screen screen(Minecraft mc) {
        return mc.gui.screen();
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        mc.gui.setScreen(screen);
    }
}

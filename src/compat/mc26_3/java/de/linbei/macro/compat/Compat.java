package de.linbei.macro.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Minecraft 26.3: screen handling on {@code Minecraft.gui}; input moved to SDL3, so key codes changed. */
public final class Compat {
    /** Old settings.properties stored GLFW key codes, which mean different keys under SDL3: ignore them. */
    public static final boolean LEGACY_GLFW_KEYCODES = false;

    private Compat() {}

    public static Screen screen(Minecraft mc) {
        return mc.gui.screen();
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        mc.gui.setScreen(screen);
    }
}

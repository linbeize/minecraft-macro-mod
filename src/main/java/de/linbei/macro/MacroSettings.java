package de.linbei.macro;

import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class MacroSettings {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("macro_mod").resolve("settings.properties");

    private MacroSettings() {}

    public static Settings load() {
        Settings s = new Settings();
        if (!Files.exists(FILE)) return s;

        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
            s.toggleKey = parseIntOrDefault(p.getProperty("toggleKey"), s.toggleKey);
            s.stopKey = parseIntOrDefault(p.getProperty("stopKey"), s.stopKey);
        } catch (IOException ignored) {
        }
        return s;
    }

    public static void save(Settings s) {
        try {
            Files.createDirectories(FILE.getParent());
            Properties p = new Properties();
            p.setProperty("toggleKey", Integer.toString(s.toggleKey));
            p.setProperty("stopKey", Integer.toString(s.stopKey));
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Macro mod keybind settings");
            }
        } catch (IOException ignored) {
        }
    }

    private static int parseIntOrDefault(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    public static class Settings {
        public int toggleKey = GLFW.GLFW_KEY_F8;
        public int stopKey = GLFW.GLFW_KEY_F9;
    }
}

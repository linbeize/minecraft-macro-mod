package de.linbei.macro;

import com.mojang.blaze3d.platform.InputConstants;
import de.linbei.macro.compat.Compat;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Mod preferences. Hotkeys are NOT stored here: they are normal key mappings, saved by vanilla
 * in options.txt and editable in Options → Controls. The old toggleKey/stopKey entries are
 * still read once, as defaults, so existing users keep their keys.
 */
public final class MacroSettings {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("macro_mod").resolve("settings.properties");

    private MacroSettings() {}

    public static Settings load() {
        Settings s = new Settings();
        if (!Files.exists(FILE)) return s;

        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
        } catch (IOException e) {
            MacroModClient.LOGGER.warn("Could not read {}", FILE, e);
            return s;
        }
        if (Compat.LEGACY_GLFW_KEYCODES) {
            s.legacyToggleKey = parseInt(p.getProperty("toggleKey"), s.legacyToggleKey);
            s.legacyStopKey = parseInt(p.getProperty("stopKey"), s.legacyStopKey);
        }
        s.repeat = Boolean.parseBoolean(p.getProperty("repeat", Boolean.toString(s.repeat)));
        s.stopOnContainer = Boolean.parseBoolean(p.getProperty("stopOnContainer", Boolean.toString(s.stopOnContainer)));
        s.showHud = Boolean.parseBoolean(p.getProperty("showHud", Boolean.toString(s.showHud)));
        s.lastScript = p.getProperty("lastScript", s.lastScript);
        s.imeEditor = Boolean.parseBoolean(p.getProperty("imeEditor", Boolean.toString(s.imeEditor)));
        return s;
    }

    public static void save(Settings s) {
        try {
            Files.createDirectories(FILE.getParent());
            Properties p = new Properties();
            p.setProperty("repeat", Boolean.toString(s.repeat));
            p.setProperty("stopOnContainer", Boolean.toString(s.stopOnContainer));
            p.setProperty("showHud", Boolean.toString(s.showHud));
            p.setProperty("imeEditor", Boolean.toString(s.imeEditor));
            if (s.lastScript != null) p.setProperty("lastScript", s.lastScript);
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Macro mod settings (hotkeys live in options.txt)");
            }
        } catch (IOException e) {
            MacroModClient.LOGGER.warn("Could not write {}", FILE, e);
        }
    }

    private static int parseInt(String s, int def) {
        if (s == null) return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static class Settings {
        /** Only used as the default key of the mapping; vanilla options.txt takes precedence. */
        public int legacyToggleKey = InputConstants.KEY_F8;
        public int legacyStopKey = InputConstants.KEY_F9;
        public boolean repeat = true;
        public boolean stopOnContainer = true;
        public boolean showHud = true;
        public String lastScript = "example";
        /** Vanilla text widgets with IME (Chinese input). Off by default: enabling IME crashed some clients (Lunar). */
        public boolean imeEditor = false;
    }
}

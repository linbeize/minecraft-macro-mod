package de.linbei.macro;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;

public class MacroModClient implements ClientModInitializer {
    private static final MacroEngine ENGINE = new MacroEngine();

    private static KeyMapping toggleKey;
    private static KeyMapping stopKey;
    private static KeyMapping aimLockKey;
    private static int toggleKeyCode;
    private static int stopKeyCode;
    private static Object lastWorldToken;

    @Override
    public void onInitializeClient() {
        MacroSettings.Settings settings = MacroSettings.load();
        toggleKeyCode = settings.toggleKey;
        stopKeyCode = settings.stopKey;

        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.macro_mod.toggle", InputConstants.Type.KEYSYM, toggleKeyCode, KeyMapping.Category.MISC
        ));
        stopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.macro_mod.stop", InputConstants.Type.KEYSYM, stopKeyCode, KeyMapping.Category.MISC
        ));
        aimLockKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.macro_mod.aimlock", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (lastWorldToken == null) {
                lastWorldToken = client.level;
            } else if (client.level != lastWorldToken) {
                lastWorldToken = client.level;
                if (ENGINE.isRunning()) {
                    ENGINE.stop();
                    msg(client, "World changed, macro auto-stopped");
                }
            }

            if (ENGINE.isRunning() && client.screen instanceof AbstractContainerScreen<?>) {
                ENGINE.stop();
                msg(client, "Inventory opened, macro auto-stopped");
            }

            while (toggleKey.consumeClick()) {
                if (!ENGINE.isRunning()) {
                    tryStart(client);
                } else {
                    ENGINE.pauseToggle();
                    msg(client, ENGINE.isPaused() ? "Macro paused" : "Macro resumed");
                }
            }
            while (stopKey.consumeClick()) {
                ENGINE.stop();
                msg(client, "Macro stopped");
            }
            while (aimLockKey.consumeClick()) {
                ENGINE.setAimLock(!ENGINE.isAimLock());
                msg(client, "Aim lock: " + (ENGINE.isAimLock() ? "ON" : "OFF"));
            }
            ENGINE.tick();
        });

        registerCommands();
        ensureExampleScript();
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("mmacro")
                        .then(ClientCommands.literal("start").executes(ctx -> {
                            tryStart(Minecraft.getInstance());
                            return 1;
                        }))
                        .then(ClientCommands.literal("pause").executes(ctx -> {
                            ENGINE.pauseToggle();
                            msg(Minecraft.getInstance(), ENGINE.isPaused() ? "Macro paused" : "Macro resumed");
                            return 1;
                        }))
                        .then(ClientCommands.literal("stop").executes(ctx -> {
                            ENGINE.stop();
                            msg(Minecraft.getInstance(), "Macro stopped");
                            return 1;
                        }))
                        .then(ClientCommands.literal("import")
                                .then(ClientCommands.argument("name", StringArgumentType.word()).executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    try {
                                        ENGINE.setScript(MacroStorage.load(name));
                                        msg(Minecraft.getInstance(), "Imported script: " + name);
                                    } catch (Exception e) {
                                        msg(Minecraft.getInstance(), "Import failed: " + e.getMessage());
                                    }
                                    return 1;
                                })))
                        .then(ClientCommands.literal("export")
                                .then(ClientCommands.argument("name", StringArgumentType.word()).executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    try {
                                        MacroStorage.save(name, ENGINE.getScript());
                                        msg(Minecraft.getInstance(), "Exported script: " + name);
                                    } catch (Exception e) {
                                        msg(Minecraft.getInstance(), "Export failed: " + e.getMessage());
                                    }
                                    return 1;
                                })))
                        .then(ClientCommands.literal("set")
                                .then(ClientCommands.argument("script", StringArgumentType.greedyString()).executes(ctx -> {
                                    String script = StringArgumentType.getString(ctx, "script");
                                    try {
                                        ENGINE.setScript(script);
                                        msg(Minecraft.getInstance(), "Script loaded in memory");
                                    } catch (Exception e) {
                                        msg(Minecraft.getInstance(), "Script parse error: " + e.getMessage());
                                    }
                                    return 1;
                                })))
                        .then(ClientCommands.literal("repeat")
                                .then(ClientCommands.literal("on").executes(ctx -> {
                                    ENGINE.setRepeat(true);
                                    msg(Minecraft.getInstance(), "Repeat: ON");
                                    return 1;
                                }))
                                .then(ClientCommands.literal("off").executes(ctx -> {
                                    ENGINE.setRepeat(false);
                                    msg(Minecraft.getInstance(), "Repeat: OFF");
                                    return 1;
                                })))
                        .then(ClientCommands.literal("aimlock")
                                .then(ClientCommands.literal("on").executes(ctx -> {
                                    ENGINE.setAimLock(true);
                                    msg(Minecraft.getInstance(), "Aim lock: ON");
                                    return 1;
                                }))
                                .then(ClientCommands.literal("off").executes(ctx -> {
                                    ENGINE.setAimLock(false);
                                    msg(Minecraft.getInstance(), "Aim lock: OFF");
                                    return 1;
                                })))
                        .then(ClientCommands.literal("where").executes(ctx -> {
                            try {
                                msg(Minecraft.getInstance(), "Scripts dir: " + MacroStorage.ensureDir());
                            } catch (IOException e) {
                                msg(Minecraft.getInstance(), "Path error: " + e.getMessage());
                            }
                            return 1;
                        }))
                        .then(ClientCommands.literal("gui").executes(ctx -> {
                            Minecraft client = Minecraft.getInstance();
                            client.execute(() -> client.setScreen(new MacroManagerScreen(ENGINE)));
                            msg(client, "Opening Macro GUI...");
                            return 1;
                        }))
        ));
    }

    private void tryStart(Minecraft client) {
        try {
            ENGINE.start();
            msg(client, "Macro started");
        } catch (Exception e) {
            msg(client, "Start failed: " + e.getMessage());
        }
    }

    private void ensureExampleScript() {
        String sample = """
                LeftDown 1,
                For 9,
                KeyDown "W", 1,
                KeyDown "D", 1,
                Delay 41000,
                KeyUp "W", 1,
                KeyUp "D", 1,
                Delay 500,
                KeyDown "W", 1,
                KeyDown "A", 1,
                Delay 41000,
                KeyUp "W", 1,
                KeyUp "A", 1,
                Delay 500,
                Next,
                KeyDown "W", 1,
                KeyDown "D", 1,
                Delay 41000,
                KeyUp "W", 1,
                KeyUp "D", 1,
                Delay 500,
                LeftUp 1,
                Delay 3000,
                KeyPress "Num 2", 1,
                Delay 2000
                """;
        try {
            MacroStorage.save("example", sample);
            if (ENGINE.getScript().isEmpty()) {
                ENGINE.setScript(sample);
            }
        } catch (Exception ignored) {
        }
    }

    public static String getToggleKeyName() {
        return toggleKey.getTranslatedKeyMessage().getString();
    }

    public static String getStopKeyName() {
        return stopKey.getTranslatedKeyMessage().getString();
    }

    public static void setToggleKeyCode(int keyCode) {
        toggleKeyCode = keyCode;
        toggleKey.setKey(InputConstants.Type.KEYSYM.getOrCreate(keyCode));
        KeyMapping.resetMapping();
        saveSettings();
    }

    public static void setStopKeyCode(int keyCode) {
        stopKeyCode = keyCode;
        stopKey.setKey(InputConstants.Type.KEYSYM.getOrCreate(keyCode));
        KeyMapping.resetMapping();
        saveSettings();
    }

    private static void saveSettings() {
        MacroSettings.Settings s = new MacroSettings.Settings();
        s.toggleKey = toggleKeyCode;
        s.stopKey = stopKeyCode;
        MacroSettings.save(s);
    }

    private static void msg(Minecraft client, String m) {
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal("[MacroMod] " + m));
        }
    }
}

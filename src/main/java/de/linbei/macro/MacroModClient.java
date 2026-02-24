package de.linbei.macro;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;

public class MacroModClient implements ClientModInitializer {
    private static final MacroEngine ENGINE = new MacroEngine();

    private static KeyBinding toggleKey;
    private static KeyBinding stopKey;
    private static KeyBinding aimLockKey;
    private static int toggleKeyCode;
    private static int stopKeyCode;
    private static Object lastWorldToken;

    @Override
    public void onInitializeClient() {
        MacroSettings.Settings settings = MacroSettings.load();
        toggleKeyCode = settings.toggleKey;
        stopKeyCode = settings.stopKey;

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macro_mod.toggle", InputUtil.Type.KEYSYM, toggleKeyCode, KeyBinding.Category.MISC
        ));
        stopKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macro_mod.stop", InputUtil.Type.KEYSYM, stopKeyCode, KeyBinding.Category.MISC
        ));
        aimLockKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.macro_mod.aimlock", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F7, KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (lastWorldToken == null) {
                lastWorldToken = client.world;
            } else if (client.world != lastWorldToken) {
                lastWorldToken = client.world;
                if (ENGINE.isRunning()) {
                    ENGINE.stop();
                    msg(client, "World changed, macro auto-stopped");
                }
            }

            while (toggleKey.wasPressed()) {
                if (!ENGINE.isRunning()) {
                    tryStart(client);
                } else {
                    ENGINE.pauseToggle();
                    msg(client, ENGINE.isPaused() ? "Macro paused" : "Macro resumed");
                }
            }
            while (stopKey.wasPressed()) {
                ENGINE.stop();
                msg(client, "Macro stopped");
            }
            while (aimLockKey.wasPressed()) {
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
                ClientCommandManager.literal("mmacro")
                        .then(ClientCommandManager.literal("start").executes(ctx -> {
                            tryStart(MinecraftClient.getInstance());
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("pause").executes(ctx -> {
                            ENGINE.pauseToggle();
                            msg(MinecraftClient.getInstance(), ENGINE.isPaused() ? "Macro paused" : "Macro resumed");
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("stop").executes(ctx -> {
                            ENGINE.stop();
                            msg(MinecraftClient.getInstance(), "Macro stopped");
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("import")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    try {
                                        ENGINE.setScript(MacroStorage.load(name));
                                        msg(MinecraftClient.getInstance(), "Imported script: " + name);
                                    } catch (Exception e) {
                                        msg(MinecraftClient.getInstance(), "Import failed: " + e.getMessage());
                                    }
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("export")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    try {
                                        MacroStorage.save(name, ENGINE.getScript());
                                        msg(MinecraftClient.getInstance(), "Exported script: " + name);
                                    } catch (Exception e) {
                                        msg(MinecraftClient.getInstance(), "Export failed: " + e.getMessage());
                                    }
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("script", StringArgumentType.greedyString()).executes(ctx -> {
                                    String script = StringArgumentType.getString(ctx, "script");
                                    try {
                                        ENGINE.setScript(script);
                                        msg(MinecraftClient.getInstance(), "Script loaded in memory");
                                    } catch (Exception e) {
                                        msg(MinecraftClient.getInstance(), "Script parse error: " + e.getMessage());
                                    }
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("repeat")
                                .then(ClientCommandManager.literal("on").executes(ctx -> {
                                    ENGINE.setRepeat(true);
                                    msg(MinecraftClient.getInstance(), "Repeat: ON");
                                    return 1;
                                }))
                                .then(ClientCommandManager.literal("off").executes(ctx -> {
                                    ENGINE.setRepeat(false);
                                    msg(MinecraftClient.getInstance(), "Repeat: OFF");
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("aimlock")
                                .then(ClientCommandManager.literal("on").executes(ctx -> {
                                    ENGINE.setAimLock(true);
                                    msg(MinecraftClient.getInstance(), "Aim lock: ON");
                                    return 1;
                                }))
                                .then(ClientCommandManager.literal("off").executes(ctx -> {
                                    ENGINE.setAimLock(false);
                                    msg(MinecraftClient.getInstance(), "Aim lock: OFF");
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("where").executes(ctx -> {
                            try {
                                msg(MinecraftClient.getInstance(), "Scripts dir: " + MacroStorage.ensureDir());
                            } catch (IOException e) {
                                msg(MinecraftClient.getInstance(), "Path error: " + e.getMessage());
                            }
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("gui").executes(ctx -> {
                            MinecraftClient client = MinecraftClient.getInstance();
                            client.execute(() -> client.setScreen(new MacroManagerScreen(ENGINE)));
                            msg(client, "Opening Macro GUI...");
                            return 1;
                        }))
        ));
    }

    private void tryStart(MinecraftClient client) {
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
        return toggleKey.getBoundKeyLocalizedText().getString();
    }

    public static String getStopKeyName() {
        return stopKey.getBoundKeyLocalizedText().getString();
    }

    public static void setToggleKeyCode(int keyCode) {
        toggleKeyCode = keyCode;
        toggleKey.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(keyCode));
        KeyBinding.updateKeysByCode();
        saveSettings();
    }

    public static void setStopKeyCode(int keyCode) {
        stopKeyCode = keyCode;
        stopKey.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(keyCode));
        KeyBinding.updateKeysByCode();
        saveSettings();
    }

    private static void saveSettings() {
        MacroSettings.Settings s = new MacroSettings.Settings();
        s.toggleKey = toggleKeyCode;
        s.stopKey = stopKeyCode;
        MacroSettings.save(s);
    }

    private static void msg(MinecraftClient client, String m) {
        if (client != null && client.player != null) client.player.sendMessage(Text.literal("[MacroMod] " + m), false);
    }
}

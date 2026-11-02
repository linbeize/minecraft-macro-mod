package de.linbei.macro;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.linbei.macro.compat.Compat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class MacroModClient implements ClientModInitializer {
    public static final String MOD_ID = "client_utilities";
    public static final Logger LOGGER = LoggerFactory.getLogger("MacroMod");
    public static final MacroEngine ENGINE = new MacroEngine();

    static final String EXAMPLE_SCRIPT = """
            # Example: walk W+D / W+A in a zig-zag while holding left click.
            # Commands: KeyDown/KeyUp/KeyPress "key", LeftDown/LeftUp/LeftClick,
            #           RightDown/RightUp/RightClick, Delay ms, For n ... Next
            LeftDown
            For 9
              KeyDown "W"; KeyDown "D"
              Delay 41000
              KeyUp "W"; KeyUp "D"
              Delay 500
              KeyDown "W"; KeyDown "A"
              Delay 41000
              KeyUp "W"; KeyUp "A"
              Delay 500
            Next
            KeyDown "W"; KeyDown "D"
            Delay 41000
            KeyUp "W"; KeyUp "D"
            Delay 500
            LeftUp
            Delay 3000
            KeyPress "2"
            Delay 2000
            """;

    private static MacroSettings.Settings settings;
    private static KeyMapping toggleKey;
    private static KeyMapping stopKey;
    private static KeyMapping aimLockKey;
    private static KeyMapping guiKey;
    private static ClientLevel lastLevel;

    @Override
    public void onInitializeClient() {
        settings = MacroSettings.load();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "macro"));
        // Mapping names are unchanged from earlier versions so saved bindings in options.txt survive.
        toggleKey = registerKey("key.macro_mod.toggle", settings.legacyToggleKey, category);
        stopKey = registerKey("key.macro_mod.stop", settings.legacyStopKey, category);
        aimLockKey = registerKey("key.macro_mod.aimlock", InputConstants.KEY_F7, category);
        guiKey = registerKey("key.macro_mod.gui", InputConstants.KEY_F6, category);

        ENGINE.setRepeat(settings.repeat);
        ENGINE.setOnFinished(() -> notify(Component.translatable("macro_mod.msg.finished"), false));

        ensureExampleScript();
        loadInitialScript();

        ClientTickEvents.END_CLIENT_TICK.register(MacroModClient::onTick);
        registerCommands();
        MacroHud.register();
    }

    private static KeyMapping registerKey(String name, int defaultKey, KeyMapping.Category category) {
        // The (name, key, category) constructor exists in every supported version (the Type enum was renamed in 26.3).
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, defaultKey, category));
    }

    // ------------------------------------------------------------------ tick

    private static void onTick(Minecraft client) {
        if (client.level != lastLevel) {
            lastLevel = client.level;
            if (!ENGINE.isStopped()) {
                ENGINE.stop();
                notify(Component.translatable("macro_mod.msg.world_changed"), false);
            }
        }

        if (settings.stopOnContainer && !ENGINE.isStopped() && Compat.screen(client) instanceof AbstractContainerScreen<?>) {
            ENGINE.stop();
            notify(Component.translatable("macro_mod.msg.container_opened"), false);
        }

        while (toggleKey.consumeClick()) notify(toggleRun(), true);
        while (stopKey.consumeClick()) notify(stop(), true);
        while (aimLockKey.consumeClick()) notify(setAimLock(!ENGINE.isAimLock()), true);
        while (guiKey.consumeClick()) Compat.setScreen(client, new MacroManagerScreen());

        ENGINE.tick(client);
    }

    // ------------------------------------------------------------------ shared actions (keys, commands, GUI)

    public static MacroSettings.Settings settings() { return settings; }

    public static KeyMapping toggleKey() { return toggleKey; }
    public static KeyMapping stopKey() { return stopKey; }

    /** Start, pause or resume. */
    public static Component toggleRun() {
        try {
            if (ENGINE.isStopped()) {
                ENGINE.start();
                return Component.translatable("macro_mod.msg.started", scriptLabel());
            }
            ENGINE.pauseToggle();
            return Component.translatable(ENGINE.isPaused() ? "macro_mod.msg.paused" : "macro_mod.msg.resumed");
        } catch (Exception e) {
            return error("macro_mod.msg.start_failed", e);
        }
    }

    public static Component stop() {
        boolean wasRunning = !ENGINE.isStopped();
        ENGINE.stop();
        return Component.translatable(wasRunning ? "macro_mod.msg.stopped" : "macro_mod.msg.not_running");
    }

    public static Component setRepeat(boolean on) {
        ENGINE.setRepeat(on);
        settings.repeat = on;
        MacroSettings.save(settings);
        return Component.translatable("macro_mod.msg.repeat", onOff(on));
    }

    public static Component setAimLock(boolean on) {
        ENGINE.setAimLock(on);
        return Component.translatable("macro_mod.msg.aimlock", onOff(on));
    }

    public static Component setShowHud(boolean on) {
        settings.showHud = on;
        MacroSettings.save(settings);
        return Component.translatable("macro_mod.msg.hud", onOff(on));
    }

    public static void setImeEditor(boolean on) {
        settings.imeEditor = on;
        MacroSettings.save(settings);
    }

    /** Loads a saved script into the engine (stops a running macro). */
    public static void loadScript(String name) throws IOException {
        String n = MacroStorage.normalizeName(name);
        ENGINE.setScript(MacroStorage.load(n), n);
        rememberLastScript(n);
    }

    public static void rememberLastScript(String name) {
        settings.lastScript = name;
        MacroSettings.save(settings);
    }

    public static Component onOff(boolean on) {
        return Component.translatable(on ? "options.on" : "options.off");
    }

    private static Component scriptLabel() {
        String n = ENGINE.getScriptName();
        return n == null ? Component.translatable("macro_mod.status.unsaved_script") : Component.literal(n);
    }

    /** Turns any exception into a readable, translated message. */
    public static MutableComponent describeError(Exception e) {
        if (e instanceof MacroParser.ParseException pe) {
            return Component.translatable("macro_mod.parse.at_line", pe.line(),
                    Component.translatable(pe.key(), pe.args()));
        }
        if (e instanceof java.nio.file.NoSuchFileException nf) {
            return Component.translatable("macro_mod.error.no_such_script", nf.getFile());
        }
        if (e instanceof java.nio.file.FileAlreadyExistsException fe) {
            return Component.translatable("macro_mod.error.already_exists", fe.getFile());
        }
        String m = e.getMessage();
        return Component.literal(m == null ? e.getClass().getSimpleName() : m);
    }

    private static Component error(String key, Exception e) {
        return Component.translatable(key, describeError(e)).withStyle(ChatFormatting.RED);
    }

    /** Overlay (action bar) for frequent state changes, chat for things worth keeping. */
    public static void notify(Component message, boolean overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Component full = Component.literal("[Macro] ").withStyle(ChatFormatting.GOLD).append(message);
        if (overlay) {
            mc.player.sendOverlayMessage(full);
        } else {
            mc.player.sendSystemMessage(full);
        }
    }

    // ------------------------------------------------------------------ startup

    private static void ensureExampleScript() {
        // Only create it once: never overwrite a user's edits.
        if (MacroStorage.exists("example")) return;
        try {
            MacroStorage.save("example", EXAMPLE_SCRIPT);
        } catch (IOException e) {
            LOGGER.warn("Could not write example script", e);
        }
    }

    private static void loadInitialScript() {
        String name = settings.lastScript;
        if (name != null && MacroStorage.exists(name)) {
            try {
                ENGINE.setScript(MacroStorage.load(name), MacroStorage.normalizeName(name));
                return;
            } catch (Exception e) {
                LOGGER.warn("Could not load last script '{}': {}", name, e.getMessage());
            }
        }
        ENGINE.setScript(EXAMPLE_SCRIPT, "example");
    }

    // ------------------------------------------------------------------ commands

    private static final SuggestionProvider<FabricClientCommandSource> SCRIPT_NAMES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(MacroStorage.listScriptNamesQuietly(), builder);

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("mmacro")
                    .executes(ctx -> reply(ctx, Component.translatable("macro_mod.cmd.help")))
                    .then(simple("start", ctx -> reply(ctx, ENGINE.isRunning()
                            ? Component.translatable("macro_mod.msg.already_running") : toggleRun())))
                    .then(simple("pause", ctx -> reply(ctx, ENGINE.isStopped()
                            ? Component.translatable("macro_mod.msg.not_running") : toggleRun())))
                    .then(simple("stop", ctx -> reply(ctx, stop())))
                    .then(simple("status", ctx -> reply(ctx, MacroStatus.describe(ENGINE))))
                    .then(simple("list", MacroModClient::listCommand))
                    .then(simple("where", ctx -> {
                        try {
                            return reply(ctx, Component.translatable("macro_mod.msg.where", MacroStorage.ensureDir().toString()));
                        } catch (IOException e) {
                            return fail(ctx, describeError(e));
                        }
                    }))
                    .then(simple("gui", ctx -> {
                        Minecraft mc = ctx.getSource().getClient();
                        // Defer: the chat screen is still closing while the command runs.
                        mc.execute(() -> Compat.setScreen(mc, new MacroManagerScreen()));
                        return 1;
                    }))
                    .then(nameCommand("load"))
                    .then(nameCommand("import"))
                    .then(ClientCommands.literal("save").then(ClientCommands.argument("name", StringArgumentType.string())
                            .suggests(SCRIPT_NAMES).executes(MacroModClient::saveCommand)))
                    .then(ClientCommands.literal("export").then(ClientCommands.argument("name", StringArgumentType.string())
                            .suggests(SCRIPT_NAMES).executes(MacroModClient::saveCommand)))
                    .then(ClientCommands.literal("set").then(ClientCommands.argument("script", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                try {
                                    ENGINE.setScript(StringArgumentType.getString(ctx, "script"), null);
                                    return reply(ctx, Component.translatable("macro_mod.msg.set", ENGINE.instructionCount()));
                                } catch (Exception e) {
                                    return fail(ctx, describeError(e));
                                }
                            })))
                    .then(toggle("repeat", MacroModClient::setRepeat))
                    .then(toggle("aimlock", MacroModClient::setAimLock))
                    .then(toggle("hud", MacroModClient::setShowHud));
            dispatcher.register(root);
        });
    }

    private interface Action {
        int run(CommandContext<FabricClientCommandSource> ctx);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> simple(String name, Action action) {
        return ClientCommands.literal(name).executes(action::run);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> nameCommand(String literal) {
        return ClientCommands.literal(literal).then(ClientCommands.argument("name", StringArgumentType.string())
                .suggests(SCRIPT_NAMES)
                .executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    try {
                        loadScript(name);
                        return reply(ctx, Component.translatable("macro_mod.msg.loaded", ENGINE.getScriptName(), ENGINE.instructionCount()));
                    } catch (Exception e) {
                        return fail(ctx, Component.translatable("macro_mod.msg.load_failed", describeError(e)));
                    }
                }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> toggle(String name, java.util.function.Function<Boolean, Component> setter) {
        return ClientCommands.literal(name)
                .then(ClientCommands.literal("on").executes(ctx -> reply(ctx, setter.apply(true))))
                .then(ClientCommands.literal("off").executes(ctx -> reply(ctx, setter.apply(false))));
    }

    private static int saveCommand(CommandContext<FabricClientCommandSource> ctx) {
        String name = MacroStorage.normalizeName(StringArgumentType.getString(ctx, "name"));
        try {
            MacroStorage.save(name, ENGINE.getScript());
            ENGINE.setScriptName(name);
            rememberLastScript(name);
            return reply(ctx, Component.translatable("macro_mod.msg.saved", name));
        } catch (Exception e) {
            return fail(ctx, Component.translatable("macro_mod.msg.save_failed", describeError(e)));
        }
    }

    private static int listCommand(CommandContext<FabricClientCommandSource> ctx) {
        try {
            var names = MacroStorage.listScriptNames();
            if (names.isEmpty()) return reply(ctx, Component.translatable("macro_mod.msg.list_empty"));
            return reply(ctx, Component.translatable("macro_mod.msg.list", names.size(), String.join(", ", names)));
        } catch (IOException e) {
            return fail(ctx, describeError(e));
        }
    }

    private static int reply(CommandContext<FabricClientCommandSource> ctx, Component msg) {
        ctx.getSource().sendFeedback(Component.literal("[Macro] ").withStyle(ChatFormatting.GOLD).append(msg));
        return 1;
    }

    private static int fail(CommandContext<FabricClientCommandSource> ctx, Component msg) {
        ctx.getSource().sendError(msg);
        return 0;
    }
}

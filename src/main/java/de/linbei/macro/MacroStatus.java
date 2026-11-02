package de.linbei.macro;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/** Human-readable engine status, shared by the HUD, the GUI and /mmacro status. */
public final class MacroStatus {
    public static final int COLOR_RUNNING = 0xFF55FF55;
    public static final int COLOR_PAUSED = 0xFFFFD040;
    public static final int COLOR_STOPPED = 0xFFA0A0A0;

    private MacroStatus() {}

    public static int color(MacroEngine e) {
        return switch (e.state()) {
            case RUNNING -> e.isSuspended() ? COLOR_PAUSED : COLOR_RUNNING;
            case PAUSED -> COLOR_PAUSED;
            case STOPPED -> COLOR_STOPPED;
        };
    }

    /** Short state label, e.g. "▶ Running". */
    public static MutableComponent stateLabel(MacroEngine e) {
        return switch (e.state()) {
            case RUNNING -> e.isSuspended()
                    ? Component.translatable("macro_mod.state.suspended")
                    : Component.translatable("macro_mod.state.running");
            case PAUSED -> Component.translatable("macro_mod.state.paused");
            case STOPPED -> Component.translatable("macro_mod.state.stopped");
        };
    }

    /** Full one-line description: state · script · line · delay · loop · run count. */
    public static MutableComponent describe(MacroEngine e) {
        MutableComponent c = stateLabel(e);
        String name = e.getScriptName();
        c.append(sep()).append(name == null
                ? Component.translatable("macro_mod.status.unsaved_script")
                : Component.literal(name));
        if (e.isStopped()) {
            return c.append(sep()).append(Component.translatable("macro_mod.status.instructions", e.instructionCount()));
        }
        if (e.currentLine() > 0) {
            c.append(sep()).append(Component.translatable("macro_mod.status.line", e.currentLine()));
        }
        long wait = e.remainingDelayMs();
        if (wait > 0) {
            c.append(sep()).append(Component.translatable("macro_mod.status.wait", formatSeconds(wait)));
        }
        int[] loop = e.innermostLoop();
        if (loop != null) {
            c.append(sep()).append(Component.translatable("macro_mod.status.loop", loop[0], loop[1]));
        }
        if (e.isRepeat()) {
            c.append(sep()).append(Component.translatable("macro_mod.status.run", e.completedRuns() + 1));
        }
        return c;
    }

    public static String formatSeconds(long ms) {
        return String.format(Locale.ROOT, "%.1fs", ms / 1000.0);
    }

    private static Component sep() {
        return Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY);
    }
}

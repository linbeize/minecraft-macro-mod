package de.linbei.macro;

import com.mojang.blaze3d.platform.InputConstants;
import de.linbei.macro.compat.Compat;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs a parsed macro on the client tick.
 *
 * <p>Timing uses a virtual clock that only advances while the macro is actually running
 * (not paused, no screen open), so pausing or opening a menu never eats into a Delay.
 * Delays are scheduled from the ideal end of the previous delay rather than from the tick
 * on which it was noticed, so long loops do not drift.
 */
public class MacroEngine {
    public enum State { STOPPED, RUNNING, PAUSED }

    /** Guards against For loops without any Delay freezing the game. */
    private static final int MAX_STEPS_PER_TICK = 10_000;
    /** After a lag spike larger than this, resynchronise instead of replaying all missed steps at once. */
    private static final long MAX_LAG_MS = 1_000;

    private List<MacroInstruction> program = List.of();
    private final Deque<LoopFrame> loopStack = new ArrayDeque<>();
    private final Set<KeyMapping> held = new LinkedHashSet<>();

    private String script = "";
    private String scriptName;
    private State state = State.STOPPED;
    private boolean suspended;

    private int pc;
    private int lastLine = -1;
    private long clock;
    private long scheduled;
    private long waitUntil;
    private long lastRealMs = -1;
    private int completedRuns;

    private boolean repeat = true;
    private boolean aimLock;
    private float lockedYaw;
    private float lockedPitch;

    private Runnable onFinished = () -> {};

    // ---------------------------------------------------------------- script

    /**
     * Parses and installs a script. The current script is left untouched if parsing fails.
     * A running macro is stopped first so that no key stays pressed.
     */
    public void setScript(String text, String name) {
        List<MacroInstruction> parsed = MacroParser.parse(text);
        if (state != State.STOPPED) stop();
        program = List.copyOf(parsed);
        script = text;
        scriptName = name;
        resetRuntime();
    }

    public String getScript() { return script; }
    public String getScriptName() { return scriptName; }
    public void setScriptName(String name) { this.scriptName = name; }
    public int instructionCount() { return program.size(); }

    // ---------------------------------------------------------------- state

    public State state() { return state; }
    public boolean isRunning() { return state == State.RUNNING; }
    public boolean isPaused() { return state == State.PAUSED; }
    public boolean isStopped() { return state == State.STOPPED; }
    /** True while running but frozen because a screen (menu, chat, ...) is open. */
    public boolean isSuspended() { return state == State.RUNNING && suspended; }
    public boolean isRepeat() { return repeat; }
    public boolean isAimLock() { return aimLock; }
    /** Used by the turn mixin: block mouse look only while the lock is actually being applied. */
    public boolean isAimLockActive() { return aimLock && state == State.RUNNING; }

    public void setRepeat(boolean repeat) { this.repeat = repeat; }
    public void setOnFinished(Runnable onFinished) { this.onFinished = onFinished; }

    public void setAimLock(boolean aimLock) {
        this.aimLock = aimLock;
        if (aimLock) captureAim();
    }

    public void start() {
        if (program.isEmpty()) throw new IllegalStateException("No script loaded");
        if (state == State.PAUSED) {
            resume();
            return;
        }
        if (state == State.RUNNING) return;
        resetRuntime();
        completedRuns = 0;
        suspended = false;
        state = State.RUNNING;
        captureAim();
    }

    public void pauseToggle() {
        if (state == State.RUNNING) {
            state = State.PAUSED;
            releasePhysical();
        } else if (state == State.PAUSED) {
            resume();
        }
    }

    private void resume() {
        state = State.RUNNING;
        captureAim();
        // held keys are pressed again on the next tick
    }

    public void stop() {
        releaseAll();
        state = State.STOPPED;
        suspended = false;
        resetRuntime();
    }

    // ---------------------------------------------------------------- status (HUD / GUI)

    public int currentLine() { return lastLine; }
    public long remainingDelayMs() { return Math.max(0, waitUntil - clock); }
    public int completedRuns() { return completedRuns; }

    /** @return {current iteration, total} of the innermost loop, or {@code null} outside loops */
    public int[] innermostLoop() {
        LoopFrame f = loopStack.peek();
        return f == null ? null : new int[]{f.total - f.remaining + 1, f.total};
    }

    // ---------------------------------------------------------------- tick

    public void tick(Minecraft client) {
        long real = System.currentTimeMillis();
        long delta = lastRealMs < 0 ? 0 : Math.max(0, real - lastRealMs);
        lastRealMs = real;

        if (state != State.RUNNING) return;
        if (client.player == null) {
            stop();
            return;
        }
        if (Compat.screen(client) != null) {
            // Vanilla releases every key when a screen opens; freeze until it closes.
            suspended = true;
            return;
        }
        suspended = false;
        clock += delta;

        if (aimLock) enforceAimLock(client.player);
        // Re-assert held keys: vanilla may have released them (screen, focus loss, ...).
        for (KeyMapping k : held) k.setDown(true);

        if (scheduled < clock - MAX_LAG_MS) scheduled = clock;

        int steps = 0;
        while (state == State.RUNNING && waitUntil <= clock) {
            if (pc >= program.size()) {
                completedRuns++;
                if (!repeat) {
                    stop();
                    onFinished.run();
                    return;
                }
                pc = 0;
                loopStack.clear();
                return; // at most one pass per tick, so a script without Delay cannot spin
            }
            if (++steps > MAX_STEPS_PER_TICK) return;
            execute(program.get(pc), client);
        }
    }

    private void execute(MacroInstruction ins, Minecraft client) {
        lastLine = ins.line();
        switch (ins.type()) {
            case KEY_DOWN -> {
                KeyMapping kb = KeyMapper.resolve(client.options, ins.key());
                if (kb != null) {
                    kb.setDown(true);
                    held.add(kb);
                }
                pc++;
            }
            case KEY_UP -> {
                KeyMapping kb = KeyMapper.resolve(client.options, ins.key());
                if (kb != null) {
                    kb.setDown(false);
                    held.remove(kb);
                }
                pc++;
            }
            case KEY_PRESS -> {
                // setDown(true)+setDown(false) does not register a click; vanilla handles
                // hotbar/drop/inventory/attack/use via consumeClick(), so emit real clicks.
                KeyMapping kb = KeyMapper.resolve(client.options, ins.key());
                // Unbound mappings all share InputConstants.UNKNOWN; clicking it would hit every one of them.
                if (kb != null && !kb.isUnbound()) {
                    InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(kb);
                    for (long i = 0; i < ins.number(); i++) KeyMapping.click(bound);
                }
                pc++;
            }
            case DELAY -> {
                scheduled += ins.number();
                waitUntil = scheduled;
                pc++;
            }
            case FOR -> {
                int times = (int) ins.number();
                if (times <= 0) {
                    pc = ins.jumpIndex() + 1;
                } else {
                    loopStack.push(new LoopFrame(pc, times));
                    pc++;
                }
            }
            case NEXT -> {
                LoopFrame frame = loopStack.peek();
                if (frame == null) {
                    pc++;
                    break;
                }
                frame.remaining--;
                if (frame.remaining > 0) {
                    pc = frame.forIndex + 1;
                } else {
                    loopStack.pop();
                    pc++;
                }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private void captureAim() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) {
            lockedYaw = p.getYRot();
            lockedPitch = p.getXRot();
        }
    }

    private void enforceAimLock(LocalPlayer p) {
        p.setYRot(lockedYaw);
        p.setXRot(lockedPitch);
        p.yRotO = lockedYaw;
        p.xRotO = lockedPitch;
    }

    /** Lifts the keys in-game but remembers them so they are pressed again on resume. */
    private void releasePhysical() {
        for (KeyMapping kb : held) kb.setDown(false);
    }

    private void releaseAll() {
        releasePhysical();
        held.clear();
    }

    private void resetRuntime() {
        pc = 0;
        lastLine = -1;
        loopStack.clear();
        scheduled = clock;
        waitUntil = clock;
    }

    private static final class LoopFrame {
        final int forIndex;
        final int total;
        int remaining;

        LoopFrame(int forIndex, int total) {
            this.forIndex = forIndex;
            this.total = total;
            this.remaining = total;
        }
    }
}

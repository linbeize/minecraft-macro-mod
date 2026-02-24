package de.linbei.macro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import java.util.*;

public class MacroEngine {
    private final List<MacroInstruction> program = new ArrayList<>();
    private final Deque<LoopFrame> loopStack = new ArrayDeque<>();
    private final Set<KeyBinding> held = new HashSet<>();

    private String script = "";
    private int pc = 0;
    private long waitUntil = 0L;
    private boolean running = false;
    private boolean paused = false;

    private boolean repeat = true;
    private boolean aimLock = false;
    private boolean leftMouseHeld = false;
    private float lockedYaw = 0f;
    private float lockedPitch = 0f;

    public void setScript(String scriptText) {
        this.script = scriptText;
        this.program.clear();
        this.program.addAll(MacroParser.parse(scriptText));
        resetRuntime();
    }

    public String getScript() { return script; }
    public boolean isRunning() { return running; }
    public boolean isPaused() { return paused; }
    public boolean isRepeat() { return repeat; }
    public boolean isAimLock() { return aimLock; }

    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }

    public void setAimLock(boolean aimLock) {
        this.aimLock = aimLock;
        if (aimLock) {
            captureAim();
        }
    }

    public void start() {
        if (program.isEmpty()) throw new IllegalStateException("No script loaded");
        running = true;
        paused = false;
        captureAim();
    }

    public void pauseToggle() {
        if (!running) return;
        paused = !paused;
    }

    public void stop() {
        running = false;
        paused = false;
        releaseAll();
        resetRuntime();
    }

    public void tick() {
        if (!running || paused) return;

        if (aimLock) {
            enforceAimLock();
        }
        if (leftMouseHeld) {
            MinecraftClient.getInstance().options.attackKey.setPressed(true);
        }

        long now = System.currentTimeMillis();
        if (waitUntil > now) return;

        if (pc < 0 || pc >= program.size()) {
            if (repeat) {
                resetRuntime();
                captureAim();
                return;
            }
            stop();
            return;
        }

        MacroInstruction ins = program.get(pc);
        switch (ins.type()) {
            case KEY_DOWN -> {
                KeyBinding kb = KeyMapper.resolve(ins.arg());
                if (kb != null) {
                    kb.setPressed(true);
                    held.add(kb);
                }
                pc++;
            }
            case KEY_UP -> {
                KeyBinding kb = KeyMapper.resolve(ins.arg());
                if (kb != null) {
                    kb.setPressed(false);
                    held.remove(kb);
                }
                pc++;
            }
            case KEY_PRESS -> {
                KeyBinding kb = KeyMapper.resolve(ins.arg());
                if (kb != null) {
                    kb.setPressed(true);
                    kb.setPressed(false);
                }
                pc++;
            }
            case LEFT_DOWN -> {
                leftMouseHeld = true;
                MinecraftClient.getInstance().options.attackKey.setPressed(true);
                pc++;
            }
            case LEFT_UP -> {
                leftMouseHeld = false;
                MinecraftClient.getInstance().options.attackKey.setPressed(false);
                pc++;
            }
            case DELAY -> {
                waitUntil = now + ins.number();
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
                if (loopStack.isEmpty()) {
                    pc++;
                    break;
                }
                LoopFrame frame = loopStack.peek();
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

    private void captureAim() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            lockedYaw = client.player.getYaw();
            lockedPitch = client.player.getPitch();
        }
    }

    private void enforceAimLock() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.setYaw(lockedYaw);
            client.player.setPitch(lockedPitch);
        }
    }

    private void releaseAll() {
        for (KeyBinding kb : held) kb.setPressed(false);
        held.clear();
        leftMouseHeld = false;
        MinecraftClient.getInstance().options.attackKey.setPressed(false);
    }

    private void resetRuntime() {
        pc = 0;
        waitUntil = 0L;
        loopStack.clear();
    }

    private static class LoopFrame {
        int forIndex;
        int remaining;
        LoopFrame(int forIndex, int remaining) {
            this.forIndex = forIndex;
            this.remaining = remaining;
        }
    }
}

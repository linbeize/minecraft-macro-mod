package de.linbei.macro;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.*;

public class MacroEngine {
    private final List<MacroInstruction> program = new ArrayList<>();
    private final Deque<LoopFrame> loopStack = new ArrayDeque<>();
    private final Set<KeyMapping> held = new HashSet<>();

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

        Minecraft client = Minecraft.getInstance();
        if (client == null) return;

        if (aimLock) {
            enforceAimLock();
        }
        if (leftMouseHeld) {
            client.options.keyAttack.setDown(true);
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
                KeyMapping kb = KeyMapper.resolve(ins.arg());
                if (kb != null) {
                    kb.setDown(true);
                    held.add(kb);
                }
                pc++;
            }
            case KEY_UP -> {
                KeyMapping kb = KeyMapper.resolve(ins.arg());
                if (kb != null) {
                    kb.setDown(false);
                    held.remove(kb);
                }
                pc++;
            }
            case KEY_PRESS -> {
                KeyMapping kb = KeyMapper.resolve(ins.arg());
                if (kb != null) {
                    kb.setDown(true);
                    kb.setDown(false);
                }
                pc++;
            }
            case LEFT_DOWN -> {
                leftMouseHeld = true;
                client.options.keyAttack.setDown(true);
                pc++;
            }
            case LEFT_UP -> {
                leftMouseHeld = false;
                client.options.keyAttack.setDown(false);
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
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            lockedYaw = client.player.getYRot();
            lockedPitch = client.player.getXRot();
        }
    }

    private void enforceAimLock() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.setYRot(lockedYaw);
            client.player.setXRot(lockedPitch);
        }
    }

    private void releaseAll() {
        for (KeyMapping kb : held) kb.setDown(false);
        held.clear();
        leftMouseHeld = false;
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.options.keyAttack.setDown(false);
        }
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

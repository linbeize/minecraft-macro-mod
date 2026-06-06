package de.linbei.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class MacroManagerScreen extends Screen {
    private final MacroEngine engine;

    private final List<String> scripts = new ArrayList<>();
    private int scriptIndex = 0;
    private String status = "";

    private EditBox nameInput;
    private Button repeatBtn;
    private Button aimLockBtn;
    private Button toggleBindBtn;
    private Button stopBindBtn;
    private BindTarget waitingBind = BindTarget.NONE;

    public MacroManagerScreen(MacroEngine engine) {
        super(Component.literal("Macro Manager"));
        this.engine = engine;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 155;
        int y = 32;

        refreshScripts();

        nameInput = new EditBox(this.font, left, y, 160, 20, Component.literal("script name"));
        nameInput.setMaxLength(64);
        if (!scripts.isEmpty()) nameInput.setValue(scripts.get(scriptIndex));
        this.addRenderableWidget(nameInput);

        this.addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> refreshScripts())
                .bounds(left + 170, y, 70, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Prev"), b -> stepScript(-1))
                .bounds(left + 245, y, 60, 20).build());

        y += 26;
        this.addRenderableWidget(Button.builder(Component.literal("Next"), b -> stepScript(1))
                .bounds(left, y, 60, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Load"), b -> loadSelected())
                .bounds(left + 65, y, 60, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> saveCurrent())
                .bounds(left + 130, y, 60, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Delete"), b -> deleteSelected())
                .bounds(left + 195, y, 60, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("New Template"), b -> newTemplate())
                .bounds(left + 260, y, 95, 20).build());

        y += 30;
        this.addRenderableWidget(Button.builder(Component.literal("Start / Pause"), b -> toggleRun())
                .bounds(left, y, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Stop"), b -> {
                    engine.stop();
                    status = "Stopped";
                })
                .bounds(left + 105, y, 60, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(left + 170, y, 70, 20).build());

        repeatBtn = this.addRenderableWidget(Button.builder(Component.literal(""), b -> {
                    engine.setRepeat(!engine.isRepeat());
                    refreshToggleLabels();
                    status = "Repeat: " + (engine.isRepeat() ? "ON" : "OFF");
                })
                .bounds(left + 245, y, 110, 20).build());

        y += 26;
        aimLockBtn = this.addRenderableWidget(Button.builder(Component.literal(""), b -> {
                    engine.setAimLock(!engine.isAimLock());
                    refreshToggleLabels();
                    status = "Aim lock: " + (engine.isAimLock() ? "ON" : "OFF");
                })
                .bounds(left, y, 140, 20).build());

        toggleBindBtn = this.addRenderableWidget(Button.builder(Component.literal(""), b -> {
                    waitingBind = BindTarget.TOGGLE;
                    refreshToggleLabels();
                    status = "Press a key for Start/Pause hotkey...";
                })
                .bounds(left + 145, y, 170, 20).build());

        y += 26;
        stopBindBtn = this.addRenderableWidget(Button.builder(Component.literal(""), b -> {
                    waitingBind = BindTarget.STOP;
                    refreshToggleLabels();
                    status = "Press a key for Stop hotkey...";
                })
                .bounds(left, y, 170, 20).build());

        refreshToggleLabels();
    }

    private void refreshScripts() {
        try {
            String current = currentName();
            scripts.clear();
            scripts.addAll(MacroStorage.listScriptNames());
            if (scripts.isEmpty()) {
                scriptIndex = 0;
            } else if (current != null) {
                int idx = scripts.indexOf(current);
                scriptIndex = idx >= 0 ? idx : 0;
            } else {
                scriptIndex = Math.min(scriptIndex, scripts.size() - 1);
            }
            if (nameInput != null && !scripts.isEmpty()) nameInput.setValue(scripts.get(scriptIndex));
            status = "Loaded list: " + scripts.size() + " script(s)";
        } catch (Exception e) {
            status = "Refresh failed: " + e.getMessage();
        }
    }

    private String currentName() {
        String n = nameInput == null ? "" : nameInput.getValue().trim();
        if (n.toLowerCase().endsWith(".txt")) n = n.substring(0, n.length() - 4);
        return n.isEmpty() ? null : n;
    }

    private void stepScript(int delta) {
        if (scripts.isEmpty()) return;
        scriptIndex = (scriptIndex + delta + scripts.size()) % scripts.size();
        nameInput.setValue(scripts.get(scriptIndex));
    }

    private void loadSelected() {
        String name = currentName();
        if (name == null) {
            status = "Name is empty";
            return;
        }
        try {
            engine.setScript(MacroStorage.load(name));
            status = "Loaded: " + name;
        } catch (Exception e) {
            status = "Load failed: " + e.getMessage();
        }
    }

    private void saveCurrent() {
        String name = currentName();
        if (name == null) {
            status = "Name is empty";
            return;
        }
        try {
            MacroStorage.save(name, engine.getScript());
            refreshScripts();
            status = "Saved: " + name;
        } catch (Exception e) {
            status = "Save failed: " + e.getMessage();
        }
    }

    private void deleteSelected() {
        String name = currentName();
        if (name == null) {
            status = "Name is empty";
            return;
        }
        try {
            MacroStorage.delete(name);
            refreshScripts();
            status = "Deleted: " + name;
        } catch (Exception e) {
            status = "Delete failed: " + e.getMessage();
        }
    }

    private void newTemplate() {
        String name = currentName();
        if (name == null) {
            status = "Name is empty";
            return;
        }
        String tpl = "LeftDown\nFor 3\nKeyDown \"W\"\nDelay 1200\nKeyUp \"W\"\nDelay 200\nKeyPress \"Num 2\"\nDelay 500\nNext\nLeftUp\n";
        try {
            MacroStorage.save(name, tpl);
            engine.setScript(tpl);
            refreshScripts();
            status = "Template created: " + name;
        } catch (Exception e) {
            status = "Template failed: " + e.getMessage();
        }
    }

    private void refreshToggleLabels() {
        if (repeatBtn != null) {
            repeatBtn.setMessage(Component.literal("Repeat: " + (engine.isRepeat() ? "ON" : "OFF")));
        }
        if (aimLockBtn != null) {
            aimLockBtn.setMessage(Component.literal("AimLock: " + (engine.isAimLock() ? "ON" : "OFF")));
        }
        if (toggleBindBtn != null) {
            String t = waitingBind == BindTarget.TOGGLE ? "Start/Pause: [Press key...]" : "Start/Pause: " + MacroModClient.getToggleKeyName();
            toggleBindBtn.setMessage(Component.literal(t));
        }
        if (stopBindBtn != null) {
            String t = waitingBind == BindTarget.STOP ? "Stop: [Press key...]" : "Stop: " + MacroModClient.getStopKeyName();
            stopBindBtn.setMessage(Component.literal(t));
        }
    }

    private void toggleRun() {
        try {
            if (!engine.isRunning()) {
                if (engine.getScript().isBlank()) {
                    loadSelected();
                }
                engine.start();
                status = "Started";
            } else {
                engine.pauseToggle();
                status = engine.isPaused() ? "Paused" : "Resumed";
            }
        } catch (Exception e) {
            status = "Run failed: " + e.getMessage();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xB0101010);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int left = this.width / 2 - 155;
        int y = 12;
        graphics.text(this.font, this.title, left, y, 0xFFFFFF, false);
        y += 90;

        graphics.text(this.font, Component.literal("Current script in memory:"), left, y, 0xA0A0A0, false);
        y += 12;

        String script = engine.getScript();
        if (script == null || script.isBlank()) {
            graphics.text(this.font, Component.literal("(empty)"), left, y, 0x808080, false);
            y += 12;
        } else {
            String[] ls = script.split("\\R");
            int max = Math.min(ls.length, 10);
            for (int i = 0; i < max; i++) {
                graphics.text(this.font, Component.literal((i + 1) + ": " + ls[i]), left, y, 0xD0D0D0, false);
                y += 10;
            }
            if (ls.length > max) {
                graphics.text(this.font, Component.literal("...(" + (ls.length - max) + " more lines)"), left, y, 0x808080, false);
                y += 10;
            }
        }

        y = this.height - 20;
        int color = status.toLowerCase().contains("failed") ? 0xFF6060 : 0x80FF80;
        graphics.text(this.font, Component.literal(status), left, y, color, false);
    }

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        int keyCode = keyInput.key();
        if (waitingBind == BindTarget.TOGGLE) {
            MacroModClient.setToggleKeyCode(keyCode);
            waitingBind = BindTarget.NONE;
            refreshToggleLabels();
            status = "Start/Pause hotkey updated";
            return true;
        }
        if (waitingBind == BindTarget.STOP) {
            MacroModClient.setStopKeyCode(keyCode);
            waitingBind = BindTarget.NONE;
            refreshToggleLabels();
            status = "Stop hotkey updated";
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    private enum BindTarget {
        NONE,
        TOGGLE,
        STOP
    }
}

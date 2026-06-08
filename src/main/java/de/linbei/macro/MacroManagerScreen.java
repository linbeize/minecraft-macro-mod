package de.linbei.macro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class MacroManagerScreen extends Screen {
    private final MacroEngine engine;

    private final List<String> scripts = new ArrayList<>();
    private int scriptIndex = 0;
    private String status = "";

    private static final int VISIBLE_EDITOR_LINES = 10;

    private TextFieldWidget nameInput;
    private final List<TextFieldWidget> scriptLineInputs = new ArrayList<>();
    private final List<String> editorLines = new ArrayList<>();
    private int editorLineOffset = 0;
    private ButtonWidget repeatBtn;
    private ButtonWidget aimLockBtn;
    private ButtonWidget toggleBindBtn;
    private ButtonWidget stopBindBtn;
    private BindTarget waitingBind = BindTarget.NONE;

    public MacroManagerScreen(MacroEngine engine) {
        super(Text.literal("Macro Manager"));
        this.engine = engine;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 180;
        int editorWidth = 360;
        int y = 32;
        scriptLineInputs.clear();

        refreshScripts();

        nameInput = new TextFieldWidget(this.textRenderer, left, y, 165, 20, Text.literal("script name"));
        nameInput.setMaxLength(64);
        if (!scripts.isEmpty()) nameInput.setText(scripts.get(scriptIndex));
        this.addDrawableChild(nameInput);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), b -> refreshScripts())
                .dimensions(left + 170, y, 70, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Prev"), b -> stepScript(-1))
                .dimensions(left + 245, y, 55, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Next"), b -> stepScript(1))
                .dimensions(left + 305, y, 55, 20).build());

        y += 26;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Load"), b -> loadSelected())
                .dimensions(left, y, 55, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Apply"), b -> applyEditor())
                .dimensions(left + 60, y, 55, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> saveCurrent())
                .dimensions(left + 120, y, 55, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), b -> deleteSelected())
                .dimensions(left + 180, y, 60, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("New Template"), b -> newTemplate())
                .dimensions(left + 245, y, 115, 20).build());

        y += 30;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Start / Pause"), b -> toggleRun())
                .dimensions(left, y, 100, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Stop"), b -> {
                    engine.stop();
                    status = "Stopped";
                })
                .dimensions(left + 105, y, 60, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(left + 170, y, 70, 20).build());

        repeatBtn = this.addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                    engine.setRepeat(!engine.isRepeat());
                    refreshToggleLabels();
                    status = "Repeat: " + (engine.isRepeat() ? "ON" : "OFF");
                })
                .dimensions(left + 250, y, 110, 20).build());

        y += 26;
        aimLockBtn = this.addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                    engine.setAimLock(!engine.isAimLock());
                    refreshToggleLabels();
                    status = "Aim lock: " + (engine.isAimLock() ? "ON" : "OFF");
                })
                .dimensions(left, y, 140, 20).build());

        toggleBindBtn = this.addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                    waitingBind = BindTarget.TOGGLE;
                    refreshToggleLabels();
                    status = "Press a key for Start/Pause hotkey...";
                })
                .dimensions(left + 145, y, 170, 20).build());

        y += 26;
        stopBindBtn = this.addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                    waitingBind = BindTarget.STOP;
                    refreshToggleLabels();
                    status = "Press a key for Stop hotkey...";
                })
                .dimensions(left, y, 170, 20).build());

        y += 30;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Line Up"), b -> scrollEditor(-1))
                .dimensions(left, y, 70, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Line Down"), b -> scrollEditor(1))
                .dimensions(left + 75, y, 80, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Add Line"), b -> addEditorLine())
                .dimensions(left + 160, y, 75, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Del Blank"), b -> deleteTrailingBlankLine())
                .dimensions(left + 240, y, 80, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Clear"), b -> clearEditor())
                .dimensions(left + 325, y, 35, 20).build());

        y += 24;
        setEditorScript(engine.getScript());
        for (int i = 0; i < VISIBLE_EDITOR_LINES; i++) {
            TextFieldWidget line = new TextFieldWidget(this.textRenderer, left + 26, y + i * 18, editorWidth - 26, 16, Text.literal("script line"));
            line.setMaxLength(512);
            scriptLineInputs.add(line);
            this.addDrawableChild(line);
        }
        refreshEditorInputs();

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
            if (nameInput != null && !scripts.isEmpty()) nameInput.setText(scripts.get(scriptIndex));
            status = "Loaded list: " + scripts.size() + " script(s)";
        } catch (Exception e) {
            status = "Refresh failed: " + e.getMessage();
        }
    }

    private String currentName() {
        String n = nameInput == null ? "" : nameInput.getText().trim();
        if (n.toLowerCase().endsWith(".txt")) n = n.substring(0, n.length() - 4);
        return n.isEmpty() ? null : n;
    }

    private void stepScript(int delta) {
        if (scripts.isEmpty()) return;
        scriptIndex = (scriptIndex + delta + scripts.size()) % scripts.size();
        nameInput.setText(scripts.get(scriptIndex));
    }

    private void loadSelected() {
        String name = currentName();
        if (name == null) {
            status = "Name is empty";
            return;
        }
        try {
            String script = MacroStorage.load(name);
            setEditorScript(script);
            engine.setScript(script);
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
            String script = editorScript();
            engine.setScript(script);
            MacroStorage.save(name, script);
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
            setEditorScript(tpl);
            engine.setScript(tpl);
            MacroStorage.save(name, tpl);
            refreshScripts();
            status = "Template created: " + name;
        } catch (Exception e) {
            status = "Template failed: " + e.getMessage();
        }
    }

    private String editorScript() {
        syncEditorFromInputs();
        return String.join("\n", editorLines).stripTrailing() + "\n";
    }

    private void setEditorScript(String script) {
        editorLines.clear();
        String text = script == null ? "" : script;
        if (!text.isEmpty()) {
            editorLines.addAll(List.of(text.split("\\R", -1)));
            while (!editorLines.isEmpty() && editorLines.get(editorLines.size() - 1).isEmpty()) {
                editorLines.remove(editorLines.size() - 1);
            }
        }
        if (editorLines.isEmpty()) {
            editorLines.add("");
        }
        editorLineOffset = Math.min(editorLineOffset, Math.max(0, editorLines.size() - VISIBLE_EDITOR_LINES));
        refreshEditorInputs();
    }

    private void syncEditorFromInputs() {
        for (int i = 0; i < scriptLineInputs.size(); i++) {
            int idx = editorLineOffset + i;
            if (idx < editorLines.size()) {
                editorLines.set(idx, scriptLineInputs.get(i).getText());
            }
        }
    }

    private void refreshEditorInputs() {
        for (int i = 0; i < scriptLineInputs.size(); i++) {
            int idx = editorLineOffset + i;
            scriptLineInputs.get(i).setText(idx < editorLines.size() ? editorLines.get(idx) : "");
        }
    }

    private void scrollEditor(int delta) {
        syncEditorFromInputs();
        int maxOffset = Math.max(0, editorLines.size() - VISIBLE_EDITOR_LINES);
        editorLineOffset = Math.max(0, Math.min(maxOffset, editorLineOffset + delta));
        refreshEditorInputs();
    }

    private void addEditorLine() {
        syncEditorFromInputs();
        editorLines.add("");
        editorLineOffset = Math.max(0, editorLines.size() - VISIBLE_EDITOR_LINES);
        refreshEditorInputs();
        status = "Added editor line";
    }

    private void deleteTrailingBlankLine() {
        syncEditorFromInputs();
        if (editorLines.size() > 1 && editorLines.get(editorLines.size() - 1).isBlank()) {
            editorLines.remove(editorLines.size() - 1);
            editorLineOffset = Math.min(editorLineOffset, Math.max(0, editorLines.size() - VISIBLE_EDITOR_LINES));
            refreshEditorInputs();
            status = "Deleted trailing blank line";
        } else {
            status = "Last line is not blank";
        }
    }

    private void clearEditor() {
        editorLines.clear();
        editorLines.add("");
        editorLineOffset = 0;
        refreshEditorInputs();
        status = "Editor cleared";
    }

    private void applyEditor() {
        try {
            engine.setScript(editorScript());
            status = "Applied editor script";
        } catch (Exception e) {
            status = "Parse failed: " + e.getMessage();
        }
    }

    private void refreshToggleLabels() {
        if (repeatBtn != null) {
            repeatBtn.setMessage(Text.literal("Repeat: " + (engine.isRepeat() ? "ON" : "OFF")));
        }
        if (aimLockBtn != null) {
            aimLockBtn.setMessage(Text.literal("AimLock: " + (engine.isAimLock() ? "ON" : "OFF")));
        }
        if (toggleBindBtn != null) {
            String t = waitingBind == BindTarget.TOGGLE ? "Start/Pause: [Press key...]" : "Start/Pause: " + MacroModClient.getToggleKeyName();
            toggleBindBtn.setMessage(Text.literal(t));
        }
        if (stopBindBtn != null) {
            String t = waitingBind == BindTarget.STOP ? "Stop: [Press key...]" : "Stop: " + MacroModClient.getStopKeyName();
            stopBindBtn.setMessage(Text.literal(t));
        }
    }

    private void toggleRun() {
        try {
            if (!engine.isRunning()) {
                engine.setScript(editorScript());
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xB0101010);
        super.render(context, mouseX, mouseY, delta);

        int left = this.width / 2 - 180;
        int y = 12;
        context.drawText(this.textRenderer, this.title, left, y, 0xFFFFFF, false);
        context.drawText(this.textRenderer, Text.literal("Script editor:"), left, 124, 0xA0A0A0, false);

        int lineY = 164;
        int lastLine = Math.min(editorLines.size(), editorLineOffset + VISIBLE_EDITOR_LINES);
        for (int i = 0; i < VISIBLE_EDITOR_LINES; i++) {
            int lineNo = editorLineOffset + i + 1;
            context.drawText(this.textRenderer, Text.literal(String.format("%2d:", lineNo)), left, lineY + i * 18 + 4, 0x808080, false);
        }
        context.drawText(this.textRenderer, Text.literal("Lines " + (editorLineOffset + 1) + "-" + Math.max(editorLineOffset + 1, lastLine) + " / " + editorLines.size()), left + 210, 124, 0x808080, false);

        y = this.height - 20;
        int color = status.toLowerCase().contains("failed") ? 0xFF6060 : 0x80FF80;
        context.drawText(this.textRenderer, Text.literal(status), left, y, color, false);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
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
    public void close() {
        MinecraftClient.getInstance().setScreen(null);
    }

    private enum BindTarget {
        NONE,
        TOGGLE,
        STOP
    }
}

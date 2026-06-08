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
    private static final int SCRIPT_ROWS = 8;
    private static final int MAX_SCRIPT_CHARS = 200000;

    private final MacroEngine engine;
    private final List<String> scripts = new ArrayList<>();
    private final List<EditBox> editorInputs = new ArrayList<>();

    private int scriptIndex = 0;
    private int scriptPage = 0;
    private int visibleEditorLines = 0;
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
        int margin = 10;
        int left = margin;
        int leftWidth = Math.min(430, Math.max(360, this.width * 34 / 100));
        int editorLeft = left + leftWidth + 12;
        int editorWidth = Math.max(260, this.width - editorLeft - margin);
        int top = 10;
        int rowHeight = 20;
        int rowGap = 5;

        editorInputs.clear();
        refreshScripts();

        nameInput = new EditBox(this.font, left, top, Math.min(170, leftWidth - 230), rowHeight, Component.literal("script name"));
        nameInput.setMaxLength(64);
        if (!scripts.isEmpty()) nameInput.setValue(scripts.get(scriptIndex));
        this.addRenderableWidget(nameInput);

        int x = left + nameInput.getWidth() + 8;
        this.addRenderableWidget(Button.builder(Component.literal("新建"), b -> newTemplate())
                .bounds(x, top, 48, rowHeight).build());
        this.addRenderableWidget(Button.builder(Component.literal("刷新"), b -> {
                    refreshScripts();
                    this.rebuildWidgets();
                })
                .bounds(x + 54, top, 48, rowHeight).build());
        this.addRenderableWidget(Button.builder(Component.literal("上一页"), b -> stepScriptPage(-1))
                .bounds(x + 108, top, 68, rowHeight).build());
        this.addRenderableWidget(Button.builder(Component.literal("下一页"), b -> stepScriptPage(1))
                .bounds(x + 108, top + rowHeight + 4, 68, rowHeight).build());

        int listTop = top + rowHeight + 24;
        for (int i = 0; i < SCRIPT_ROWS; i++) {
            int y = listTop + i * (rowHeight + rowGap);
            int row = i;
            int nameWidth = Math.max(150, leftWidth - 226);
            this.addRenderableWidget(Button.builder(Component.literal(scriptNameForRow(row)), b -> selectRow(row))
                    .bounds(left, y, nameWidth, rowHeight).build());
            int bx = left + nameWidth + 8;
            this.addRenderableWidget(Button.builder(Component.literal("选"), b -> selectRow(row))
                    .bounds(bx, y, 36, rowHeight).build());
            this.addRenderableWidget(Button.builder(Component.literal("编"), b -> editRow(row))
                    .bounds(bx + 42, y, 36, rowHeight).build());
            this.addRenderableWidget(Button.builder(Component.literal("复"), b -> copyRow(row))
                    .bounds(bx + 84, y, 36, rowHeight).build());
            this.addRenderableWidget(Button.builder(Component.literal("删"), b -> deleteRow(row))
                    .bounds(bx + 126, y, 36, rowHeight).build());
        }

        int controlsTop = listTop + SCRIPT_ROWS * (rowHeight + rowGap) + 8;
        this.addRenderableWidget(Button.builder(Component.literal("载入编辑器"), b -> loadSelected())
                .bounds(left, controlsTop, 82, rowHeight).build());
        this.addRenderableWidget(Button.builder(Component.literal("保存编辑器"), b -> saveCurrent())
                .bounds(left + 88, controlsTop, 82, rowHeight).build());
        this.addRenderableWidget(Button.builder(Component.literal("Start/Pause"), b -> toggleRun())
                .bounds(left + 176, controlsTop, 88, rowHeight).build());
        this.addRenderableWidget(Button.builder(Component.literal("Stop"), b -> {
                    engine.stop();
                    status = "Stopped";
                })
                .bounds(left + 270, controlsTop, 54, rowHeight).build());

        controlsTop += rowHeight + 10;
        repeatBtn = this.addRenderableWidget(Button.builder(Component.literal(""), b -> {
                    engine.setRepeat(!engine.isRepeat());
                    refreshToggleLabels();
                    status = "Repeat: " + (engine.isRepeat() ? "ON" : "OFF");
                })
                .bounds(left, controlsTop, 82, rowHeight).build());
        aimLockBtn = this.addRenderableWidget(Button.builder(Component.literal(""), b -> {
                    engine.setAimLock(!engine.isAimLock());
                    refreshToggleLabels();
                    status = "Aim lock: " + (engine.isAimLock() ? "ON" : "OFF");
                })
                .bounds(left + 88, controlsTop, 92, rowHeight).build());
        toggleBindBtn = this.addRenderableWidget(Button.builder(Component.literal(""), b -> {
                    waitingBind = BindTarget.TOGGLE;
                    refreshToggleLabels();
                    status = "Press a key for Start/Pause hotkey...";
                })
                .bounds(left + 186, controlsTop, 134, rowHeight).build());

        controlsTop += rowHeight + 10;
        stopBindBtn = this.addRenderableWidget(Button.builder(Component.literal(""), b -> {
                    waitingBind = BindTarget.STOP;
                    refreshToggleLabels();
                    status = "Press a key for Stop hotkey...";
                })
                .bounds(left, controlsTop, 134, rowHeight).build());
        this.addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
                .bounds(left + 140, controlsTop, 54, rowHeight).build());

        int editorTop = top;
        int editorBottom = this.height - 52;
        visibleEditorLines = Math.max(1, (editorBottom - editorTop - 12) / 14);
        for (int i = 0; i < visibleEditorLines; i++) {
            EditBox line = new EditBox(this.font, editorLeft + 6, editorTop + 6 + i * 14, editorWidth - 12, 12, Component.literal("script line"));
            line.setMaxLength(512);
            line.setBordered(true);
            line.setTextColor(0xFFFFFF);
            line.setTextColorUneditable(0xFFFFFF);
            line.setTextShadow(true);
            line.setCanLoseFocus(true);
            editorInputs.add(line);
            this.addRenderableWidget(line);
        }
        setEditorScript(engine.getScript());
        refreshToggleLabels();
    }

    private String scriptNameForRow(int row) {
        int idx = scriptPage * SCRIPT_ROWS + row;
        return idx >= 0 && idx < scripts.size() ? scripts.get(idx) : "";
    }

    private int scriptIndexForRow(int row) {
        int idx = scriptPage * SCRIPT_ROWS + row;
        return idx >= 0 && idx < scripts.size() ? idx : -1;
    }

    private void selectRow(int row) {
        int idx = scriptIndexForRow(row);
        if (idx < 0) return;
        scriptIndex = idx;
        nameInput.setValue(scripts.get(scriptIndex));
        status = "Selected: " + scripts.get(scriptIndex);
    }

    private void editRow(int row) {
        selectRow(row);
        loadSelected();
    }

    private void copyRow(int row) {
        int idx = scriptIndexForRow(row);
        if (idx < 0) return;
        String source = scripts.get(idx);
        try {
            String target = nextCopyName(source);
            MacroStorage.save(target, MacroStorage.load(source));
            refreshScripts();
            nameInput.setValue(target);
            status = "Copied: " + target;
        } catch (Exception e) {
            status = "Copy failed: " + e.getMessage();
        }
    }

    private void deleteRow(int row) {
        int idx = scriptIndexForRow(row);
        if (idx < 0) return;
        nameInput.setValue(scripts.get(idx));
        deleteSelected();
    }

    private String nextCopyName(String source) throws Exception {
        for (int i = 1; i < 1000; i++) {
            String name = source + "_copy" + i;
            if (!MacroStorage.listScriptNames().contains(name)) return name;
        }
        return source + "_copy";
    }

    private void stepScriptPage(int delta) {
        int pages = Math.max(1, (scripts.size() + SCRIPT_ROWS - 1) / SCRIPT_ROWS);
        scriptPage = (scriptPage + delta + pages) % pages;
        this.rebuildWidgets();
    }

    private void refreshScripts() {
        try {
            String current = currentName();
            scripts.clear();
            scripts.addAll(MacroStorage.listScriptNames());
            if (scripts.isEmpty()) {
                scriptIndex = 0;
                scriptPage = 0;
            } else if (current != null) {
                int idx = scripts.indexOf(current);
                scriptIndex = idx >= 0 ? idx : Math.min(scriptIndex, scripts.size() - 1);
            } else {
                scriptIndex = Math.min(scriptIndex, scripts.size() - 1);
            }
            scriptPage = Math.min(scriptPage, Math.max(0, (scripts.size() + SCRIPT_ROWS - 1) / SCRIPT_ROWS - 1));
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
            this.rebuildWidgets();
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
            this.rebuildWidgets();
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
        String tpl = "LeftDown 1,\nFor 9,\nKeyDown \"W\", 1,\nKeyDown \"D\", 1,\nDelay 41000,\nKeyUp \"W\", 1,\nKeyUp \"D\", 1,\nDelay 500,\nNext,\nLeftUp 1,\n";
        try {
            setEditorScript(tpl);
            engine.setScript(tpl);
            MacroStorage.save(name, tpl);
            refreshScripts();
            status = "Template created: " + name;
            this.rebuildWidgets();
        } catch (Exception e) {
            status = "Template failed: " + e.getMessage();
        }
    }

    private String editorScript() {
        syncEditorFromInputs();
        return String.join("\n", visibleEditorLines()) .stripTrailing() + "\n";
    }

    private List<String> visibleEditorLines() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < visibleEditorLines; i++) {
            out.add(i < editorInputs.size() ? editorInputs.get(i).getValue() : "");
        }
        return out;
    }

    private void setEditorScript(String script) {
        List<String> lines = splitScript(script);
        for (int i = 0; i < editorInputs.size(); i++) {
            editorInputs.get(i).setValue(i < lines.size() ? lines.get(i) : "");
        }
    }

    private List<String> splitScript(String script) {
        String text = script == null ? "" : script;
        List<String> lines = new ArrayList<>();
        if (!text.isEmpty()) {
            lines.addAll(List.of(text.split("\\R", -1)));
            while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
        }
        while (lines.size() < visibleEditorLines) lines.add("");
        return lines;
    }

    private void syncEditorFromInputs() {
        // Editor state lives directly in the visible input widgets.
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
        if (repeatBtn != null) repeatBtn.setMessage(Component.literal("Repeat: " + (engine.isRepeat() ? "ON" : "OFF")));
        if (aimLockBtn != null) aimLockBtn.setMessage(Component.literal("AimLock: " + (engine.isAimLock() ? "ON" : "OFF")));
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
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xB0101010);
        int margin = 10;
        int leftWidth = Math.min(430, Math.max(360, this.width * 34 / 100));
        int editorLeft = margin + leftWidth + 12;
        int editorTop = 10;
        int editorRight = this.width - margin;
        int editorBottom = this.height - 52;
        graphics.fill(editorLeft - 2, editorTop - 2, editorRight + 2, editorBottom + 2, 0xFFAAAAAA);
        graphics.fill(editorLeft, editorTop, editorRight, editorBottom, 0xFF050505);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int margin = 10;
        int leftWidth = Math.min(430, Math.max(360, this.width * 34 / 100));
        int editorLeft = margin + leftWidth + 12;
        int editorTop = 10;
        int editorRight = this.width - margin;
        int editorBottom = this.height - 52;

        graphics.fill(editorLeft - 2, editorTop - 2, editorRight + 2, editorBottom + 2, 0xFFAAAAAA);
        graphics.fill(editorLeft, editorTop, editorRight, editorBottom, 0xFF050505);
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int count = 0;
        for (EditBox input : editorInputs) {
            count += input.getValue().length() + 1;
        }
        graphics.text(this.font, Component.literal(count + "/" + MAX_SCRIPT_CHARS), editorRight - 92, editorBottom + 8, 0xD0D0D0, false);

        int color = status.toLowerCase().contains("failed") ? 0xFF6060 : 0x80FF80;
        graphics.text(this.font, Component.literal(status), margin, this.height - 20, color, false);
        graphics.text(this.font, Component.literal("Page " + (scriptPage + 1) + "/" + Math.max(1, (scripts.size() + SCRIPT_ROWS - 1) / SCRIPT_ROWS)), margin + leftWidth - 90, this.height - 20, 0xD0D0D0, false);
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

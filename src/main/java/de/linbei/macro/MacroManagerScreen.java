package de.linbei.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MacroManagerScreen extends Screen {
    private static final int SCRIPT_ROWS = 8;
    private static final int MAX_SCRIPT_CHARS = 200000;

    private final MacroEngine engine;
    private final List<String> scripts = new ArrayList<>();
    private final List<String> editorLines = new ArrayList<>();

    private int scriptIndex = 0;
    private int scriptPage = 0;
    private int scrollLine = 0;
    private int cursorLine = 0;
    private int cursorCol = 0;
    private int visibleEditorLines = 1;
    private String selectedName = "example";
    private String status = "";

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
        int top = 10;
        int rowHeight = 20;
        int rowGap = 5;

        refreshScripts();

        this.addRenderableWidget(Button.builder(Component.literal(selectedName), b -> loadSelected())
                .bounds(left, top, Math.min(170, leftWidth - 230), rowHeight).build());

        int x = left + Math.min(170, leftWidth - 230) + 8;
        this.addRenderableWidget(Button.builder(Component.literal("新建"), b -> newTemplate()).bounds(x, top, 48, rowHeight).build());
        this.addRenderableWidget(Button.builder(Component.literal("刷新"), b -> { refreshScripts(); this.rebuildWidgets(); }).bounds(x + 54, top, 48, rowHeight).build());
        this.addRenderableWidget(Button.builder(Component.literal("上一页"), b -> stepScriptPage(-1)).bounds(x + 108, top, 68, rowHeight).build());
        this.addRenderableWidget(Button.builder(Component.literal("下一页"), b -> stepScriptPage(1)).bounds(x + 108, top + rowHeight + 4, 68, rowHeight).build());

        int listTop = top + rowHeight + 24;
        for (int i = 0; i < SCRIPT_ROWS; i++) {
            int y = listTop + i * (rowHeight + rowGap);
            int row = i;
            int nameWidth = Math.max(150, leftWidth - 226);
            this.addRenderableWidget(Button.builder(Component.literal(scriptNameForRow(row)), b -> selectRow(row)).bounds(left, y, nameWidth, rowHeight).build());
            int bx = left + nameWidth + 8;
            this.addRenderableWidget(Button.builder(Component.literal("选"), b -> selectRow(row)).bounds(bx, y, 36, rowHeight).build());
            this.addRenderableWidget(Button.builder(Component.literal("编"), b -> editRow(row)).bounds(bx + 42, y, 36, rowHeight).build());
            this.addRenderableWidget(Button.builder(Component.literal("复"), b -> copyRow(row)).bounds(bx + 84, y, 36, rowHeight).build());
            this.addRenderableWidget(Button.builder(Component.literal("删"), b -> deleteRow(row)).bounds(bx + 126, y, 36, rowHeight).build());
        }

        int controlsTop = listTop + SCRIPT_ROWS * (rowHeight + rowGap) + 8;
        this.addRenderableWidget(Button.builder(Component.literal("载入编辑器"), b -> loadSelected()).bounds(left, controlsTop, 82, rowHeight).build());
        this.addRenderableWidget(Button.builder(Component.literal("保存编辑器"), b -> saveCurrent()).bounds(left + 88, controlsTop, 82, rowHeight).build());
        this.addRenderableWidget(Button.builder(Component.literal("Start/Pause"), b -> toggleRun()).bounds(left + 176, controlsTop, 88, rowHeight).build());
        this.addRenderableWidget(Button.builder(Component.literal("Stop"), b -> { engine.stop(); status = "Stopped"; }).bounds(left + 270, controlsTop, 54, rowHeight).build());

        controlsTop += rowHeight + 10;
        repeatBtn = this.addRenderableWidget(Button.builder(Component.literal(""), b -> {
            engine.setRepeat(!engine.isRepeat()); refreshToggleLabels(); status = "Repeat: " + (engine.isRepeat() ? "ON" : "OFF");
        }).bounds(left, controlsTop, 82, rowHeight).build());
        aimLockBtn = this.addRenderableWidget(Button.builder(Component.literal(""), b -> {
            engine.setAimLock(!engine.isAimLock()); refreshToggleLabels(); status = "Aim lock: " + (engine.isAimLock() ? "ON" : "OFF");
        }).bounds(left + 88, controlsTop, 92, rowHeight).build());
        toggleBindBtn = this.addRenderableWidget(Button.builder(Component.literal(""), b -> {
            waitingBind = BindTarget.TOGGLE; refreshToggleLabels(); status = "Press a key for Start/Pause hotkey...";
        }).bounds(left + 186, controlsTop, 134, rowHeight).build());

        controlsTop += rowHeight + 10;
        stopBindBtn = this.addRenderableWidget(Button.builder(Component.literal(""), b -> {
            waitingBind = BindTarget.STOP; refreshToggleLabels(); status = "Press a key for Stop hotkey...";
        }).bounds(left, controlsTop, 134, rowHeight).build());
        this.addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose()).bounds(left + 140, controlsTop, 54, rowHeight).build());

        int editorTop = top;
        int editorBottom = this.height - 52;
        visibleEditorLines = Math.max(1, (editorBottom - editorTop - 14) / 14);
        if (editorLines.isEmpty()) setEditorScript(engine.getScript());
        clampCursor();
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
        selectedName = scripts.get(scriptIndex);
        status = "Selected: " + selectedName;
        this.rebuildWidgets();
    }

    private void editRow(int row) {
        int idx = scriptIndexForRow(row);
        if (idx < 0) return;
        scriptIndex = idx;
        selectedName = scripts.get(scriptIndex);
        loadSelected();
        this.rebuildWidgets();
    }

    private void copyRow(int row) {
        int idx = scriptIndexForRow(row);
        if (idx < 0) return;
        String source = scripts.get(idx);
        try {
            String target = nextCopyName(source);
            MacroStorage.save(target, MacroStorage.load(source));
            selectedName = target;
            refreshScripts();
            status = "Copied: " + target;
            this.rebuildWidgets();
        } catch (Exception e) { status = "Copy failed: " + e.getMessage(); }
    }

    private void deleteRow(int row) {
        int idx = scriptIndexForRow(row);
        if (idx < 0) return;
        selectedName = scripts.get(idx);
        deleteSelected();
    }

    private String nextCopyName(String source) throws Exception {
        List<String> names = MacroStorage.listScriptNames();
        for (int i = 1; i < 1000; i++) {
            String name = source + "_copy" + i;
            if (!names.contains(name)) return name;
        }
        return source + "_copy";
    }

    private String nextNewName() throws Exception {
        List<String> names = MacroStorage.listScriptNames();
        for (int i = 1; i < 1000; i++) {
            String name = "macro_" + i;
            if (!names.contains(name)) return name;
        }
        return "macro_new";
    }

    private void stepScriptPage(int delta) {
        int pages = Math.max(1, (scripts.size() + SCRIPT_ROWS - 1) / SCRIPT_ROWS);
        scriptPage = (scriptPage + delta + pages) % pages;
        this.rebuildWidgets();
    }

    private void refreshScripts() {
        try {
            scripts.clear();
            scripts.addAll(MacroStorage.listScriptNames());
            if (scripts.isEmpty()) {
                scriptIndex = 0;
                scriptPage = 0;
            } else {
                int idx = scripts.indexOf(selectedName);
                scriptIndex = idx >= 0 ? idx : Math.min(scriptIndex, scripts.size() - 1);
                selectedName = scripts.get(scriptIndex);
            }
            scriptPage = Math.min(scriptPage, Math.max(0, (scripts.size() + SCRIPT_ROWS - 1) / SCRIPT_ROWS - 1));
            status = "Loaded list: " + scripts.size() + " script(s)";
        } catch (Exception e) { status = "Refresh failed: " + e.getMessage(); }
    }

    private void loadSelected() {
        if (selectedName == null || selectedName.isBlank()) { status = "Name is empty"; return; }
        try {
            String script = MacroStorage.load(selectedName);
            setEditorScript(script);
            engine.setScript(script);
            status = "Loaded: " + selectedName;
        } catch (Exception e) { status = "Load failed: " + e.getMessage(); }
    }

    private void saveCurrent() {
        if (selectedName == null || selectedName.isBlank()) { status = "Name is empty"; return; }
        try {
            String script = editorScript();
            engine.setScript(script);
            MacroStorage.save(selectedName, script);
            refreshScripts();
            status = "Saved: " + selectedName;
            this.rebuildWidgets();
        } catch (Exception e) { status = "Save failed: " + e.getMessage(); }
    }

    private void deleteSelected() {
        if (selectedName == null || selectedName.isBlank()) { status = "Name is empty"; return; }
        try {
            MacroStorage.delete(selectedName);
            refreshScripts();
            status = "Deleted: " + selectedName;
            this.rebuildWidgets();
        } catch (Exception e) { status = "Delete failed: " + e.getMessage(); }
    }

    private void newTemplate() {
        String tpl = "LeftDown 1,\nFor 9,\nKeyDown \"W\", 1,\nKeyDown \"D\", 1,\nDelay 41000,\nKeyUp \"W\", 1,\nKeyUp \"D\", 1,\nDelay 500,\nNext,\nLeftUp 1,\n";
        try {
            selectedName = nextNewName();
            setEditorScript(tpl);
            engine.setScript(tpl);
            MacroStorage.save(selectedName, tpl);
            refreshScripts();
            status = "Template created: " + selectedName;
            this.rebuildWidgets();
        } catch (Exception e) { status = "Template failed: " + e.getMessage(); }
    }

    private String editorScript() { return String.join("\n", editorLines).stripTrailing() + "\n"; }

    private void setEditorScript(String script) {
        editorLines.clear();
        String text = script == null ? "" : script;
        if (!text.isEmpty()) {
            editorLines.addAll(List.of(text.split("\\R", -1)));
            while (!editorLines.isEmpty() && editorLines.get(editorLines.size() - 1).isEmpty()) editorLines.remove(editorLines.size() - 1);
        }
        if (editorLines.isEmpty()) editorLines.add("");
        cursorLine = 0; cursorCol = 0; scrollLine = 0; clampCursor();
    }

    private void refreshToggleLabels() {
        if (repeatBtn != null) repeatBtn.setMessage(Component.literal("Repeat: " + (engine.isRepeat() ? "ON" : "OFF")));
        if (aimLockBtn != null) aimLockBtn.setMessage(Component.literal("AimLock: " + (engine.isAimLock() ? "ON" : "OFF")));
        if (toggleBindBtn != null) toggleBindBtn.setMessage(Component.literal(waitingBind == BindTarget.TOGGLE ? "Start/Pause: [Press key...]" : "Start/Pause: " + MacroModClient.getToggleKeyName()));
        if (stopBindBtn != null) stopBindBtn.setMessage(Component.literal(waitingBind == BindTarget.STOP ? "Stop: [Press key...]" : "Stop: " + MacroModClient.getStopKeyName()));
    }

    private void toggleRun() {
        try {
            if (!engine.isRunning()) { engine.setScript(editorScript()); engine.start(); status = "Started"; }
            else { engine.pauseToggle(); status = engine.isPaused() ? "Paused" : "Resumed"; }
        } catch (Exception e) { status = "Run failed: " + e.getMessage(); }
    }

    private void insertText(String text) {
        if (text == null || text.isEmpty()) return;
        if (editorScript().length() + text.length() > MAX_SCRIPT_CHARS) return;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r') continue;
            if (c == '\n') insertNewline(); else insertChar(String.valueOf(c));
        }
    }

    private void insertChar(String value) {
        String line = editorLines.get(cursorLine);
        editorLines.set(cursorLine, line.substring(0, cursorCol) + value + line.substring(cursorCol));
        cursorCol += value.length(); ensureCursorVisible();
    }

    private void insertNewline() {
        String line = editorLines.get(cursorLine);
        editorLines.set(cursorLine, line.substring(0, cursorCol));
        editorLines.add(cursorLine + 1, line.substring(cursorCol));
        cursorLine++; cursorCol = 0; ensureCursorVisible();
    }

    private void backspace() {
        if (cursorCol > 0) {
            String line = editorLines.get(cursorLine);
            editorLines.set(cursorLine, line.substring(0, cursorCol - 1) + line.substring(cursorCol));
            cursorCol--;
        } else if (cursorLine > 0) {
            int oldLen = editorLines.get(cursorLine - 1).length();
            editorLines.set(cursorLine - 1, editorLines.get(cursorLine - 1) + editorLines.get(cursorLine));
            editorLines.remove(cursorLine); cursorLine--; cursorCol = oldLen;
        }
        ensureCursorVisible();
    }

    private void deleteForward() {
        String line = editorLines.get(cursorLine);
        if (cursorCol < line.length()) editorLines.set(cursorLine, line.substring(0, cursorCol) + line.substring(cursorCol + 1));
        else if (cursorLine < editorLines.size() - 1) { editorLines.set(cursorLine, line + editorLines.get(cursorLine + 1)); editorLines.remove(cursorLine + 1); }
        ensureCursorVisible();
    }

    private void moveCursor(int lineDelta, int colDelta) {
        cursorLine = Math.max(0, Math.min(editorLines.size() - 1, cursorLine + lineDelta));
        cursorCol = Math.max(0, Math.min(editorLines.get(cursorLine).length(), cursorCol + colDelta));
        ensureCursorVisible();
    }

    private void clampCursor() {
        if (editorLines.isEmpty()) editorLines.add("");
        cursorLine = Math.max(0, Math.min(editorLines.size() - 1, cursorLine));
        cursorCol = Math.max(0, Math.min(editorLines.get(cursorLine).length(), cursorCol));
        ensureCursorVisible();
    }

    private void ensureCursorVisible() {
        if (cursorLine < scrollLine) scrollLine = cursorLine;
        if (cursorLine >= scrollLine + visibleEditorLines) scrollLine = cursorLine - visibleEditorLines + 1;
        scrollLine = Math.max(0, Math.min(scrollLine, Math.max(0, editorLines.size() - visibleEditorLines)));
    }

    private String keyToText(int key, int modifiers) {
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            char c = (char) ('a' + key - GLFW.GLFW_KEY_A);
            return shift ? String.valueOf(c).toUpperCase(Locale.ROOT) : String.valueOf(c);
        }
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            String normal = "0123456789";
            String shifted = ")!@#$%^&*(";
            return String.valueOf((shift ? shifted : normal).charAt(key - GLFW.GLFW_KEY_0));
        }
        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> " ";
            case GLFW.GLFW_KEY_COMMA -> shift ? "<" : ",";
            case GLFW.GLFW_KEY_PERIOD -> shift ? ">" : ".";
            case GLFW.GLFW_KEY_SLASH -> shift ? "?" : "/";
            case GLFW.GLFW_KEY_SEMICOLON -> shift ? ":" : ";";
            case GLFW.GLFW_KEY_APOSTROPHE -> shift ? "\"" : "'";
            case GLFW.GLFW_KEY_LEFT_BRACKET -> shift ? "{" : "[";
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> shift ? "}" : "]";
            case GLFW.GLFW_KEY_BACKSLASH -> shift ? "|" : "\\";
            case GLFW.GLFW_KEY_MINUS -> shift ? "_" : "-";
            case GLFW.GLFW_KEY_EQUAL -> shift ? "+" : "=";
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> shift ? "~" : "`";
            default -> null;
        };
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

        for (int i = 0; i < visibleEditorLines; i++) {
            int lineIndex = scrollLine + i;
            if (lineIndex >= editorLines.size()) break;
            String line = editorLines.get(lineIndex);
            int y = editorTop + 7 + i * 14;
            graphics.text(this.font, Component.literal(line), editorLeft + 7, y, 0xFFFFFF, true);
            if (lineIndex == cursorLine && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cursorX = editorLeft + 7 + this.font.width(line.substring(0, Math.min(cursorCol, line.length())));
                graphics.fill(cursorX, y - 1, cursorX + 1, y + 10, 0xFFFFFFFF);
            }
        }

        graphics.text(this.font, Component.literal(editorScript().length() + "/" + MAX_SCRIPT_CHARS), editorRight - 92, editorBottom + 8, 0xD0D0D0, false);
        int color = status.toLowerCase().contains("failed") ? 0xFF6060 : 0x80FF80;
        graphics.text(this.font, Component.literal(status), margin, this.height - 20, color, false);
        graphics.text(this.font, Component.literal("Page " + (scriptPage + 1) + "/" + Math.max(1, (scripts.size() + SCRIPT_ROWS - 1) / SCRIPT_ROWS)), margin + leftWidth - 90, this.height - 20, 0xD0D0D0, false);
    }

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        int keyCode = keyInput.key();
        if (waitingBind == BindTarget.TOGGLE) { MacroModClient.setToggleKeyCode(keyCode); waitingBind = BindTarget.NONE; refreshToggleLabels(); status = "Start/Pause hotkey updated"; return true; }
        if (waitingBind == BindTarget.STOP) { MacroModClient.setStopKeyCode(keyCode); waitingBind = BindTarget.NONE; refreshToggleLabels(); status = "Stop hotkey updated"; return true; }
        boolean ctrl = (keyInput.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        switch (keyCode) {
            case GLFW.GLFW_KEY_V -> { if (ctrl) { insertText(Minecraft.getInstance().keyboardHandler.getClipboard()); return true; } }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { insertNewline(); return true; }
            case GLFW.GLFW_KEY_BACKSPACE -> { backspace(); return true; }
            case GLFW.GLFW_KEY_DELETE -> { deleteForward(); return true; }
            case GLFW.GLFW_KEY_LEFT -> { moveCursor(0, -1); return true; }
            case GLFW.GLFW_KEY_RIGHT -> { moveCursor(0, 1); return true; }
            case GLFW.GLFW_KEY_UP -> { moveCursor(-1, 0); return true; }
            case GLFW.GLFW_KEY_DOWN -> { moveCursor(1, 0); return true; }
            case GLFW.GLFW_KEY_HOME -> { cursorCol = 0; ensureCursorVisible(); return true; }
            case GLFW.GLFW_KEY_END -> { cursorCol = editorLines.get(cursorLine).length(); ensureCursorVisible(); return true; }
            case GLFW.GLFW_KEY_PAGE_UP -> { scrollLine = Math.max(0, scrollLine - visibleEditorLines); cursorLine = scrollLine; clampCursor(); return true; }
            case GLFW.GLFW_KEY_PAGE_DOWN -> { scrollLine = Math.min(Math.max(0, editorLines.size() - visibleEditorLines), scrollLine + visibleEditorLines); cursorLine = scrollLine; clampCursor(); return true; }
        }
        String text = keyToText(keyCode, keyInput.modifiers());
        if (text != null && !ctrl) { insertText(text); return true; }
        return super.keyPressed(keyInput);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(null); }

    private enum BindTarget { NONE, TOGGLE, STOP }
}

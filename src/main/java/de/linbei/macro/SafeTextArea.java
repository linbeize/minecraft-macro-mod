package de.linbei.macro;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Text field that never enables IME text input.
 *
 * <p>Vanilla {@code EditBox} / {@code MultiLineEditBox} call {@code Minecraft.onTextInputFocusChange}
 * when focused, which turns on OS text input / IME. That crashes some clients (seen on Lunar), so
 * this widget is the default. Characters are derived from key presses (US layout) instead of
 * character events, because without text input SDL (26.3+) delivers no character events at all.
 * Non-ASCII text can still be pasted with Ctrl+V.
 */
public class SafeTextArea extends AbstractWidget {
    private static final int PAD = 4;
    private static final Map<Integer, String[]> KEY_CHARS = new HashMap<>();

    static {
        String letters = "abcdefghijklmnopqrstuvwxyz";
        int[] letterKeys = {
                InputConstants.KEY_A, InputConstants.KEY_B, InputConstants.KEY_C, InputConstants.KEY_D, InputConstants.KEY_E,
                InputConstants.KEY_F, InputConstants.KEY_G, InputConstants.KEY_H, InputConstants.KEY_I, InputConstants.KEY_J,
                InputConstants.KEY_K, InputConstants.KEY_L, InputConstants.KEY_M, InputConstants.KEY_N, InputConstants.KEY_O,
                InputConstants.KEY_P, InputConstants.KEY_Q, InputConstants.KEY_R, InputConstants.KEY_S, InputConstants.KEY_T,
                InputConstants.KEY_U, InputConstants.KEY_V, InputConstants.KEY_W, InputConstants.KEY_X, InputConstants.KEY_Y,
                InputConstants.KEY_Z};
        for (int i = 0; i < letterKeys.length; i++) {
            String c = String.valueOf(letters.charAt(i));
            KEY_CHARS.put(letterKeys[i], new String[]{c, c.toUpperCase(java.util.Locale.ROOT)});
        }
        int[] digitKeys = {
                InputConstants.KEY_0, InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3, InputConstants.KEY_4,
                InputConstants.KEY_5, InputConstants.KEY_6, InputConstants.KEY_7, InputConstants.KEY_8, InputConstants.KEY_9};
        String shiftedDigits = ")!@#$%^&*(";
        for (int i = 0; i < digitKeys.length; i++) {
            KEY_CHARS.put(digitKeys[i], new String[]{String.valueOf(i), String.valueOf(shiftedDigits.charAt(i))});
        }
        int[] numpad = {
                InputConstants.KEY_NUMPAD0, InputConstants.KEY_NUMPAD1, InputConstants.KEY_NUMPAD2, InputConstants.KEY_NUMPAD3,
                InputConstants.KEY_NUMPAD4, InputConstants.KEY_NUMPAD5, InputConstants.KEY_NUMPAD6, InputConstants.KEY_NUMPAD7,
                InputConstants.KEY_NUMPAD8, InputConstants.KEY_NUMPAD9};
        for (int i = 0; i < numpad.length; i++) {
            KEY_CHARS.put(numpad[i], new String[]{String.valueOf(i), String.valueOf(i)});
        }
        KEY_CHARS.put(InputConstants.KEY_SPACE, new String[]{" ", " "});
        KEY_CHARS.put(InputConstants.KEY_COMMA, new String[]{",", "<"});
        KEY_CHARS.put(InputConstants.KEY_PERIOD, new String[]{".", ">"});
        KEY_CHARS.put(InputConstants.KEY_SLASH, new String[]{"/", "?"});
        KEY_CHARS.put(InputConstants.KEY_SEMICOLON, new String[]{";", ":"});
        KEY_CHARS.put(InputConstants.KEY_APOSTROPHE, new String[]{"'", "\""});
        KEY_CHARS.put(InputConstants.KEY_LBRACKET, new String[]{"[", "{"});
        KEY_CHARS.put(InputConstants.KEY_RBRACKET, new String[]{"]", "}"});
        KEY_CHARS.put(InputConstants.KEY_BACKSLASH, new String[]{"\\", "|"});
        KEY_CHARS.put(InputConstants.KEY_MINUS, new String[]{"-", "_"});
        KEY_CHARS.put(InputConstants.KEY_EQUALS, new String[]{"=", "+"});
        KEY_CHARS.put(InputConstants.KEY_GRAVE, new String[]{"`", "~"});
        KEY_CHARS.put(InputConstants.KEY_ADD, new String[]{"+", "+"});
        KEY_CHARS.put(InputConstants.KEY_MULTIPLY, new String[]{"*", "*"});
    }

    private final Font font;
    private final boolean multiline;
    private final List<String> lines = new ArrayList<>();
    private Component placeholder = Component.empty();
    private Consumer<String> listener = v -> {};
    private int maxChars = Integer.MAX_VALUE;
    private int length;
    private int cursorLine;
    private int cursorCol;
    private int scrollLine;
    private int scrollX;
    private boolean allSelected;

    public SafeTextArea(Font font, int x, int y, int width, int height, Component message, boolean multiline) {
        super(x, y, width, height, message);
        this.font = font;
        this.multiline = multiline;
        lines.add("");
    }

    // ------------------------------------------------------------------ value

    public String getValue() {
        return String.join("\n", lines);
    }

    public void setValue(String value) {
        lines.clear();
        String v = value == null ? "" : value.replace("\r", "");
        if (!multiline) v = v.replace("\n", " ");
        for (String l : v.split("\n", -1)) lines.add(l);
        length = v.length();
        cursorLine = 0;
        cursorCol = 0;
        scrollLine = 0;
        scrollX = 0;
        allSelected = false;
    }

    public void setValueListener(Consumer<String> listener) { this.listener = listener; }
    public void setPlaceholder(Component placeholder) { this.placeholder = placeholder; }
    public void setMaxChars(int maxChars) { this.maxChars = maxChars; }

    private void changed() {
        length = getValue().length();
        listener.accept(getValue());
    }

    // ------------------------------------------------------------------ editing

    private void insert(String text) {
        if (allSelected) clearAll();
        String t = text.replace("\r", "").replace("\t", "  ");
        if (!multiline) t = t.replace("\n", " ");
        if (t.isEmpty() || length + t.length() > maxChars) return;
        String line = lines.get(cursorLine);
        String before = line.substring(0, cursorCol);
        String after = line.substring(cursorCol);
        String[] parts = t.split("\n", -1);
        if (parts.length == 1) {
            lines.set(cursorLine, before + t + after);
            cursorCol += t.length();
        } else {
            lines.set(cursorLine, before + parts[0]);
            for (int i = 1; i < parts.length; i++) lines.add(cursorLine + i, parts[i]);
            cursorLine += parts.length - 1;
            cursorCol = parts[parts.length - 1].length();
            lines.set(cursorLine, lines.get(cursorLine) + after);
        }
        changed();
    }

    private void clearAll() {
        lines.clear();
        lines.add("");
        cursorLine = 0;
        cursorCol = 0;
        allSelected = false;
    }

    private void backspace() {
        if (allSelected) {
            clearAll();
        } else if (cursorCol > 0) {
            String line = lines.get(cursorLine);
            lines.set(cursorLine, line.substring(0, cursorCol - 1) + line.substring(cursorCol));
            cursorCol--;
        } else if (cursorLine > 0) {
            int prevLen = lines.get(cursorLine - 1).length();
            lines.set(cursorLine - 1, lines.get(cursorLine - 1) + lines.remove(cursorLine));
            cursorLine--;
            cursorCol = prevLen;
        } else {
            return;
        }
        changed();
    }

    private void deleteForward() {
        if (allSelected) {
            clearAll();
        } else {
            String line = lines.get(cursorLine);
            if (cursorCol < line.length()) {
                lines.set(cursorLine, line.substring(0, cursorCol) + line.substring(cursorCol + 1));
            } else if (cursorLine < lines.size() - 1) {
                lines.set(cursorLine, line + lines.remove(cursorLine + 1));
            } else {
                return;
            }
        }
        changed();
    }

    private void moveTo(int line, int col) {
        allSelected = false;
        cursorLine = Math.max(0, Math.min(lines.size() - 1, line));
        cursorCol = Math.max(0, Math.min(lines.get(cursorLine).length(), col));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (!isFocused()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (e.isSelectAll()) {
            allSelected = true;
            return true;
        }
        if (e.isCopy()) {
            mc.keyboardHandler.setClipboard(allSelected ? getValue() : lines.get(cursorLine));
            return true;
        }
        if (e.isCut()) {
            mc.keyboardHandler.setClipboard(allSelected ? getValue() : lines.get(cursorLine));
            if (allSelected) {
                clearAll();
                changed();
            }
            return true;
        }
        if (e.isPaste()) {
            insert(mc.keyboardHandler.getClipboard());
            return true;
        }
        int key = e.key();
        if (multiline && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)) {
            insert("\n");
            return true;
        }
        if (key == InputConstants.KEY_BACKSPACE) { backspace(); return true; }
        if (key == InputConstants.KEY_DELETE) { deleteForward(); return true; }
        if (key == InputConstants.KEY_LEFT) {
            if (cursorCol == 0 && cursorLine > 0) moveTo(cursorLine - 1, Integer.MAX_VALUE);
            else moveTo(cursorLine, cursorCol - 1);
            return true;
        }
        if (key == InputConstants.KEY_RIGHT) {
            if (cursorCol == lines.get(cursorLine).length() && cursorLine < lines.size() - 1) moveTo(cursorLine + 1, 0);
            else moveTo(cursorLine, cursorCol + 1);
            return true;
        }
        if (multiline && key == InputConstants.KEY_UP) { moveTo(cursorLine - 1, cursorCol); return true; }
        if (multiline && key == InputConstants.KEY_DOWN) { moveTo(cursorLine + 1, cursorCol); return true; }
        if (key == InputConstants.KEY_HOME) { moveTo(e.hasControlDown() ? 0 : cursorLine, 0); return true; }
        if (key == InputConstants.KEY_END) {
            moveTo(e.hasControlDown() ? lines.size() - 1 : cursorLine, Integer.MAX_VALUE);
            return true;
        }
        if (multiline && key == InputConstants.KEY_PAGEUP) { moveTo(cursorLine - visibleLines(), cursorCol); return true; }
        if (multiline && key == InputConstants.KEY_PAGEDOWN) { moveTo(cursorLine + visibleLines(), cursorCol); return true; }

        if (!e.hasControlDown() && !e.hasAltDown()) {
            String[] chars = KEY_CHARS.get(key);
            if (chars != null) {
                insert(e.hasShiftDown() ? chars[1] : chars[0]);
                return true;
            }
        }
        // Swallow other keys while focused (e.g. E would otherwise do nothing anyway, but keep it predictable),
        // except Escape and Tab, which the screen uses.
        return key != InputConstants.KEY_ESCAPE && key != InputConstants.KEY_TAB;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int line = scrollLine + (int) ((event.y() - getY() - PAD) / lineHeight());
        line = Math.max(0, Math.min(lines.size() - 1, line));
        String text = lines.get(line);
        int target = (int) (event.x() - getX() - PAD) + scrollX;
        int col = font.plainSubstrByWidth(text, Math.max(0, target)).length();
        moveTo(line, col);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollXAmount, double scrollYAmount) {
        if (!multiline || !isMouseOver(mouseX, mouseY)) return false;
        scrollLine = Math.max(0, Math.min(Math.max(0, lines.size() - visibleLines()), scrollLine - (int) Math.signum(scrollYAmount) * 3));
        return true;
    }

    // ------------------------------------------------------------------ rendering

    private int lineHeight() { return font.lineHeight + 2; }
    private int visibleLines() { return Math.max(1, (getHeight() - 2 * PAD) / lineHeight()); }

    private void ensureCursorVisible(int innerW) {
        int vis = visibleLines();
        if (cursorLine < scrollLine) scrollLine = cursorLine;
        if (cursorLine >= scrollLine + vis) scrollLine = cursorLine - vis + 1;
        int cx = font.width(lines.get(cursorLine).substring(0, cursorCol));
        if (cx - scrollX > innerW - 4) scrollX = cx - innerW + 4;
        if (cx - scrollX < 0) scrollX = Math.max(0, cx - innerW / 3);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int innerW = w - 2 * PAD;
        g.fill(x, y, x + w, y + h, isFocused() ? 0xFFFFFFFF : 0xFFA0A0A0);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF000000);
        if (isFocused()) ensureCursorVisible(innerW);

        boolean empty = lines.size() == 1 && lines.get(0).isEmpty();
        int textY = multiline ? y + PAD : y + (h - font.lineHeight) / 2;
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        if (empty && !isFocused()) {
            g.text(font, placeholder, x + PAD, textY, 0xFF707070, false);
        }
        int vis = visibleLines();
        for (int i = 0; i < vis && scrollLine + i < lines.size(); i++) {
            int li = scrollLine + i;
            int ly = textY + i * lineHeight();
            String line = lines.get(li);
            if (allSelected && !line.isEmpty()) {
                g.fill(x + PAD - scrollX, ly - 1, x + PAD - scrollX + font.width(line), ly + font.lineHeight, 0xFF3060C0);
            }
            g.text(font, line, x + PAD - scrollX, ly, 0xFFE0E0E0, false);
            if (isFocused() && li == cursorLine && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = x + PAD - scrollX + font.width(line.substring(0, cursorCol));
                g.fill(cx, ly - 1, cx + 1, ly + font.lineHeight, 0xFFFFFFFF);
            }
        }
        g.disableScissor();

        if (multiline && lines.size() > vis) {
            int trackH = h - 2;
            int barH = Math.max(8, trackH * vis / lines.size());
            int maxScroll = lines.size() - vis;
            int barY = y + 1 + (trackH - barH) * Math.min(scrollLine, maxScroll) / maxScroll;
            g.fill(x + w - 4, barY, x + w - 1, barY + barH, 0xFF808080);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}

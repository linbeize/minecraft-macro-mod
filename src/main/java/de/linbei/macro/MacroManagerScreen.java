package de.linbei.macro;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Script manager.
 *
 * <pre>
 * Macro Manager                               ▶ Running · farm · line 5 · wait 12.3s
 * ┌ Scripts (3) ──┐ Name [farm______] [Save] [Rename] [Revert]
 * │ ▶ farm *      │ ┌──────────────────────────────────────────┐
 * │   example     │ │ editor (SafeTextArea, or vanilla w/ IME)  │
 * │   macro_2     │ └──────────────────────────────────────────┘
 * │[New][Copy][Del]│ ✔ OK: 12 instructions             ● Unsaved
 * status message
 * [▶ Start][■ Stop][Repeat][Aim lock][HUD][Start/Pause key][Stop key][?][Done]
 * </pre>
 *
 * The draft (text, file name, typed name) is static so closing the screen never loses edits.
 * When the macro is stopped, the engine follows the draft on close, so the hotkey runs what
 * the user last saw in the editor.
 */
public class MacroManagerScreen extends Screen {
    private static final int MARGIN = 6;
    private static final int ROW_H = 20;
    private static final int GAP = 4;
    private static final int TITLE_H = 20;
    private static final int MAX_SCRIPT_CHARS = 200_000;
    private static final long CONFIRM_MS = 4_000;
    private static final long MESSAGE_MS = 8_000;

    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_DIM = 0xFF909090;
    private static final int COLOR_OK = 0xFF60E060;
    private static final int COLOR_WARN = 0xFFFFD040;
    private static final int COLOR_ERR = 0xFFFF6060;

    private static final String NEW_TEMPLATE = """
            # New macro. Hover the "?" button for help.
            KeyDown "W"
            Delay 1000
            KeyUp "W"
            """;

    // ---- draft state, kept across openings of the screen
    private static String draftText;
    private static String draftName;      // file the draft belongs to; null = never saved
    private static String savedText;      // file content at load/save time, for the dirty check
    private static String draftNameInput; // what is typed in the name box
    private static int listScroll;

    private final MacroEngine engine = MacroModClient.ENGINE;
    private final List<String> scripts = new ArrayList<>();
    private final List<Button> rowButtons = new ArrayList<>();

    // Either vanilla widgets (IME on) or SafeTextArea (default), see settings.imeEditor.
    private AbstractWidget editorWidget;
    private Consumer<String> editorSetter;
    private AbstractWidget nameWidget;
    private Supplier<String> nameGetter;
    private Consumer<String> nameSetter;
    private Button imeBtn;
    private Button runBtn;
    private Button stopBtn;
    private Button repeatBtn;
    private Button aimBtn;
    private Button hudBtn;
    private Button toggleBindBtn;
    private Button stopBindBtn;
    private Button copyBtn;
    private Button deleteBtn;
    private Button renameBtn;
    private Button revertBtn;
    private BindTarget waitingBind = BindTarget.NONE;

    private boolean valid = true;
    private Component validation = Component.empty();
    private Component message = Component.empty();
    private int messageColor = COLOR_TEXT;
    private long messageTime;
    private String pendingConfirm;
    private long pendingUntil;

    // layout, computed in init()
    private int leftX;
    private int leftW;
    private int rightX;
    private int rightW;
    private int top;
    private int listTop;
    private int listRows;
    private int contentBottom;
    private int barTop;

    public MacroManagerScreen() {
        super(Component.translatable("macro_mod.gui.title"));
    }

    // ================================================================== layout

    @Override
    protected void init() {
        refreshScriptList();
        if (draftText == null || (!isDirty() && !Objects.equals(draftName, engine.getScriptName()))) {
            loadDraftFromEngine();
        }

        leftW = Mth.clamp(this.width / 4, 110, 170);
        leftX = MARGIN;
        rightX = leftX + leftW + 10;
        rightW = this.width - rightX - MARGIN;
        top = TITLE_H;

        // ---- bottom bar (wraps to more rows on narrow screens)
        // Dynamic labels (on/off, key names) are filled in by refreshLabels(); widths fit the longest variant.
        runBtn = button(fit(60, Component.translatable("macro_mod.gui.start"), Component.translatable("macro_mod.gui.pause"),
                Component.translatable("macro_mod.gui.resume")), "macro_mod.gui.start.tooltip", b -> onRun());
        stopBtn = button(fit(40, Component.translatable("macro_mod.gui.stop")), null,
                b -> say(MacroModClient.stop(), COLOR_TEXT));
        repeatBtn = button(fit(60, Component.translatable("macro_mod.gui.repeat", MacroModClient.onOff(false))),
                "macro_mod.gui.repeat.tooltip", b -> say(MacroModClient.setRepeat(!engine.isRepeat()), COLOR_TEXT));
        aimBtn = button(fit(60, Component.translatable("macro_mod.gui.aimlock", MacroModClient.onOff(false))),
                "macro_mod.gui.aimlock.tooltip", b -> say(MacroModClient.setAimLock(!engine.isAimLock()), COLOR_TEXT));
        hudBtn = button(fit(50, Component.translatable("macro_mod.gui.hud", MacroModClient.onOff(false))),
                "macro_mod.gui.hud.tooltip", b -> say(MacroModClient.setShowHud(!MacroModClient.settings().showHud), COLOR_TEXT));
        toggleBindBtn = button(fit(90, Component.translatable("macro_mod.gui.bind_toggle", "Right Shift")),
                "macro_mod.gui.bind.tooltip", b -> startBind(BindTarget.TOGGLE));
        stopBindBtn = button(fit(70, Component.translatable("macro_mod.gui.bind_stop", "Right Shift")),
                "macro_mod.gui.bind.tooltip", b -> startBind(BindTarget.STOP));
        imeBtn = button(fit(50, Component.translatable("macro_mod.gui.ime", MacroModClient.onOff(false))),
                "macro_mod.gui.ime.tooltip", b -> toggleIme());
        Button help = button(20, "macro_mod.gui.help", b -> {});
        help.setMessage(Component.literal("?"));
        Button done = button(fit(50, Component.translatable("gui.done")), null, b -> onClose());
        done.setMessage(Component.translatable("gui.done"));
        List<AbstractWidget> bar = List.of(runBtn, stopBtn, repeatBtn, aimBtn, hudBtn, toggleBindBtn, stopBindBtn, imeBtn, help, done);
        barTop = flowLayout(bar);
        bar.forEach(this::addRenderableWidget);
        contentBottom = barTop - 14;

        // ---- left: script list
        listTop = top + 14;
        int actionY = contentBottom - ROW_H;
        listRows = Math.max(1, (actionY - GAP - listTop + 2) / (ROW_H + 2));
        rowButtons.clear();
        for (int i = 0; i < listRows; i++) {
            final int row = i;
            Button b = Button.builder(Component.empty(), x -> onRowClicked(row))
                    .bounds(leftX, listTop + i * (ROW_H + 2), leftW, ROW_H).build();
            rowButtons.add(addRenderableWidget(b));
        }
        int aw = (leftW - 2 * GAP) / 3;
        addRenderableWidget(Button.builder(Component.translatable("macro_mod.gui.new"), b -> newScript())
                .bounds(leftX, actionY, aw, ROW_H)
                .tooltip(Tooltip.create(Component.translatable("macro_mod.gui.new.tooltip"))).build());
        copyBtn = addRenderableWidget(Button.builder(Component.translatable("macro_mod.gui.copy"), b -> copyScript())
                .bounds(leftX + aw + GAP, actionY, aw, ROW_H)
                .tooltip(Tooltip.create(Component.translatable("macro_mod.gui.copy.tooltip"))).build());
        deleteBtn = addRenderableWidget(Button.builder(Component.translatable("macro_mod.gui.delete"), b -> deleteScript())
                .bounds(leftX + 2 * (aw + GAP), actionY, leftW - 2 * (aw + GAP), ROW_H)
                .tooltip(Tooltip.create(Component.translatable("macro_mod.gui.delete.tooltip"))).build());

        // ---- right: name row
        int labelW = this.font.width(Component.translatable("macro_mod.gui.name")) + 6;
        int bw = fit(40, Component.translatable("macro_mod.gui.save"), Component.translatable("macro_mod.gui.rename"),
                Component.translatable("macro_mod.gui.revert"));
        int nameW = Math.max(50, rightW - labelW - 3 * (bw + GAP));
        boolean ime = MacroModClient.settings().imeEditor;
        Component nameLabel = Component.translatable("macro_mod.gui.name");
        Component nameHint = Component.translatable("macro_mod.gui.name.hint").withStyle(ChatFormatting.DARK_GRAY);
        if (ime) {
            EditBox box = new EditBox(this.font, rightX + labelW, top, nameW, ROW_H, nameLabel);
            box.setMaxLength(48);
            box.setHint(nameHint);
            box.setResponder(v -> draftNameInput = v);
            nameWidget = box;
            nameGetter = box::getValue;
            nameSetter = box::setValue;
        } else {
            SafeTextArea box = new SafeTextArea(this.font, rightX + labelW, top, nameW, ROW_H, nameLabel, false);
            box.setMaxChars(48);
            box.setPlaceholder(nameHint);
            box.setValueListener(v -> draftNameInput = v);
            nameWidget = box;
            nameGetter = box::getValue;
            nameSetter = box::setValue;
        }
        nameSetter.accept(draftNameInput == null ? "" : draftNameInput);
        nameWidget.setTooltip(Tooltip.create(Component.translatable("macro_mod.gui.name.tooltip")));
        addRenderableWidget(nameWidget);
        int bx = nameWidget.getRight() + GAP;
        addRenderableWidget(Button.builder(Component.translatable("macro_mod.gui.save"), b -> save())
                .bounds(bx, top, bw, ROW_H).tooltip(Tooltip.create(Component.translatable("macro_mod.gui.save.tooltip"))).build());
        renameBtn = addRenderableWidget(Button.builder(Component.translatable("macro_mod.gui.rename"), b -> rename())
                .bounds(bx + bw + GAP, top, bw, ROW_H).tooltip(Tooltip.create(Component.translatable("macro_mod.gui.rename.tooltip"))).build());
        revertBtn = addRenderableWidget(Button.builder(Component.translatable("macro_mod.gui.revert"), b -> revert())
                .bounds(bx + 2 * (bw + GAP), top, bw, ROW_H).tooltip(Tooltip.create(Component.translatable("macro_mod.gui.revert.tooltip"))).build());

        // ---- right: editor
        int edTop = top + ROW_H + GAP;
        int edH = Math.max(30, contentBottom - 12 - edTop);
        Component edLabel = Component.translatable("macro_mod.gui.editor");
        Component edPlaceholder = Component.translatable("macro_mod.gui.editor.placeholder");
        if (ime) {
            // Vanilla editor: IME / Chinese input, selection, undo. Enables OS text input when focused.
            MultiLineEditBox box = MultiLineEditBox.builder().setX(rightX).setY(edTop).setPlaceholder(edPlaceholder)
                    .build(this.font, rightW, edH, edLabel);
            box.setCharacterLimit(MAX_SCRIPT_CHARS);
            box.setValue(draftText);
            box.setValueListener(this::onEditorChanged);
            editorWidget = box;
            editorSetter = box::setValue;
        } else {
            // Default: never enables IME text input (it crashed some clients, e.g. Lunar).
            SafeTextArea box = new SafeTextArea(this.font, rightX, edTop, rightW, edH, edLabel, true);
            box.setMaxChars(MAX_SCRIPT_CHARS);
            box.setPlaceholder(edPlaceholder);
            box.setValue(draftText);
            box.setValueListener(this::onEditorChanged);
            editorWidget = box;
            editorSetter = box::setValue;
        }
        addRenderableWidget(editorWidget);
        setInitialFocus(editorWidget);

        validate();
        clampListScroll();
        refreshListButtons();
        refreshLabels();
    }

    private Button button(int width, String tooltipKey, Button.OnPress press) {
        Button.Builder b = Button.builder(Component.empty(), press).size(width, ROW_H);
        if (tooltipKey != null) b.tooltip(Tooltip.create(Component.translatable(tooltipKey)));
        return b.build();
    }

    /** Width that fits the widest of the given labels. */
    private int fit(int min, Component... labels) {
        int w = min;
        for (Component c : labels) w = Math.max(w, this.font.width(c) + 12);
        return w;
    }

    /** Places widgets left-to-right from the bottom of the screen, wrapping as needed. Returns the top y. */
    private int flowLayout(List<AbstractWidget> widgets) {
        int avail = this.width - 2 * MARGIN;
        List<List<AbstractWidget>> rows = new ArrayList<>();
        List<AbstractWidget> cur = new ArrayList<>();
        int curW = 0;
        for (AbstractWidget w : widgets) {
            int need = (cur.isEmpty() ? 0 : GAP) + w.getWidth();
            if (!cur.isEmpty() && curW + need > avail) {
                rows.add(cur);
                cur = new ArrayList<>();
                curW = 0;
                need = w.getWidth();
            }
            cur.add(w);
            curW += need;
        }
        rows.add(cur);

        int y = this.height - MARGIN - rows.size() * ROW_H - (rows.size() - 1) * GAP;
        int firstY = y;
        for (List<AbstractWidget> row : rows) {
            int x = MARGIN;
            for (AbstractWidget w : row) {
                w.setX(x);
                w.setY(y);
                x += w.getWidth() + GAP;
            }
            y += ROW_H + GAP;
        }
        return firstY;
    }

    // ================================================================== draft handling

    private void loadDraftFromEngine() {
        String name = engine.getScriptName();
        if (name != null && MacroStorage.exists(name)) {
            try {
                setDraftFromFile(name);
                return;
            } catch (IOException ignored) {
                // fall through to the in-memory script
            }
        }
        draftText = engine.getScript();
        draftName = null;
        savedText = null;
        draftNameInput = name != null ? name : MacroStorage.uniqueName("macro");
    }

    private static void setDraftFromFile(String name) throws IOException {
        String text = MacroStorage.load(name);
        draftText = text;
        savedText = text;
        draftName = MacroStorage.normalizeName(name);
        draftNameInput = draftName;
    }

    /** Push the draft into the widgets after it was replaced programmatically. */
    private void applyDraftToWidgets() {
        editorSetter.accept(draftText);
        nameSetter.accept(draftNameInput == null ? "" : draftNameInput);
        validate();
        scrollToSelected();
        refreshListButtons();
        refreshLabels();
    }

    private static boolean isDirty() {
        return draftText != null && (savedText == null || !savedText.equals(draftText));
    }

    private void onEditorChanged(String value) {
        draftText = value;
        validate();
    }

    private void validate() {
        try {
            int n = MacroParser.parse(draftText).size();
            valid = true;
            validation = Component.translatable("macro_mod.gui.valid", n);
        } catch (MacroParser.ParseException e) {
            valid = false;
            validation = MacroModClient.describeError(e);
        }
    }

    /** While stopped, the engine (and the hotkey) follows whatever the editor shows. */
    private void syncEngineIfStopped() {
        if (!engine.isStopped() || !valid) return;
        try {
            engine.setScript(draftText, isDirty() ? null : draftName);
            if (draftName != null && !isDirty()) MacroModClient.rememberLastScript(draftName);
        } catch (MacroParser.ParseException ignored) {
            // validated above
        }
    }

    // ================================================================== actions

    private void onRun() {
        if (!engine.isStopped()) {
            say(MacroModClient.toggleRun(), COLOR_TEXT);
            return;
        }
        if (!valid) {
            say(Component.translatable("macro_mod.gui.fix_errors_first", validation), COLOR_ERR);
            return;
        }
        engine.setScript(draftText, isDirty() ? null : draftName);
        if (draftName != null && !isDirty()) MacroModClient.rememberLastScript(draftName);
        Component result = MacroModClient.toggleRun();
        if (engine.isRunning()) {
            // The macro is frozen while any screen is open, so go back to the game.
            onClose();
            MacroModClient.notify(result, true);
        } else {
            say(result, COLOR_ERR);
        }
    }

    private void onRowClicked(int row) {
        int idx = listScroll + row;
        if (idx < 0 || idx >= scripts.size()) return;
        String name = scripts.get(idx);
        if (name.equals(draftName)) return;
        if (isDirty() && !confirm("open:" + name, Component.translatable("macro_mod.gui.confirm_discard"))) return;
        try {
            setDraftFromFile(name);
            applyDraftToWidgets();
            syncEngineIfStopped();
            say(Component.translatable("macro_mod.gui.opened", name), COLOR_OK);
        } catch (IOException e) {
            sayError(e);
        }
    }

    private void save() {
        String name = MacroStorage.normalizeName(nameGetter.get());
        if (!MacroStorage.isValidName(name)) {
            say(Component.translatable("macro_mod.gui.invalid_name"), COLOR_ERR);
            return;
        }
        boolean otherFile = draftName == null || !draftName.equalsIgnoreCase(name);
        if (otherFile && MacroStorage.exists(name)
                && !confirm("overwrite:" + name, Component.translatable("macro_mod.gui.confirm_overwrite", name))) {
            return;
        }
        try {
            MacroStorage.save(name, draftText);
            draftName = name;
            savedText = draftText;
            draftNameInput = name;
            refreshScriptList();
            applyDraftToWidgets();
            syncEngineIfStopped();
            say(valid ? Component.translatable("macro_mod.gui.saved", name)
                    : Component.translatable("macro_mod.gui.saved_with_errors", name), valid ? COLOR_OK : COLOR_WARN);
        } catch (IOException e) {
            sayError(e);
        }
    }

    private void rename() {
        if (draftName == null) {
            save();
            return;
        }
        String name = MacroStorage.normalizeName(nameGetter.get());
        if (!MacroStorage.isValidName(name)) {
            say(Component.translatable("macro_mod.gui.invalid_name"), COLOR_ERR);
            return;
        }
        if (name.equals(draftName)) {
            say(Component.translatable("macro_mod.gui.rename_same"), COLOR_WARN);
            return;
        }
        try {
            String old = draftName;
            MacroStorage.rename(old, name);
            if (old.equals(engine.getScriptName())) engine.setScriptName(name);
            if (old.equals(MacroModClient.settings().lastScript)) MacroModClient.rememberLastScript(name);
            draftName = name;
            draftNameInput = name;
            refreshScriptList();
            applyDraftToWidgets();
            say(Component.translatable("macro_mod.gui.renamed", old, name), COLOR_OK);
        } catch (IOException e) {
            sayError(e);
        }
    }

    private void revert() {
        if (draftName == null) return;
        if (!isDirty()) {
            say(Component.translatable("macro_mod.gui.nothing_to_revert"), COLOR_TEXT);
            return;
        }
        if (!confirm("revert", Component.translatable("macro_mod.gui.confirm_discard"))) return;
        try {
            setDraftFromFile(draftName);
            applyDraftToWidgets();
            syncEngineIfStopped();
            say(Component.translatable("macro_mod.gui.reverted"), COLOR_OK);
        } catch (IOException e) {
            sayError(e);
        }
    }

    private void newScript() {
        if (isDirty() && !confirm("new", Component.translatable("macro_mod.gui.confirm_discard"))) return;
        draftText = NEW_TEMPLATE;
        draftName = null;
        savedText = null;
        draftNameInput = MacroStorage.uniqueName("macro");
        applyDraftToWidgets();
        say(Component.translatable("macro_mod.gui.new_created"), COLOR_OK);
        setFocused(editorWidget);
    }

    private void copyScript() {
        if (draftName == null) return;
        String target = MacroStorage.uniqueName(draftName + "_copy");
        try {
            MacroStorage.save(target, draftText);
            setDraftFromFile(target);
            refreshScriptList();
            applyDraftToWidgets();
            say(Component.translatable("macro_mod.gui.copied", target), COLOR_OK);
        } catch (IOException e) {
            sayError(e);
        }
    }

    private void deleteScript() {
        if (draftName == null) return;
        String name = draftName;
        if (!confirm("delete:" + name, Component.translatable("macro_mod.gui.confirm_delete", name))) return;
        try {
            int idx = scripts.indexOf(name);
            MacroStorage.delete(name);
            if (name.equals(engine.getScriptName())) engine.setScriptName(null);
            refreshScriptList();
            if (scripts.isEmpty()) {
                draftText = NEW_TEMPLATE;
                draftName = null;
                savedText = null;
                draftNameInput = MacroStorage.uniqueName("macro");
            } else {
                setDraftFromFile(scripts.get(Mth.clamp(idx, 0, scripts.size() - 1)));
            }
            applyDraftToWidgets();
            syncEngineIfStopped();
            say(Component.translatable("macro_mod.gui.deleted", name), COLOR_OK);
        } catch (IOException e) {
            sayError(e);
        }
    }

    /** Switch between the safe editor and the vanilla IME editor. The draft is static, so nothing is lost. */
    private void toggleIme() {
        MacroModClient.setImeEditor(!MacroModClient.settings().imeEditor);
        rebuildWidgets();
        say(Component.translatable(MacroModClient.settings().imeEditor ? "macro_mod.gui.ime_on" : "macro_mod.gui.ime_off"),
                MacroModClient.settings().imeEditor ? COLOR_WARN : COLOR_OK);
    }

    private void startBind(BindTarget target) {
        waitingBind = target;
        refreshLabels();
        say(Component.translatable("macro_mod.gui.bind_prompt"), COLOR_WARN);
    }

    /** First call arms the action and shows the prompt; a second call within a few seconds confirms it. */
    private boolean confirm(String token, Component prompt) {
        long now = System.currentTimeMillis();
        if (token.equals(pendingConfirm) && now < pendingUntil) {
            pendingConfirm = null;
            return true;
        }
        pendingConfirm = token;
        pendingUntil = now + CONFIRM_MS;
        say(prompt, COLOR_WARN);
        return false;
    }

    private void say(Component msg, int color) {
        message = msg;
        messageColor = color;
        messageTime = System.currentTimeMillis();
    }

    private void sayError(Exception e) {
        say(MacroModClient.describeError(e), COLOR_ERR);
    }

    // ================================================================== list

    private void refreshScriptList() {
        scripts.clear();
        try {
            scripts.addAll(MacroStorage.listScriptNames());
        } catch (IOException e) {
            sayError(e);
        }
        clampListScroll();
    }

    private void clampListScroll() {
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, scripts.size() - Math.max(1, listRows)));
    }

    private void scrollToSelected() {
        int idx = draftName == null ? -1 : scripts.indexOf(draftName);
        if (idx < 0) return;
        if (idx < listScroll) listScroll = idx;
        if (idx >= listScroll + listRows) listScroll = idx - listRows + 1;
        clampListScroll();
    }

    private void refreshListButtons() {
        boolean dirty = isDirty();
        for (int i = 0; i < rowButtons.size(); i++) {
            Button b = rowButtons.get(i);
            int idx = listScroll + i;
            if (idx >= scripts.size()) {
                b.visible = false;
                continue;
            }
            String name = scripts.get(idx);
            b.visible = true;
            if (name.equals(draftName)) {
                b.setMessage(Component.literal("▶ " + name + (dirty ? " *" : "")).withStyle(ChatFormatting.YELLOW));
            } else if (name.equals(engine.getScriptName()) && !engine.isStopped()) {
                b.setMessage(Component.literal(name).withStyle(ChatFormatting.GREEN));
            } else {
                b.setMessage(Component.literal(name));
            }
        }
    }

    // ================================================================== labels / tick

    @Override
    public void tick() {
        super.tick();
        refreshLabels();
        refreshListButtons();
    }

    private void refreshLabels() {
        if (runBtn == null) return;
        runBtn.setMessage(switch (engine.state()) {
            case STOPPED -> Component.translatable("macro_mod.gui.start");
            case RUNNING -> Component.translatable("macro_mod.gui.pause");
            case PAUSED -> Component.translatable("macro_mod.gui.resume");
        });
        stopBtn.setMessage(Component.translatable("macro_mod.gui.stop"));
        stopBtn.active = !engine.isStopped();
        repeatBtn.setMessage(Component.translatable("macro_mod.gui.repeat", MacroModClient.onOff(engine.isRepeat())));
        aimBtn.setMessage(Component.translatable("macro_mod.gui.aimlock", MacroModClient.onOff(engine.isAimLock())));
        hudBtn.setMessage(Component.translatable("macro_mod.gui.hud", MacroModClient.onOff(MacroModClient.settings().showHud)));
        imeBtn.setMessage(Component.translatable("macro_mod.gui.ime", MacroModClient.onOff(MacroModClient.settings().imeEditor)));
        toggleBindBtn.setMessage(Component.translatable("macro_mod.gui.bind_toggle", bindLabel(BindTarget.TOGGLE, MacroModClient.toggleKey())));
        stopBindBtn.setMessage(Component.translatable("macro_mod.gui.bind_stop", bindLabel(BindTarget.STOP, MacroModClient.stopKey())));
        boolean hasFile = draftName != null;
        copyBtn.active = hasFile;
        deleteBtn.active = hasFile;
        renameBtn.active = hasFile;
        revertBtn.active = hasFile;
    }

    private Component bindLabel(BindTarget target, KeyMapping key) {
        if (waitingBind == target) return Component.literal("> ? <").withStyle(ChatFormatting.YELLOW);
        return key.getTranslatedKeyMessage();
    }

    // ================================================================== input

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (waitingBind != BindTarget.NONE) {
            KeyMapping km = waitingBind == BindTarget.TOGGLE ? MacroModClient.toggleKey() : MacroModClient.stopKey();
            waitingBind = BindTarget.NONE;
            if (event.isEscape()) {
                say(Component.translatable("macro_mod.gui.bind_cancelled"), COLOR_TEXT);
            } else {
                km.setKey(InputConstants.getKey(event));
                KeyMapping.resetMapping();
                this.minecraft.options.save(); // vanilla options.txt is the single source of truth
                say(Component.translatable("macro_mod.gui.bind_set", km.getTranslatedKeyMessage()), COLOR_OK);
            }
            refreshLabels();
            return true;
        }
        if (event.hasControlDown() && event.key() == InputConstants.KEY_S) {
            save();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        boolean overList = mouseX >= leftX && mouseX < leftX + leftW
                && mouseY >= listTop && mouseY < listTop + listRows * (ROW_H + 2);
        if (overList && scripts.size() > listRows) {
            listScroll -= (int) Math.signum(scrollY);
            clampListScroll();
            refreshListButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        waitingBind = BindTarget.NONE;
        syncEngineIfStopped();
        super.removed();
    }

    // ================================================================== rendering

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        // script list panel
        g.fill(leftX - 3, top - 3, leftX + leftW + 3, contentBottom + 3, 0x90000000);
        g.fill(leftX - 3, top - 3, leftX + leftW + 3, top - 2, 0x40FFFFFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        // title + live engine status
        g.text(this.font, this.title, leftX, 6, 0xFFFFFFFF, true);
        int statusX = leftX + this.font.width(this.title) + 12;
        MutableComponent status = MacroStatus.describe(engine);
        if (this.font.width(status) > this.width - MARGIN - statusX) status = MacroStatus.stateLabel(engine);
        g.text(this.font, status, Math.max(statusX, this.width - MARGIN - this.font.width(status)), 6, MacroStatus.color(engine), true);

        // list header
        g.text(this.font, Component.translatable("macro_mod.gui.scripts", scripts.size()), leftX, top + 1, COLOR_TEXT, false);
        if (scripts.size() > listRows) {
            String range = (listScroll + 1) + "-" + Math.min(scripts.size(), listScroll + listRows) + "/" + scripts.size();
            g.text(this.font, range, leftX + leftW - this.font.width(range), top + 1, COLOR_DIM, false);
        }
        if (scripts.isEmpty()) {
            g.text(this.font, Component.translatable("macro_mod.gui.no_scripts"), leftX + 4, listTop + 6, COLOR_DIM, false);
        }

        // name label
        g.text(this.font, Component.translatable("macro_mod.gui.name"), rightX, top + 6, COLOR_TEXT, false);

        // validation + dirty marker below the editor
        int vy = editorWidget.getBottom() + 3;
        Component v = Component.literal(valid ? "✔ " : "✖ ").append(validation);
        g.text(this.font, v, rightX, vy, valid ? COLOR_OK : COLOR_ERR, false);
        if (isDirty()) {
            Component d = Component.translatable(draftName == null ? "macro_mod.gui.unsaved_new" : "macro_mod.gui.unsaved");
            g.text(this.font, d, rightX + rightW - this.font.width(d), vy, COLOR_WARN, false);
        }

        // transient message above the bottom bar
        if (System.currentTimeMillis() - messageTime < MESSAGE_MS) {
            g.text(this.font, message, MARGIN, barTop - 11, messageColor, false);
        }
    }

    private enum BindTarget { NONE, TOGGLE, STOP }
}

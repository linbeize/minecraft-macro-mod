package de.linbei.macro;

import de.linbei.macro.compat.Compat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** On/off switch for the mining-events overlay; drag the box to move it. */
public class ChEventsScreen extends Screen {
    public static final int DEFAULT_X = 2;
    public static final int DEFAULT_Y = 40;

    private final Screen parent;
    private Button toggleBtn;
    private int[] box = {0, 0, 0, 0};
    private boolean dragging;
    private double grabX;
    private double grabY;

    public ChEventsScreen(Screen parent) {
        super(Component.translatable("macro_mod.chevents.screen"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = this.height - 28;
        int cx = this.width / 2;
        toggleBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
                    MacroModClient.setChEvents(!MacroModClient.settings().chEvents);
                    refreshLabels();
                })
                .bounds(cx - 154, y, 100, 20)
                .tooltip(Tooltip.create(Component.translatable("macro_mod.chevents.toggle.tooltip")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("macro_mod.chevents.reset"),
                        b -> MacroModClient.setChEventsPos(DEFAULT_X, DEFAULT_Y))
                .bounds(cx - 50, y, 100, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(cx + 54, y, 100, 20)
                .build());
        refreshLabels();
        ChEvents.refreshSoon();
    }

    private void refreshLabels() {
        toggleBtn.setMessage(Component.translatable("macro_mod.chevents.toggle",
                MacroModClient.onOff(MacroModClient.settings().chEvents)));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mx = event.x(), my = event.y();
        if (event.button() == 0 && mx >= box[0] && mx < box[0] + box[2] && my >= box[1] && my < box[1] + box[3]) {
            dragging = true;
            grabX = mx - box[0];
            grabY = my - box[1];
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            // Clamp here too, so the saved value matches what is shown.
            int x = (int) Math.round(event.x() - grabX);
            int y = (int) Math.round(event.y() - grabY);
            MacroModClient.settings().chEventsX = Math.max(0, Math.min(x, this.width - box[2]));
            MacroModClient.settings().chEventsY = Math.max(0, Math.min(y, this.height - box[3]));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            MacroModClient.setChEventsPos(MacroModClient.settings().chEventsX, MacroModClient.settings().chEventsY);
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // Light tint instead of blur, so the overlay can be placed against the real game view.
        g.fill(0, 0, this.width, this.height, 0x40000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        box = ChEventsHud.draw(g, this.font, ChEventsHud.lines(), true);
        Component hint = Component.translatable(MacroModClient.settings().chEvents
                ? "macro_mod.chevents.drag_hint" : "macro_mod.chevents.off_hint");
        g.text(this.font, hint, (this.width - this.font.width(hint)) / 2, this.height - 42, 0xFFE0E0E0, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Compat.setScreen(this.minecraft, parent);
    }
}

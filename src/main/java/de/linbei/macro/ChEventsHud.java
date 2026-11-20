package de.linbei.macro;

import de.linbei.macro.compat.Compat;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Movable overlay listing running and upcoming mining events. */
public final class ChEventsHud {
    private static final int PAD = 4;
    private static final int LINE_GAP = 1;

    private ChEventsHud() {}

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MacroModClient.MOD_ID, "ch_events"),
                (graphics, deltaTracker) -> {
                    Minecraft mc = Minecraft.getInstance();
                    // The position editor draws its own copy (the HUD would sit under the screen).
                    if (!MacroModClient.settings().chEvents || Compat.screen(mc) instanceof ChEventsScreen) return;
                    draw(graphics, mc.font, lines(), false);
                });
    }

    /** Box size for the given lines: {width, height}. */
    private static int[] size(Font font, List<Component> lines) {
        int w = 0;
        for (Component c : lines) w = Math.max(w, font.width(c));
        return new int[]{w + 2 * PAD, lines.size() * (font.lineHeight + LINE_GAP) - LINE_GAP + 2 * PAD};
    }

    /** Saved top-left corner, clamped so the box always stays on screen. */
    private static int[] position(int boxW, int boxH) {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        MacroSettings.Settings s = MacroModClient.settings();
        int x = Math.max(0, Math.min(s.chEventsX, sw - boxW));
        int y = Math.max(0, Math.min(s.chEventsY, sh - boxH));
        return new int[]{x, y};
    }

    /** Draws the box at the saved position; returns {x, y, w, h}. */
    public static int[] draw(GuiGraphicsExtractor g, Font font, List<Component> lines, boolean highlight) {
        int[] size = size(font, lines);
        int[] pos = position(size[0], size[1]);
        int x = pos[0], y = pos[1], w = size[0], h = size[1];
        g.fill(x, y, x + w, y + h, 0x90000000);
        if (highlight) {
            int c = 0xFFFFD040;
            g.fill(x, y, x + w, y + 1, c);
            g.fill(x, y + h - 1, x + w, y + h, c);
            g.fill(x, y, x + 1, y + h, c);
            g.fill(x + w - 1, y, x + w, y + h, c);
        }
        int ty = y + PAD;
        for (Component c : lines) {
            g.text(font, c, x + PAD, ty, 0xFFFFFFFF, true);
            ty += font.lineHeight + LINE_GAP;
        }
        return new int[]{x, y, w, h};
    }

    /** Overlay text, built from the latest snapshot. */
    public static List<Component> lines() {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("macro_mod.chevents.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        ChEvents.Snapshot snap = ChEvents.snapshot();
        if (snap == null) {
            boolean failed = ChEvents.failed();
            out.add(Component.translatable(failed ? "macro_mod.chevents.error" : "macro_mod.chevents.loading")
                    .withStyle(failed ? ChatFormatting.RED : ChatFormatting.GRAY));
            return out;
        }

        long now = snap.now();
        String area = null;
        for (ChEvents.Event e : snap.events()) {
            if (e.endsAt() <= now) continue;
            if (!Objects.equals(area, e.area())) {
                area = e.area();
                out.add(Component.literal(ChEvents.pretty(area)).withStyle(ChatFormatting.AQUA));
            }
            MutableComponent time = e.startsAt() <= now
                    ? Component.translatable("macro_mod.chevents.ends_in", ChEvents.duration(e.endsAt() - now)).withStyle(ChatFormatting.GREEN)
                    : Component.translatable("macro_mod.chevents.starts_in", ChEvents.duration(e.startsAt() - now)).withStyle(ChatFormatting.YELLOW);
            out.add(Component.literal(" " + ChEvents.pretty(e.id()) + " ").withStyle(color(e.id()))
                    .append(time)
                    .append(Component.literal(" (" + e.lobbies() + ")").withStyle(ChatFormatting.DARK_GRAY)));
        }
        if (area == null) {
            out.add(Component.translatable("macro_mod.chevents.none").withStyle(ChatFormatting.GRAY));
        }
        if (ChEvents.failed()) {
            out.add(Component.translatable("macro_mod.chevents.stale").withStyle(ChatFormatting.RED));
        }
        return out;
    }

    private static ChatFormatting color(String id) {
        return switch (id) {
            case "DOUBLE_POWDER" -> ChatFormatting.AQUA;
            case "GONE_WITH_THE_WIND" -> ChatFormatting.BLUE;
            case "BETTER_TOGETHER" -> ChatFormatting.LIGHT_PURPLE;
            case "GOBLIN_RAID" -> ChatFormatting.RED;
            case "RAFFLE" -> ChatFormatting.GOLD;
            case "MITHRIL_GOURMAND" -> ChatFormatting.DARK_AQUA;
            default -> ChatFormatting.WHITE;
        };
    }
}

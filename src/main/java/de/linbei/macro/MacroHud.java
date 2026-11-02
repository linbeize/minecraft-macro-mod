package de.linbei.macro;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Small status line in the top-left corner while a macro is running or paused. */
public final class MacroHud {
    private MacroHud() {}

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MacroModClient.MOD_ID, "status"),
                (graphics, deltaTracker) -> extract(graphics));
    }

    private static void extract(GuiGraphicsExtractor g) {
        MacroEngine engine = MacroModClient.ENGINE;
        Minecraft mc = Minecraft.getInstance();
        // No hideGui check needed: vanilla skips all HUD elements while F1 hides the GUI.
        if (!MacroModClient.settings().showHud || engine.isStopped()) return;

        Font font = mc.font;
        Component text = MacroStatus.describe(engine);
        int w = font.width(text);
        g.fill(2, 2, 2 + w + 6, 2 + font.lineHeight + 4, 0x90000000);
        g.fill(2, 2, 4, 2 + font.lineHeight + 4, MacroStatus.color(engine));
        g.text(font, text, 7, 4, 0xFFFFFFFF, true);
    }
}

package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public class TierHudRenderer {

    private static Tier displayedTier  = null;
    private static Tier incomingTier   = null;

    private static float outAlpha      = 0f;
    private static float inAlpha       = 0f;

    private static float outStep       = 0f;
    private static float inStep        = 0f;

    private static boolean fading      = false;

    private static final float FADE_OUT_SECS = 0.25f;
    private static final int   HUD_Y         = 30;

    private static final int[] TIER_COLORS = {
            0x55FF55,
            0xFFFF55,
            0xFFAA00,
            0xFF5555,
            0xAA0000,
    };

    public static void onTierChanged(Tier next) {
        if (!ModConfig.get().showTierHud) return;

        if (displayedTier == null && next == null) return;

        if (next == null) {
            outStep       = 1f / (FADE_OUT_SECS * 20f);
            outAlpha      = 1f;
            inAlpha       = 0f;
            inStep        = 0f;
            incomingTier  = null;
            fading        = true;
            return;
        }

        float fadeSec = displayedTier != null
                ? Math.max(0.05f, MusicManager.getCrossfadeSecs(displayedTier, next))
                : FADE_OUT_SECS;
        float step    = 1f / (fadeSec * 20f);

        incomingTier  = next;
        outStep       = displayedTier != null ? step : 0f;
        outAlpha      = displayedTier != null ? 1f   : 0f;
        inStep        = step;
        inAlpha       = 0f;
        fading        = true;
    }

    public static void render(DrawContext context, RenderTickCounter counter) {
        if (!ModConfig.get().showTierHud || !ModConfig.get().pvpMusic) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        if (fading) {
            if (outAlpha > 0f) {
                outAlpha = Math.max(0f, outAlpha - outStep);
            }

            inAlpha = Math.min(1f, inAlpha + inStep);

            if (outAlpha <= 0f && inAlpha >= 1f) {
                displayedTier = incomingTier;
                fading        = false;
            } else if (outAlpha <= 0f && incomingTier != null) {
                displayedTier = incomingTier;
            }
        }

        int screenW = context.getScaledWindowWidth();
        int y       = HUD_Y;

        if (displayedTier != null && outAlpha > 0f && !fading) {
            drawTierText(context, displayedTier, 1f, screenW, y);
        }

        if (fading) {
            if (displayedTier != null && outAlpha > 0f) {
                drawTierText(context, displayedTier, outAlpha, screenW, y);
            }
            if (incomingTier != null && inAlpha > 0f) {
                drawTierText(context, incomingTier, inAlpha, screenW, y);
            }
        }

        if (!fading && displayedTier != null) {
            drawTierText(context, displayedTier, 1f, screenW, y);
        }
    }

    private static void drawTierText(DrawContext context, Tier tier, float alpha, int screenW, int y) {
        MinecraftClient client = MinecraftClient.getInstance();
        String label  = "Tier " + (tier.ordinal() + 1);
        int    color  = TIER_COLORS[tier.ordinal()];
        int    a      = (int)(alpha * 255f) & 0xFF;
        int    argb   = (a << 24) | color;
        int    x      = screenW - client.textRenderer.getWidth(label) - 10;
        context.drawTextWithShadow(client.textRenderer, label, x, y, argb);
    }
}
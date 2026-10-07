package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.FrameConfig;
import dev.sirblambo.impactful.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

public class DeathFlashRenderer {

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!DeathFlashController.isActive()) return;

        int frameIndex = DeathFlashController.getCurrentFrame();
        ModConfig config = ModConfig.get();
        FrameConfig frame = config.getDeathFrame(frameIndex);

        if (frame == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        context.getMatrices().pushMatrix();

        int bgArgb = frame.getBackgroundArgbWithOpacity();
        if ((bgArgb >>> 24) != 0) {
            context.fill(0, 0, screenW, screenH, bgArgb);
        }

        int frameTint = frame.getFrameTint();
        if ((frameTint >>> 24) != 0 && FrameManager.hasDeathTexture(frameIndex)) {
            drawOverlay(context, FrameManager.getDeathTexture(frameIndex), frameIndex, screenW, screenH, config, frameTint);
        }

        context.getMatrices().popMatrix();
        DeathFlashController.markRendered();
    }

    private static void drawOverlay(DrawContext context, Identifier texture, int frameIndex,
                                    int screenW, int screenH, ModConfig config, int tint) {
        if (config.stretchToFill) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                    0, 0, 0.0F, 0.0F, screenW, screenH, screenW, screenH, tint);
        } else {
            int nativeW = FrameManager.getDeathTextureWidth(frameIndex);
            int nativeH = FrameManager.getDeathTextureHeight(frameIndex);
            if (nativeW <= 0 || nativeH <= 0) return;

            float guiScale = (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
            float drawW = nativeW / guiScale;
            float drawH = nativeH / guiScale;
            float offsetX = (screenW - drawW) / 2.0F;
            float offsetY = (screenH - drawH) / 2.0F;

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(offsetX, offsetY);
            context.getMatrices().scale(1.0F / guiScale, 1.0F / guiScale);
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                    0, 0, 0.0F, 0.0F, nativeW, nativeH, nativeW, nativeH, tint);
            context.getMatrices().popMatrix();
        }
    }
}
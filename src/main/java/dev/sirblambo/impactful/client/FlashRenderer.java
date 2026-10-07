package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.FrameConfig;
import dev.sirblambo.impactful.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import java.util.UUID;

public class FlashRenderer {

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (FlashController.isActive()) {
            int frameIndex = FlashController.getCurrentFrame();
            ModConfig config = ModConfig.get();
            FrameConfig frame = config.getFrame(frameIndex);

            MinecraftClient client = MinecraftClient.getInstance();
            int screenW = client.getWindow().getScaledWidth();
            int screenH = client.getWindow().getScaledHeight();

            context.getMatrices().pushMatrix();

            int bgArgb = frame.getBackgroundArgbWithOpacity();
            if ((bgArgb >>> 24) != 0) {
                context.fill(0, 0, screenW, screenH, bgArgb);
            }

            int frameTint = frame.getFrameTint();
            if ((frameTint >>> 24) != 0 && FrameManager.hasTexture(frameIndex)) {
                drawOverlay(context, FrameManager.getTexture(frameIndex), frameIndex, screenW, screenH, frameTint);
            }

            if (ModConfig.get().flashHitEntity) {
                UUID victimUuid = FlashController.getVictimUuid();
                if (victimUuid != null && client.world != null) {
                    client.world.getEntitiesByClass(LivingEntity.class,
                            client.player.getBoundingBox().expand(64.0),
                            e -> e.getUuid().equals(victimUuid)
                    ).stream().findFirst().ifPresent(entity -> {
                        int color = FrameManager.getDominantColor(frameIndex);
                        drawEspBox(context, entity, client, color);
                    });
                }
            }

            context.getMatrices().popMatrix();
            FlashController.markRendered();
        }
    }

    private static void drawEspBox(DrawContext context, LivingEntity entity, MinecraftClient client, int color) {
        net.minecraft.util.math.Box box = entity.getBoundingBox();

        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};

        int minSX = Integer.MAX_VALUE, minSY = Integer.MAX_VALUE;
        int maxSX = Integer.MIN_VALUE, maxSY = Integer.MIN_VALUE;

        net.minecraft.client.render.Camera cam = client.gameRenderer.getCamera();
        double camX = cam.getCameraPos().x;
        double camY = cam.getCameraPos().y;
        double camZ = cam.getCameraPos().z;

        for (double wx : xs) {
            for (double wy : ys) {
                for (double wz : zs) {
                    int[] screen = worldToScreen(wx - camX, wy - camY, wz - camZ,
                            client.gameRenderer, client.getWindow());
                    if (screen == null) continue;
                    minSX = Math.min(minSX, screen[0]);
                    minSY = Math.min(minSY, screen[1]);
                    maxSX = Math.max(maxSX, screen[0]);
                    maxSY = Math.max(maxSY, screen[1]);
                }
            }
        }

        if (minSX == Integer.MAX_VALUE) return;

        int thick = 2;
        context.fill(minSX, minSY, maxSX, minSY + thick, color);
        context.fill(minSX, maxSY - thick, maxSX, maxSY, color);
        context.fill(minSX, minSY, minSX + thick, maxSY, color);
        context.fill(maxSX - thick, minSY, maxSX, maxSY, color);
    }

    private static int[] worldToScreen(double x, double y, double z,
                                       net.minecraft.client.render.GameRenderer gameRenderer,
                                       net.minecraft.client.util.Window window) {
        org.joml.Matrix4f proj = gameRenderer.getBasicProjectionMatrix(70.0f);
        org.joml.Vector4f vec = new org.joml.Vector4f((float) x, (float) y, (float) z, 1.0f);
        vec.mul(proj);
        if (vec.w <= 0) return null;
        float nx = vec.x / vec.w;
        float ny = vec.y / vec.w;
        int sx = (int) ((nx + 1.0f) / 2.0f * window.getScaledWidth());
        int sy = (int) ((1.0f - ny) / 2.0f * window.getScaledHeight());
        return new int[]{sx, sy};
    }

    private static void drawOverlay(DrawContext context, Identifier texture, int frameIndex, int screenW, int screenH, int tint) {
        if (ModConfig.get().stretchToFill) {
            context.getMatrices().pushMatrix();
            context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    texture,
                    0, 0,
                    0.0F, 0.0F,
                    screenW, screenH,
                    screenW, screenH,
                    tint
            );
            context.getMatrices().popMatrix();
        } else {
            int nativeW = FrameManager.getTextureWidth(frameIndex);
            int nativeH = FrameManager.getTextureHeight(frameIndex);
            if (nativeW <= 0 || nativeH <= 0) return;

            float guiScale = (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
            float drawW = nativeW / guiScale;
            float drawH = nativeH / guiScale;
            float offsetX = (screenW - drawW) / 2.0F;
            float offsetY = (screenH - drawH) / 2.0F;

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(offsetX, offsetY);
            context.getMatrices().scale(1.0F / guiScale, 1.0F / guiScale);
            context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    texture,
                    0, 0,
                    0.0F, 0.0F,
                    nativeW, nativeH,
                    nativeW, nativeH,
                    tint
            );
            context.getMatrices().popMatrix();
        }
    }
}
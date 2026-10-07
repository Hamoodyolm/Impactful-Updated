package dev.sirblambo.impactful.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.sirblambo.impactful.config.ModConfig;
import net.minecraft.client.MinecraftClient;

public class FrozenView {
    private static GpuTexture snapshot = null;
    private static boolean hasSnapshot = false;

    private static boolean shouldFreeze() {
        return FlashController.isActive() && ModConfig.get().freezeCamera;
    }

    public static void requestCapture() {
        hasSnapshot = false;
    }

    public static boolean drawFrozen() {
        if (!shouldFreeze()) {
            hasSnapshot = false;
            return false;
        }
        if (!hasSnapshot) return false;
        GpuTexture color = MinecraftClient.getInstance().getFramebuffer().getColorAttachment();
        if (color == null || !matches(color)) {
            hasSnapshot = false;
            return false;
        }
        RenderSystem.getDevice().createCommandEncoder()
                .copyTextureToTexture(snapshot, color, 0, 0, 0, 0, 0, color.getWidth(0), color.getHeight(0));
        return true;
    }

    public static void captureIfNeeded() {
        if (hasSnapshot || !shouldFreeze()) return;
        GpuTexture color = MinecraftClient.getInstance().getFramebuffer().getColorAttachment();
        if (color == null) return;
        if (!matches(color)) {
            if (snapshot != null && !snapshot.isClosed()) snapshot.close();
            snapshot = RenderSystem.getDevice().createTexture(() -> "Impactful Frozen View",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC,
                    color.getFormat(), color.getWidth(0), color.getHeight(0), 1, 1);
        }
        RenderSystem.getDevice().createCommandEncoder()
                .copyTextureToTexture(color, snapshot, 0, 0, 0, 0, 0, color.getWidth(0), color.getHeight(0));
        hasSnapshot = true;
    }

    private static boolean matches(GpuTexture color) {
        return snapshot != null && !snapshot.isClosed()
                && snapshot.getFormat() == color.getFormat()
                && snapshot.getWidth(0) == color.getWidth(0)
                && snapshot.getHeight(0) == color.getHeight(0);
    }
}

package dev.sirblambo.impactful.client;
import dev.sirblambo.impactful.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import java.util.UUID;

public class FlashController {
    private static int currentFrame = -1;
    private static long frameStart = 0L;
    private static boolean currentFrameShown = false;
    private static UUID victimUuid = null;
    private static float frozenYaw = 0f;
    private static float frozenPitch = 0f;

    public static void trigger(UUID uuid) {
        ModConfig config = ModConfig.get();
        if (!config.impactFramesEnabled) return;
        int frameCount = FrameManager.getFrameCount();
        if (frameCount != 0 && !config.frames.isEmpty()) {
            currentFrame = 0;
            currentFrameShown = false;
            frameStart = System.currentTimeMillis();
            victimUuid = uuid;
            FrozenView.requestCapture();
            SoundManager.playIfEnabled();
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                frozenYaw = client.player.getYaw();
                frozenPitch = client.player.getPitch();
            }
            HitColorRenderer.setVictimGlowing(uuid, true);
        }
    }

    public static void update() {
        if (currentFrame >= 0) {
            ModConfig config = ModConfig.get();
            int frameCount = FrameManager.getFrameCount();
            int maxFrames = Math.min(frameCount, config.frames.size());
            if (currentFrame >= maxFrames) {
                reset();
            } else if (currentFrameShown) {
                long elapsed = System.currentTimeMillis() - frameStart;
                int duration = config.getFrame(currentFrame).durationMs;
                if (elapsed >= (long) duration) {
                    frameStart = System.currentTimeMillis();
                    currentFrameShown = false;
                    if (++currentFrame >= maxFrames) {
                        reset();
                    }
                }
            }
        }
    }

    public static void markRendered() {
        currentFrameShown = true;
    }

    private static void reset() {
        HitColorRenderer.onReset();
        currentFrame = -1;
        currentFrameShown = false;
        victimUuid = null;
        frozenYaw = 0f;
        frozenPitch = 0f;
    }

    public static boolean isActive() {
        return currentFrame >= 0;
    }

    public static int getCurrentFrame() {
        return currentFrame;
    }

    public static UUID getVictimUuid() {
        return victimUuid;
    }

    public static float getFrozenYaw() { return frozenYaw; }
    public static float getFrozenPitch() { return frozenPitch; }
}
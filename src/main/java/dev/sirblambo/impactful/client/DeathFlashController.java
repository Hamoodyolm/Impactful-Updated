package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.ModConfig;

public class DeathFlashController {
    private static int currentFrame = -1;
    private static long frameStart = 0L;
    private static boolean currentFrameShown = false;

    public static void trigger() {
        if (!ModConfig.get().deathFramesEnabled) return;
        ModConfig config = ModConfig.get();
        if (config.deathFrames.isEmpty()) return;
        currentFrame = 0;
        currentFrameShown = false;
        frameStart = System.currentTimeMillis();
    }

    public static void update() {
        if (currentFrame >= 0) {
            ModConfig config = ModConfig.get();
            int maxFrames = config.deathFrames.size();
            if (currentFrame >= maxFrames) {
                reset();
                return;
            }
            if (currentFrameShown) {
                long elapsed = System.currentTimeMillis() - frameStart;
                int duration = config.getDeathFrame(currentFrame).durationMs;
                if (elapsed >= duration) {
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
        currentFrame = -1;
        currentFrameShown = false;
    }

    public static boolean isActive() { return currentFrame >= 0; }
    public static int getCurrentFrame() { return currentFrame; }
}
package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.ModConfig;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PauseMusicDucker {

    private static final float DUCK_VOLUME  = 0.3f;
    private static final float DUCK_PITCH   = 0.75f;
    private static final long  EASE_MS      = 500L;
    private static final int   STEPS        = 60;
    private static final long  STEP_MS      = EASE_MS / STEPS;

    private static boolean     ducked       = false;
    private static ScheduledExecutorService executor;
    private static ScheduledFuture<?>        currentTask;

    private static volatile float volumeMult = 1.0f;
    private static volatile float pitchMult  = 1.0f;

    public static void initialize() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PauseMusicDucker");
            t.setDaemon(true);
            return t;
        });
    }

    public static void onPause() {
        if (!ModConfig.get().pauseMusicDuck) return;
        if (ducked) return;
        ducked = true;
        cancelCurrent();
        currentTask = executor.schedule(() -> ease(1.0f, DUCK_VOLUME, 1.0f, DUCK_PITCH), 0, TimeUnit.MILLISECONDS);
    }

    public static void onResume() {
        if (!ModConfig.get().pauseMusicDuck) return;
        if (!ducked) return;
        ducked = false;
        cancelCurrent();
        currentTask = executor.schedule(() -> ease(DUCK_VOLUME, 1.0f, DUCK_PITCH, 1.0f), 0, TimeUnit.MILLISECONDS);
    }

    private static void ease(float fromVol, float toVol, float fromPitch, float toPitch) {
        for (int i = 0; i <= STEPS; i++) {
            float t = (float) i / STEPS;
            volumeMult = fromVol + (toVol - fromVol) * t;
            pitchMult  = fromPitch + (toPitch - fromPitch) * t;
            
            MusicManager.updateActiveVolumes();
            try { Thread.sleep(STEP_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        volumeMult = toVol;
        pitchMult  = toPitch;
        MusicManager.updateActiveVolumes();
    }

    private static void cancelCurrent() {
        if (currentTask != null && !currentTask.isDone()) currentTask.cancel(true);
    }

    public static void shutdown() {
        if (executor != null) executor.shutdownNow();
    }

    public static float getVolumeMult() { return volumeMult; }
    public static float getPitchMult()  { return pitchMult; }
}
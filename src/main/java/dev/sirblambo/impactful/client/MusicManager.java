package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;

import javax.sound.sampled.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MusicManager {

    private static final Path MUSIC_DIR =
            FabricLoader.getInstance().getGameDir().resolve("Impactful").resolve("musicalz");

    private static final long FADE_STEP_MS = 10L;
    private static final long CLIP_START_TIMEOUT_MS = 400L;
    private static final float FADE_OUT_FLOOR_DB = 40f;
    private static final float FADE_OUT_SECS = 0.25f;
    private static final float PREVIEW_FADE_OUT_SECS = 1.0f;
    private static final long TEST_SEGMENT_MS = 5000L;
    private static final float PENDING_CHECK_SLACK_SECS = 0.05f;

    private static final Map<Tier, Clip>   clips       = new EnumMap<>(Tier.class);
    private static final Map<Tier, Float>  fadeFactors = new ConcurrentHashMap<>();
    private static final Map<Tier, String> loadedNames = new EnumMap<>(Tier.class);

    private static Tier currentTier   = null;
    private static Tier pendingTier   = null;
    private static boolean initialized = false;
    private static ExecutorService executor;

    private static ExecutorService previewExecutor;
    private static Future<?> previewTask;
    private static volatile boolean previewActive = false;
    private static volatile int previewGen = 0;

    public static void initialize() {
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MusicManager");
            t.setDaemon(true);
            return t;
        });

        previewExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MusicManagerPreview");
            t.setDaemon(true);
            return t;
        });

        try { Files.createDirectories(MUSIC_DIR); }
        catch (Exception e) { System.err.println("[Impactful] Could not create musicalz dir: " + e.getMessage()); }

        loadAll();
        initialized = true;
    }

    public static void reload() {
        stopPreview();
        stopAll();
        for (Clip c : clips.values()) { if (c != null) c.close(); }
        clips.clear();
        fadeFactors.clear();
        loadedNames.clear();
        currentTier = null;
        pendingTier = null;
        loadAll();
    }

    private static void loadAll() {
        clips.clear();
        fadeFactors.clear();
        loadedNames.clear();
        Tier[] tiers = Tier.values();

        java.util.TreeMap<Integer, Path> found = new java.util.TreeMap<>();
        try (java.util.stream.Stream<Path> stream = Files.list(MUSIC_DIR)) {
            stream.filter(p -> { String n = p.getFileName().toString().toLowerCase(); return n.endsWith(".ogg") || n.endsWith(".wav"); })
                  .sorted()
                  .forEach(p -> {
                      java.util.regex.Matcher m =
                          java.util.regex.Pattern.compile("(\\d+)").matcher(p.getFileName().toString());
                      if (m.find()) {
                          int num = Integer.parseInt(m.group(1));
                          if (num >= 1 && num <= tiers.length && !found.containsKey(num)) {
                              found.put(num, p);
                          }
                      }
                  });
        } catch (Exception e) {
            System.err.println("[Impactful] Failed to scan musicalz dir: " + e.getMessage());
        }

        for (Map.Entry<Integer, Path> entry : found.entrySet()) {
            int tierIndex = entry.getKey() - 1;
            Tier tier = tiers[tierIndex];
            Clip clip = loadClip(entry.getValue());
            if (clip != null) {
                clips.put(tier, clip);
                fadeFactors.put(tier, 0f);
                loadedNames.put(tier, entry.getValue().getFileName().toString());
            }
        }
    }

    public static String getLoadedFileName(int index) {
        Tier[] tiers = Tier.values();
        if (index < 0 || index >= tiers.length) return "(tier " + (index + 1) + ")";
        String name = loadedNames.get(tiers[index]);
        return name != null ? name : "(tier " + (index + 1) + ")";
    }

    private static Clip loadClip(Path path) {
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(path.toFile());
            AudioFormat base     = ais.getFormat();
            AudioFormat decoded  = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    base.getSampleRate(), 16,
                    base.getChannels(), base.getChannels() * 2,
                    base.getSampleRate(), false);
            AudioInputStream pcm  = AudioSystem.getAudioInputStream(decoded, ais);
            DataLine.Info    info = new DataLine.Info(Clip.class, decoded);
            Clip clip = (Clip) AudioSystem.getLine(info);
            clip.open(pcm);
            setGain(clip, 0f);
            return clip;
        } catch (Throwable e) {
            System.err.println("[Impactful] Failed to load " + path.getFileName() + ": " + e);
            return null;
        }
    }

    public static int getTierCount()              { return Tier.values().length; }
    public static String getTierName(int index)   { return index >= 0 && index < Tier.values().length ? Tier.values()[index].name().toLowerCase() : "unknown"; }
    public static int getCurrentTierIndex()       { return currentTier != null ? currentTier.ordinal() : -1; }

    public static void setPlayerNearby(boolean nearby) {
        if (!initialized || !ModConfig.get().pvpMusic) return;
        ModConfig cfg = ModConfig.get();

        if (nearby && currentTier == null && cfg.tierEnabled[Tier.CALM.ordinal()]) {
            crossfadeTo(Tier.CALM);
        } else if (!nearby && currentTier == Tier.CALM) {
            fadeOutAll();
        }
    }

    static float getCrossfadeSecs(Tier from, Tier to) {
        ModConfig cfg = ModConfig.get();
        float[] cf = cfg.tierCrossfades;
        float secs = 1.0f;
        if (from == Tier.CALM     && to == Tier.TENSION)  secs = cf[0];
        else if (from == Tier.TENSION  && to == Tier.CALM)     secs = cf[1];
        else if (from == Tier.TENSION  && to == Tier.DANGER)   secs = cf[2];
        else if (from == Tier.DANGER   && to == Tier.TENSION)  secs = cf[3];
        else if (from == Tier.DANGER   && to == Tier.COMBAT)   secs = cf[4];
        else if (from == Tier.COMBAT   && to == Tier.DANGER)   secs = cf[5];
        else if (from == Tier.COMBAT   && to == Tier.CRITICAL) secs = cf[6];
        else if (from == Tier.CRITICAL && to == Tier.COMBAT)   secs = cf[7];
        return Math.max(1.0f, secs);
    }

    public static void requestTier(Tier next) {
        if (!initialized) return;
        if (!ModConfig.get().pvpMusicFinishTrack || currentTier == null || !hasClip(currentTier)) {
            pendingTier = null;
            crossfadeTo(next);
            return;
        }
        pendingTier = next == currentTier ? null : next;
    }

    public static void tickPendingTier() {
        Tier pending = pendingTier;
        if (pending == null) return;
        Tier current = currentTier;
        if (current == null) {
            pendingTier = null;
            return;
        }
        Clip clip = clips.get(current);
        if (ModConfig.get().pvpMusicFinishTrack && clip != null && clip.isRunning() && clip.getFrameLength() > 0) {
            long length    = clip.getFrameLength();
            long remaining = length - (clip.getLongFramePosition() % length);
            float rate     = clip.getFormat().getFrameRate() * PauseMusicDucker.getPitchMult();
            float fadeSecs = getCrossfadeSecs(current, pending);
            if (remaining > (fadeSecs + PENDING_CHECK_SLACK_SECS) * rate) return;
        }
        pendingTier = null;
        crossfadeTo(pending);
    }

    public static void crossfadeTo(Tier next) {
        if (!initialized) return;

        pendingTier   = null;
        Tier previous = currentTier;
        currentTier   = next;
        TierHudRenderer.onTierChanged(next);

        float fadeSecs = previous != null ? getCrossfadeSecs(previous, next) : 0f;

        executor.submit(() -> {
            Clip incoming = clips.get(next);
            if (incoming == null) return;

            applyFade(next, incoming, 0f);

            int startFrame = incoming.getFramePosition();
            if (!incoming.isRunning()) {
                incoming.setFramePosition(0);
                startFrame = 0;
                incoming.loop(Clip.LOOP_CONTINUOUSLY);
            }

            Clip outClip = previous != null ? clips.get(previous) : null;

            try {
                awaitClipStart(incoming, startFrame);

                if (previous == null || fadeSecs <= 0f) {
                    applyFade(next, incoming, 1f);
                } else {
                    runCrossfade(previous, outClip, next, incoming, fadeSecs);
                }
            } catch (InterruptedException ignored) {
                return;
            }

            if (outClip != null) { outClip.stop(); applyFade(previous, outClip, 0f); }
        });
    }

    public static void fadeOutAll() {
        if (!initialized) return;
        Tier wasPlaying = currentTier;
        currentTier = null;
        pendingTier = null;
        TierHudRenderer.onTierChanged(null);

        if (wasPlaying == null) return;

        executor.submit(() -> {
            Clip out = clips.get(wasPlaying);
            if (out == null || !out.isRunning()) return;

            try {
                runFadeOut(wasPlaying, out, FADE_OUT_SECS);
            } catch (InterruptedException ignored) {
                return;
            }
            out.stop(); applyFade(wasPlaying, out, 0f);
        });
    }

    public static void stopAll() {
        currentTier = null;
        pendingTier = null;
        for (Map.Entry<Tier, Clip> entry : clips.entrySet()) {
            if (entry.getValue() != null) {
                entry.getValue().stop();
                applyFade(entry.getKey(), entry.getValue(), 0f);
            }
        }
    }

    public static void shutdown() {
        stopPreview();
        stopAll();
        for (Clip clip : clips.values()) { if (clip != null) clip.close(); }
        clips.clear();
        fadeFactors.clear();
        if (executor != null) executor.shutdownNow();
        if (previewExecutor != null) previewExecutor.shutdownNow();
        initialized = false;
    }

    private static void awaitClipStart(Clip clip, int startFrame) throws InterruptedException {
        if (clip == null) return;
        long deadline = System.currentTimeMillis() + CLIP_START_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (clip.isRunning() && clip.getFramePosition() != startFrame) return;
            Thread.sleep(2L);
        }
    }

    private static void runCrossfade(Tier from, Clip outClip, Tier to, Clip inClip, float fadeSecs)
            throws InterruptedException {
        long durationMs = (long) (fadeSecs * 1000f);
        long start = System.nanoTime();

        while (true) {
            long elapsed = (System.nanoTime() - start) / 1_000_000L;
            float t = durationMs <= 0 ? 1f : Math.min(1f, (float) elapsed / durationMs);
            double angle = t * (Math.PI / 2.0);

            if (outClip != null) applyFade(from, outClip, (float) Math.cos(angle));
            applyFade(to, inClip, (float) Math.sin(angle));

            if (t >= 1f) return;
            Thread.sleep(FADE_STEP_MS);
        }
    }

    private static void runFadeOut(Tier tier, Clip clip, float fadeSecs) throws InterruptedException {
        long durationMs = (long) (fadeSecs * 1000f);
        long start = System.nanoTime();

        while (true) {
            long elapsed = (System.nanoTime() - start) / 1_000_000L;
            float t = durationMs <= 0 ? 1f : Math.min(1f, (float) elapsed / durationMs);

            applyFade(tier, clip, t >= 1f
                    ? 0f
                    : (float) Math.pow(10.0, -(FADE_OUT_FLOOR_DB * t) / 20.0));

            if (t >= 1f) return;
            Thread.sleep(FADE_STEP_MS);
        }
    }

    private static float configGain(Tier tier) {
        ModConfig cfg = ModConfig.get();
        return cfg.tierVolumes[tier.ordinal()] * cfg.musicMasterVolume;
    }

    private static void applyFade(Tier tier, Clip clip, float factor) {
        if (tier != null) fadeFactors.put(tier, factor);
        if (clip == null) return;
        setGain(clip, (tier != null ? configGain(tier) : 1f) * factor);
    }

    private static void setGain(Clip clip, float gain) {
        if (clip == null) return;
        try {
            FloatControl ctrl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float blendedGain = gain * PauseMusicDucker.getVolumeMult();
            float db = blendedGain <= 0.0001f ? ctrl.getMinimum() : (float)(20.0 * Math.log10(blendedGain));
            ctrl.setValue(Math.max(ctrl.getMinimum(), Math.min(ctrl.getMaximum(), db)));
        } catch (Exception ignored) {}
    }

    public static void updateActiveVolumes() {
        for (Map.Entry<Tier, Clip> entry : clips.entrySet()) {
            Tier tier = entry.getKey();
            Clip clip = entry.getValue();
            if (clip == null || !clip.isRunning()) continue;

            setGain(clip, configGain(tier) * fadeFactors.getOrDefault(tier, 0f));

            if (tier == currentTier) {
                try {
                    FloatControl ctrl = (FloatControl) clip.getControl(FloatControl.Type.SAMPLE_RATE);
                    float baseRate = clip.getFormat().getSampleRate();
                    ctrl.setValue(Math.max(ctrl.getMinimum(), Math.min(ctrl.getMaximum(), baseRate * PauseMusicDucker.getPitchMult())));
                } catch (Exception ignored) {}
            }
        }
    }

    public static boolean isPreviewing() { return previewActive; }

    public static boolean hasClip(Tier tier) { return tier != null && clips.get(tier) != null; }

    public static void startPreview(Tier tier) {
        if (!initialized || !hasClip(tier)) return;

        stopPreview();
        stopAll();

        previewActive = true;
        final int gen = ++previewGen;

        currentTier = tier;
        TierHudRenderer.onTierChanged(tier);

        previewTask = previewExecutor.submit(() -> {
            Thread.interrupted();
            if (gen != previewGen) return;
            startClipAt(tier, clips.get(tier), 1f);
        });
    }

    public static void runTierTest(Tier from, Tier to) {
        if (!initialized || !hasClip(from) || !hasClip(to)) return;

        stopPreview();
        stopAll();

        previewActive = true;
        final int gen = ++previewGen;
        final float fadeSecs = getCrossfadeSecs(from, to);

        previewTask = previewExecutor.submit(() -> {
            Thread.interrupted();
            try {
                if (gen != previewGen) return;
                currentTier = from;
                TierHudRenderer.onTierChanged(from);

                Clip fromClip = clips.get(from);
                awaitClipStart(fromClip, startClipAt(from, fromClip, 1f));

                Thread.sleep(TEST_SEGMENT_MS);
                if (gen != previewGen) return;

                previewCrossfade(from, to, fadeSecs, gen);
                if (gen != previewGen) return;

                Thread.sleep(TEST_SEGMENT_MS);
                if (gen != previewGen) return;

                previewFadeOut(to, gen);
            } catch (InterruptedException ignored) {
            } finally {
                if (gen == previewGen) previewActive = false;
            }
        });
    }

    public static void stopPreview() {
        Future<?> task = previewTask;
        previewTask = null;
        previewGen++;
        if (task != null) task.cancel(true);

        if (!previewActive) return;
        previewActive = false;
        stopAll();
        TierHudRenderer.onTierChanged(null);
    }

    private static int startClipAt(Tier tier, Clip clip, float factor) {
        if (clip == null) return 0;
        int length = clip.getFrameLength();
        int start  = length > 0 ? (int) (Math.random() * (length * 0.75)) : 0;
        clip.stop();
        clip.setFramePosition(start);
        applyFade(tier, clip, factor);
        clip.loop(Clip.LOOP_CONTINUOUSLY);
        return start;
    }

    private static void previewCrossfade(Tier from, Tier to, float fadeSecs, int gen)
            throws InterruptedException {
        Clip outClip = clips.get(from);
        Clip inClip  = clips.get(to);
        if (inClip == null) return;

        currentTier = to;
        TierHudRenderer.onTierChanged(to);

        int startFrame = startClipAt(to, inClip, 0f);
        awaitClipStart(inClip, startFrame);
        if (gen != previewGen) return;

        runCrossfade(from, outClip, to, inClip, fadeSecs);
        if (gen != previewGen) return;

        if (outClip != null) { outClip.stop(); applyFade(from, outClip, 0f); }
    }

    private static void previewFadeOut(Tier tier, int gen) throws InterruptedException {
        Clip clip = clips.get(tier);
        if (clip == null) return;

        runFadeOut(tier, clip, PREVIEW_FADE_OUT_SECS);
        if (gen != previewGen) return;

        clip.stop();
        applyFade(tier, clip, 0f);
        currentTier = null;
        TierHudRenderer.onTierChanged(null);
    }

    public static Clip getClip(Tier tier) { return clips.get(tier); }
    public static void setClipVolumePublic(Clip clip, float gain) { setGain(clip, gain); }
    public static void setClipRatePublic(Clip clip, float rate) {
        if (clip == null) return;
        try {
            FloatControl ctrl = (FloatControl) clip.getControl(FloatControl.Type.SAMPLE_RATE);
            float base = clip.getFormat().getSampleRate();
            ctrl.setValue(Math.max(ctrl.getMinimum(), Math.min(ctrl.getMaximum(), base * rate)));
        } catch (Exception ignored) {}
    }

    public static Tier getCurrentTier()  { return currentTier; }
    public static Path getMusicDir()     { return MUSIC_DIR; }
}
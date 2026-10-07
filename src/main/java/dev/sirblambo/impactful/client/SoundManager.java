package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;

import javax.sound.sampled.*;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SoundManager {

    private static Path getSoundDir() {
        return FabricLoader.getInstance().getGameDir().resolve("Impactful").resolve("death_soundz");
    }
    private static final String QUIT_SOUND = "special_log.wav";
    private static final int CLIP_POOL_SIZE = 4;
    private static volatile File soundFile = null;
    private static boolean warnedMultiple = false;

    private static final ScheduledExecutorService AUDIO = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Impactful-KillSound");
        t.setDaemon(true);
        return t;
    });
    private static final List<Clip> pool = new ArrayList<>();
    private static File pooledFile = null;
    private static long pooledModified = 0L;
    private static int nextClip = 0;

    public static void initialize() {
        try {
            Files.createDirectories(getSoundDir());
        } catch (Exception e) {
            System.err.println("[Impactful] Could not create death_soundz dir: " + e.getMessage());
        }

        for (String name : new String[]{ "deathsound.wav", QUIT_SOUND }) {
            Path dest = getSoundDir().resolve(name);
            if (Files.exists(dest)) continue;
            try (InputStream is = SoundManager.class.getResourceAsStream("/assets/impactful/defaults/" + name)) {
                if (is != null) {
                    Files.copy(is, dest);
                }
            } catch (Exception e) {
                System.err.println("[Impactful] Could not copy " + name + ": " + e.getMessage());
            }
        }

        scanSounds();
    }

    public static void scanSounds() {
        soundFile = null;
        warnedMultiple = false;
        AUDIO.execute(SoundManager::closePool);

        try {
            List<File> found = new ArrayList<>();
            File dir = getSoundDir().toFile();
            if (dir.exists()) {
                for (File f : dir.listFiles()) {
                    String name = f.getName().toLowerCase();
                    if (name.equals(QUIT_SOUND)) continue;
                    if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg")) {
                        found.add(f);
                    }
                }
            }

            if (found.size() == 1) {
                soundFile = found.get(0);
            } else if (found.size() > 1) {
                soundFile = found.get(0);
                warnedMultiple = true;
            }
        } catch (Exception e) {
            System.err.println("[Impactful] Error scanning death_soundz: " + e.getMessage());
        }
    }

    public static boolean shouldWarnMultiple() {
        return warnedMultiple;
    }

    public static void playIfEnabled() {
        if (!ModConfig.get().killSound) return;
        if (soundFile == null) return;

        File file = soundFile;
        int delay = Math.max(0, ModConfig.get().killSoundDelayMs);
        AUDIO.schedule(() -> playPooled(file), delay, TimeUnit.MILLISECONDS);
    }

    private static void playPooled(File file) {
        try {
            if (!file.equals(pooledFile) || file.lastModified() != pooledModified) loadPool(file);
            if (pool.isEmpty()) return;
            Clip clip = pool.get(nextClip);
            nextClip = (nextClip + 1) % pool.size();
            clip.stop();
            clip.flush();
            clip.setFramePosition(0);
            clip.start();
        } catch (Throwable e) {
            System.err.println("[Impactful] Sound playback error: " + e);
        }
    }

    private static void loadPool(File file) throws Exception {
        closePool();
        pooledFile = file;
        pooledModified = file.lastModified();
        if (file.getName().toLowerCase().endsWith(".mp3")) {
            System.err.println("[Impactful] MP3 kill sounds aren't supported - please use WAV or OGG.");
            return;
        }

        AudioFormat decoded;
        byte[] data;
        try (AudioInputStream in = AudioSystem.getAudioInputStream(file)) {
            AudioFormat base = in.getFormat();
            decoded = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    base.getSampleRate(), 16,
                    base.getChannels(), base.getChannels() * 2,
                    base.getSampleRate(), false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(decoded, in)) {
                data = pcm.readAllBytes();
            }
        }

        DataLine.Info info = new DataLine.Info(Clip.class, decoded);
        for (int i = 0; i < CLIP_POOL_SIZE; i++) {
            Clip clip = (Clip) AudioSystem.getLine(info);
            clip.open(decoded, data, 0, data.length);
            pool.add(clip);
        }
    }

    private static void closePool() {
        for (Clip clip : pool) clip.close();
        pool.clear();
        pooledFile = null;
        nextClip = 0;
    }

    public static Path getQuitSoundPath() {
        return getSoundDir().resolve(QUIT_SOUND);
    }

    public static void playQuitSound() {
        File file = getQuitSoundPath().toFile();
        if (!file.exists()) return;

        Thread thread = new Thread(() -> {
            try {
                playFile(file);
            } catch (Throwable e) {
                System.err.println("[Impactful] Quit sound playback error: " + e);
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static void playFile(File file) throws Exception {
        String name = file.getName().toLowerCase();

        if (name.endsWith(".mp3")) {
            System.err.println("[Impactful] MP3 kill sounds aren't supported - please use WAV or OGG.");
            return;
        }

        AudioInputStream in   = AudioSystem.getAudioInputStream(file);
        AudioFormat      base = in.getFormat();
        AudioFormat      decoded = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                base.getSampleRate(), 16,
                base.getChannels(), base.getChannels() * 2,
                base.getSampleRate(), false);
        AudioInputStream pcm  = AudioSystem.getAudioInputStream(decoded, in);
        DataLine.Info    info = new DataLine.Info(Clip.class, decoded);
        Clip clip = (Clip) AudioSystem.getLine(info);
        clip.open(pcm);
        clip.start();
        Thread.sleep(clip.getMicrosecondLength() / 1000);
        clip.close();
    }
}
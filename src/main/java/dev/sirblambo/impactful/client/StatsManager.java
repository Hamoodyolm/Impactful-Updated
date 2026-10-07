package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;

import javax.sound.sampled.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class StatsManager {

    private static final Path SOUND_DIR =
            FabricLoader.getInstance().getGameDir().resolve("Impactful").resolve("Win_Lose_soundz");

    private static final long[] REVEAL_MS     = { 0, 0, 1740, 2170, 2600, 3020, 3500, 3500 };
    private static final long   BOARD_HOLD_MS = 5000;

    private static final String[] LEAVE_CALLOUT = { "How was that difficult,", "brother YOU LEFT!", "Shame." };
    private static final String[] RUN_CALLOUT   = { "Seriously?", "Running from a fight?", "Why??" };

    private static volatile String[] boardLines   = null;
    private static volatile long     boardStartMs = -1;
    private static volatile long     boardEndMs   = 0;

    public static void initialize() {
        try { Files.createDirectories(SOUND_DIR); }
        catch (Exception e) { System.err.println("[Impactful] Could not create Win_Lose_soundz dir: " + e.getMessage()); }
        copyDefaults();
    }

    private static void copyDefaults() {
        for (String name : new String[]{ "victoryscreenstats.wav", "losescreenstats.wav" }) {
            Path dest = SOUND_DIR.resolve(name);
            if (Files.exists(dest)) continue;
            try (InputStream is = StatsManager.class.getResourceAsStream("/assets/impactful/defaults/" + name)) {
                if (is != null) Files.copy(is, dest);
            } catch (Exception e) {
                System.err.println("[Impactful] Could not copy " + name + ": " + e.getMessage());
            }
        }
    }

    public static void triggerVictory() {
        if (!BattleTracker.isInBattle()) return;
        DebugBattleStats.captureFinal(true);
        if (!ModConfig.get().showStatsAfterFight || isBoardActive()) {
            BattleTracker.reset();
            return;
        }

        String time     = BattleTracker.getFormattedTime();
        float  dealt    = BattleTracker.getDamageDealt();
        float  taken    = BattleTracker.getDamageTaken();
        int    consumes = BattleTracker.getTotalConsumablesDisplay();
        MatchGrader.GradingResult grade = MatchGrader.evaluate();
        BattleTracker.reset();

        showBoard(SOUND_DIR.resolve("victoryscreenstats.wav"), true, time, dealt, taken, consumes, gradeText(grade.grade), difficultyText(grade.difficulty));
    }

    public static void triggerDefeat() {
        if (!BattleTracker.isInBattle()) return;
        DebugBattleStats.captureFinal(false);
        if (!ModConfig.get().showStatsAfterFight || isBoardActive()) {
            BattleTracker.reset();
            return;
        }

        String time     = BattleTracker.getFormattedTime();
        float  dealt    = BattleTracker.getDamageDealt();
        float  taken    = BattleTracker.getDamageTaken();
        int    consumes = BattleTracker.getTotalConsumablesDisplay();
        MatchGrader.GradingResult grade = MatchGrader.evaluate();
        BattleTracker.reset();

        showBoard(SOUND_DIR.resolve("losescreenstats.wav"), false, time, dealt, taken, consumes, gradeText(grade.grade), difficultyText(grade.difficulty));
    }

    public static void triggerQuit(boolean ranViaGamemode) {
        DebugBattleStats.captureQuit();
        BattleTracker.reset();
        KillDetector.reset();
        IncomingDamageEstimator.reset();
        MusicTracker.reset();
        if (!ModConfig.get().showStatsAfterFight || isBoardActive()) {
            SoundManager.playQuitSound();
            return;
        }

        String[] callout = ranViaGamemode ? RUN_CALLOUT : LEAVE_CALLOUT;
        String[] chunks  = new String[callout.length];
        for (int i = 0; i < callout.length; i++) chunks[i] = "\u00a7c\u00a7l" + callout[i];
        showBoard(SoundManager.getQuitSoundPath(), false, "-9999", -9999f, -9999f, -9999, "F", chunks);
    }

    private static String gradeText(String grade) {
        if (!grade.equals("F")) return grade;
        int roll = ThreadLocalRandom.current().nextInt(500);
        if (roll == 0) return "You suck. F";
        if (roll == 1) return "Not even Wemmbu could fix those skills.";
        return grade;
    }

    private static String difficultyText(MatchGrader.Difficulty difficulty) {
        return difficulty.color + "\u00a7l" + difficulty.label;
    }

    private static void showBoard(Path soundFile, boolean victory,
                                  String time, float dealt, float taken,
                                  int consumes, String grade, String... difficultyLines) {
        String gradeColor = victory ? "\u00a72" : "\u00a73";
        List<String> lines = new ArrayList<>(List.of(
                victory
                        ? "\u00a7f--{\u00a7k##\u00a7r\u00a72\u00a7lVICTORY\u00a7r\u00a7f\u00a7k##\u00a7r}--"
                        : "\u00a7f--{\u00a7k##\u00a7r\u00a74\u00a7lDEFEAT\u00a7r\u00a7f\u00a7k##\u00a7r}--",
                "\u00a76Game Stats:",
                "\u00a7eTime Of Battle\u00a7f: \u00a72\u00a7l" + time,
                "\u00a7eDamage Taken\u00a7f: \u00a72\u00a7l" + (int) taken,
                "\u00a7eDamage Dealt\u00a7f: \u00a72\u00a7l" + (int) dealt,
                "\u00a7eConsumables Used\u00a7f: \u00a72\u00a7l" + consumes,
                "\u00a7f--{\u00a7k##\u00a7r| " + gradeColor + "\u00a7lGrade: " + grade + " \u00a7f|\u00a7k##\u00a7r}---"
        ));
        if (difficultyLines.length == 1) {
            lines.add("\u00a77Grading Difficulty\u00a7f: " + difficultyLines[0]);
        } else {
            lines.add("\u00a77Grading Difficulty\u00a7f:");
            lines.addAll(List.of(difficultyLines));
        }
        boardEndMs   = REVEAL_MS[REVEAL_MS.length - 1] + BOARD_HOLD_MS;
        boardStartMs = -1;
        boardLines   = lines.toArray(new String[0]);

        Thread t = new Thread(() -> {
            Clip clip = loadClip(soundFile);
            try {
                if (clip != null) {
                    boardEndMs = Math.max(boardEndMs, clip.getMicrosecondLength() / 1000);
                    clip.start();
                }
                boardStartMs = System.currentTimeMillis();
                if (clip != null) Thread.sleep(clip.getMicrosecondLength() / 1000);
            } catch (Exception e) {
                System.err.println("[Impactful] Stats sound error: " + e.getMessage());
            } finally {
                if (boardStartMs < 0) boardStartMs = System.currentTimeMillis();
                if (clip != null) clip.close();
            }
        });
        t.setDaemon(true);
        t.start();
    }

    public static boolean isBoardActive() {
        if (boardLines == null) return false;
        long start = boardStartMs;
        return start < 0 || System.currentTimeMillis() - start < boardEndMs;
    }

    public static List<String> getAllBoardLines() {
        return isBoardActive() ? List.of(boardLines) : null;
    }

    public static List<String> getRevealedBoardLines() {
        if (!isBoardActive()) return null;
        String[] lines = boardLines;
        long start   = boardStartMs;
        long elapsed = start < 0 ? 0 : System.currentTimeMillis() - start;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            long revealAt = REVEAL_MS[Math.min(i, REVEAL_MS.length - 1)];
            if (revealAt == 0 || (start >= 0 && elapsed >= revealAt)) out.add(lines[i]);
        }
        return out;
    }

    private static Clip loadClip(Path path) {
        if (!Files.exists(path)) return null;
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(path.toFile());
            AudioFormat base     = ais.getFormat();
            AudioFormat decoded  = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    base.getSampleRate(), 16,
                    base.getChannels(), base.getChannels() * 2,
                    base.getSampleRate(), false);
            AudioInputStream pcm = AudioSystem.getAudioInputStream(decoded, ais);
            DataLine.Info    info = new DataLine.Info(Clip.class, decoded);
            Clip clip = (Clip) AudioSystem.getLine(info);
            clip.open(pcm);
            return clip;
        } catch (Exception e) {
            System.err.println("[Impactful] Failed to load " + path.getFileName() + ": " + e.getMessage());
            return null;
        }
    }
}

package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.ModConfig;

public class DebugBattleStats {

    public static volatile boolean lastWasVictory = false;
    public static volatile boolean lastWasQuit    = false;
    public static volatile String  lastTime       = "00:00.000";
    public static volatile float   lastDealt      = 0f;
    public static volatile float   lastTaken      = 0f;
    public static volatile int     lastConsumes   = 0;
    public static volatile MatchGrader.GradingResult lastResult = null;
    private static volatile long   lastEndMs      = 0L;

    public static void captureFinal(boolean victory) {
        if (!ModConfig.get().debugBattleScore) return;
        lastWasVictory = victory;
        lastWasQuit    = false;
        lastTime      = BattleTracker.getFormattedTime();
        lastDealt      = BattleTracker.getDamageDealt();
        lastTaken      = BattleTracker.getDamageTaken();
        lastConsumes   = BattleTracker.getTotalConsumablesDisplay();
        lastResult     = MatchGrader.evaluate();
        lastEndMs      = System.currentTimeMillis();
    }

    public static void captureQuit() {
        if (!ModConfig.get().debugBattleScore) return;
        lastWasVictory = false;
        lastWasQuit    = true;
        lastResult     = MatchGrader.evaluate();
        lastEndMs      = System.currentTimeMillis();
    }

    public static boolean hasFreshSnapshot(long holdMs) {
        return lastResult != null && (System.currentTimeMillis() - lastEndMs) < holdMs;
    }

    public static void clear() {
        lastResult  = null;
        lastEndMs   = 0L;
        lastWasQuit = false;
    }
}

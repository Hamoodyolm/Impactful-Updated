package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.ModConfig;

public class MatchGrader {

    public enum Difficulty {
        EASY  ("Easy",   "§a", 0.60, 0.90, 0.40, new double[]{ 85, 72, 58, 44, 30 }),
        NORMAL("Normal", "§e", 0.85, 0.55, 0.75, new double[]{ 90, 80, 65, 50, 35 }),
        HARD  ("Hard",   "§c", 1.00, 0.40, 1.00, new double[]{ 95, 85, 70, 55, 40 }),
        SUPER_HARD("Super Hard", "§4", 1.25, 0.30, 1.50, new double[]{ 97, 90, 78, 62, 48 }),
        WHY   ("WHY",    "§5", 2.00, 0.18, 3.00, new double[]{ 99, 95, 88, 75, 60 });

        public final String   label;
        public final String   color;
        private final double  takenWeight;
        private final double  pacingCurve;
        private final double  penaltyScale;
        private final double[] thresholds;

        Difficulty(String label, String color, double takenWeight, double pacingCurve,
                   double penaltyScale, double[] thresholds) {
            this.label        = label;
            this.color        = color;
            this.takenWeight  = takenWeight;
            this.pacingCurve  = pacingCurve;
            this.penaltyScale = penaltyScale;
            this.thresholds   = thresholds;
        }

        public Difficulty next() {
            Difficulty[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        public static Difficulty fromIndex(int index) {
            Difficulty[] all = values();
            return index >= 0 && index < all.length ? all[index] : NORMAL;
        }
    }

    public static class GradingResult {
        public final String grade;
        public final double totalScore;
        public final double combatScore;
        public final double pacingScore;
        public final double resourceScore;
        public final Difficulty difficulty;

        public GradingResult(String grade, double totalScore,
                             double combatScore, double pacingScore, double resourceScore,
                             Difficulty difficulty) {
            this.grade         = grade;
            this.totalScore    = totalScore;
            this.combatScore   = combatScore;
            this.pacingScore   = pacingScore;
            this.resourceScore = resourceScore;
            this.difficulty    = difficulty;
        }
    }

    public static Difficulty currentDifficulty() {
        int locked = BattleTracker.getLockedDifficulty();
        return Difficulty.fromIndex(locked >= 0 ? locked : ModConfig.get().scoringDifficulty);
    }

    public static GradingResult evaluate() {
        Difficulty diff = currentDifficulty();

        double dealt          = BattleTracker.getDamageDealt();
        double taken          = BattleTracker.getDamageTaken();
        double timeSeconds    = BattleTracker.getTimeInSeconds();
        int    carrots        = BattleTracker.getGoldenCarrots();
        int    standard       = BattleTracker.getStandardConsumables();
        int    premium        = BattleTracker.getPremiumConsumables();

        double duration       = Math.max(0.5, timeSeconds);
        double safeDealt      = Math.max(0.0, dealt);
        double safeTaken      = Math.max(0.0, taken) * diff.takenWeight;

        double totalVolume    = safeDealt + safeTaken;
        double damageScore    = totalVolume > 0 ? 40.0 * (safeDealt / totalVolume) : 20.0;

        double dps            = safeDealt / duration;
        double pacingScore    = 30.0 * (1.0 - Math.exp(-diff.pacingCurve * dps));

        double rawPenalty     = ((carrots * 0.5) + (standard * 2.0) + (premium * 10.0)) * diff.penaltyScale;
        double durationMins   = Math.max(0.25, duration / 60.0);
        double resourceScore  = Math.max(0.0, 30.0 - (rawPenalty / durationMins));

        double total = damageScore + pacingScore + resourceScore;

        double[] t = diff.thresholds;
        String grade;
        if      (total >= t[0]) grade = "S";
        else if (total >= t[1]) grade = "A";
        else if (total >= t[2]) grade = "B";
        else if (total >= t[3]) grade = "C";
        else if (total >= t[4]) grade = "D";
        else                    grade = "F";

        return new GradingResult(grade, total, damageScore, pacingScore, resourceScore, diff);
    }
}

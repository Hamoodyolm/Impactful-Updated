package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.List;

public class DebugStatsRenderer {

    private static final long FINAL_HOLD_MS = 5000L;
    private static final int  PANEL_BG      = 0xB0000000;
    private static final int  PAD           = 4;
    private static final int  LINE_H        = 10;

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        List<String> board = StatsManager.getRevealedBoardLines();
        if (board != null) {
            List<String> full = StatsManager.getAllBoardLines();
            drawPanel(context, board, full != null ? full : board);
            return;
        }

        if (!ModConfig.get().debugBattleScore) return;

        if (BattleTracker.isInBattle()) {
            List<String> lines = buildLiveLines();
            drawPanel(context, lines, lines);
        } else if (DebugBattleStats.hasFreshSnapshot(FINAL_HOLD_MS)) {
            List<String> lines = DebugBattleStats.lastWasQuit
                    ? List.of("§c§lWe don't grade quitters.")
                    : buildFinalLines();
            drawPanel(context, lines, lines);
        }
    }

    private static List<String> buildLiveLines() {
        MatchGrader.GradingResult r = MatchGrader.evaluate();
        List<String> lines = new ArrayList<>();
        lines.add("§7Time: §f" + BattleTracker.getFormattedTime());
        lines.add("§7Dmg Dealt: §a" + (int) BattleTracker.getDamageDealt());
        lines.add("§7Dmg Taken: §c" + (int) BattleTracker.getDamageTaken());
        lines.add("§7Consumables: §f" + BattleTracker.getTotalConsumablesDisplay());
        addScoreLines(lines, r);
        return lines;
    }

    private static List<String> buildFinalLines() {
        MatchGrader.GradingResult r = DebugBattleStats.lastResult;
        List<String> lines = new ArrayList<>();
        String tag = DebugBattleStats.lastWasVictory ? "§aVICTORY" : "§cDEFEAT";
        lines.add("§6§lIMPACTFUL DEBUG §r§e[FINAL] " + tag);
        lines.add("§7Time: §f" + DebugBattleStats.lastTime);
        lines.add("§7Dmg Dealt: §a" + (int) DebugBattleStats.lastDealt);
        lines.add("§7Dmg Taken: §c" + (int) DebugBattleStats.lastTaken);
        lines.add("§7Consumables: §f" + DebugBattleStats.lastConsumes);
        addScoreLines(lines, r);
        return lines;
    }

    private static void addScoreLines(List<String> lines, MatchGrader.GradingResult r) {
        if (r == null) return;
        lines.add("§8§m                    ");
        lines.add("§7Combat:   §f" + fmt(r.combatScore)   + "§8/40");
        lines.add("§7Pacing:   §f" + fmt(r.pacingScore)   + "§8/30");
        lines.add("§7Resource: §f" + fmt(r.resourceScore) + "§8/30");
        lines.add("§e§lTOTAL: §f" + fmt(r.totalScore) + "§8/100  §e§lGrade: " + gradeColor(r.grade) + "§l" + r.grade);
        lines.add("§7Difficulty: " + r.difficulty.color + r.difficulty.label);
    }

    private static String fmt(double v) {
        return String.valueOf(Math.round(v * 10.0) / 10.0);
    }

    private static String gradeColor(String grade) {
        return switch (grade) {
            case "S" -> "§b";
            case "A" -> "§a";
            case "B" -> "§2";
            case "C" -> "§e";
            case "D" -> "§6";
            default  -> "§c";
        };
    }

    private static void drawPanel(DrawContext context, List<String> lines, List<String> sizeLines) {
        MinecraftClient client = MinecraftClient.getInstance();
        var tr = client.textRenderer;

        int maxW = 0;
        for (String line : sizeLines) maxW = Math.max(maxW, tr.getWidth(line));

        int panelW = maxW + PAD * 2;
        int panelH = sizeLines.size() * LINE_H + PAD * 2;

        int x = 6;
        int y = (context.getScaledWindowHeight() - panelH) / 2;

        context.fill(x, y, x + panelW, y + panelH, PANEL_BG);

        int ty = y + PAD;
        for (String line : lines) {
            context.drawTextWithShadow(tr, line, x + PAD, ty, 0xFFFFFFFF);
            ty += LINE_H;
        }
    }
}

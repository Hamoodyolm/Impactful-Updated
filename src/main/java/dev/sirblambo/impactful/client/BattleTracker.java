package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.ModConfig;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BattleTracker {

    private static long    battleStartMs        = -1;
    private static float   damageDealt          = 0;
    private static float   estimatedDamageDealt = 0;
    private static float   damageTaken          = 0;
    private static int     goldenCarrotsUsed    = 0;
    private static int     standardConsumables  = 0;
    private static int     premiumConsumables   = 0;
    private static boolean inBattle             = false;
    private static int     spectatorDefeatTicks = 0;
    private static final Map<UUID, String> opponents = new HashMap<>();
    private static int     lockedDifficulty     = -1;

    private static final long BATTLE_TIMEOUT_MS = 45_000L;
    private static final int  SPECTATOR_DEFEAT_DELAY_TICKS = 3;
    private static long    lastActivityMs       = -1;

    public static final Set<Item> FREE_FOODS = Set.of(
            Items.BAKED_POTATO,
            Items.COOKED_CHICKEN,
            Items.COOKED_MUTTON,
            Items.COOKED_PORKCHOP,
            Items.COOKED_BEEF
    );

    public static final Set<Item> CARROT_FOODS = Set.of(
            Items.GOLDEN_CARROT
    );

    public static final Set<Item> STANDARD_CONSUMABLES = Set.of(
            Items.GOLDEN_APPLE,
            Items.POTION,
            Items.SPLASH_POTION,
            Items.LINGERING_POTION
    );

    public static final Set<Item> PREMIUM_CONSUMABLES = Set.of(
            Items.ENCHANTED_GOLDEN_APPLE
    );

    public static final Set<Item> ALL_TRACKED;
    static {
        ALL_TRACKED = new java.util.HashSet<>();
        ALL_TRACKED.addAll(FREE_FOODS);
        ALL_TRACKED.addAll(CARROT_FOODS);
        ALL_TRACKED.addAll(STANDARD_CONSUMABLES);
        ALL_TRACKED.addAll(PREMIUM_CONSUMABLES);
    }

    public static void onFirstHit() {
        if (inBattle) return;
        inBattle             = true;
        battleStartMs        = System.currentTimeMillis();
        lastActivityMs       = battleStartMs;
        damageDealt          = 0;
        estimatedDamageDealt = 0;
        damageTaken          = 0;
        goldenCarrotsUsed    = 0;
        standardConsumables  = 0;
        premiumConsumables   = 0;
        lockedDifficulty     = ModConfig.get().scoringDifficulty;
    }

    public static void addOpponent(UUID uuid, String name) {
        if (inBattle) opponents.put(uuid, name);
    }

    public static boolean isOpponent(UUID uuid) { return opponents.containsKey(uuid); }
    public static Map<UUID, String> getOpponents() { return Collections.unmodifiableMap(opponents); }

    public static void registerDamageDealt(float amount) {
        if (!inBattle) return;
        damageDealt += amount;
        lastActivityMs = System.currentTimeMillis();
    }

    public static void registerEstimatedDamageDealt(float amount) {
        if (!inBattle) return;
        estimatedDamageDealt += amount;
        lastActivityMs = System.currentTimeMillis();
    }

    public static void registerDamageTaken(float amount) {
        if (!inBattle) return;
        damageTaken += amount;
        lastActivityMs = System.currentTimeMillis();
    }

    public static void registerConsumable(Item item) {
        if (!inBattle) return;
        if (CARROT_FOODS.contains(item))         goldenCarrotsUsed++;
        else if (STANDARD_CONSUMABLES.contains(item)) standardConsumables++;
        else if (PREMIUM_CONSUMABLES.contains(item))  premiumConsumables++;
        lastActivityMs = System.currentTimeMillis();
    }

    public static void registerTotemPop() {
        if (!inBattle) return;
        premiumConsumables++;
        lastActivityMs = System.currentTimeMillis();
    }

    public static void onLocalSpectator() {
        if (inBattle) spectatorDefeatTicks = SPECTATOR_DEFEAT_DELAY_TICKS;
    }

    public static void tick() {
        if (!inBattle || lastActivityMs < 0) return;
        if (spectatorDefeatTicks > 0 && --spectatorDefeatTicks == 0) {
            StatsManager.triggerDefeat();
            return;
        }
        if (System.currentTimeMillis() - lastActivityMs > BATTLE_TIMEOUT_MS) {
            reset();
        }
    }

    public static void reset() {
        battleStartMs        = -1;
        lastActivityMs       = -1;
        damageDealt          = 0;
        estimatedDamageDealt = 0;
        damageTaken          = 0;
        goldenCarrotsUsed    = 0;
        standardConsumables  = 0;
        premiumConsumables   = 0;
        inBattle             = false;
        spectatorDefeatTicks = 0;
        opponents.clear();
        lockedDifficulty     = -1;
    }

    public static boolean isInBattle()          { return inBattle; }
    public static int     getLockedDifficulty()  { return lockedDifficulty; }
    public static float   getDamageDealt()       { return damageDealt + estimatedDamageDealt; }
    public static float   getDamageTaken()       { return damageTaken; }
    public static int     getGoldenCarrots()     { return goldenCarrotsUsed; }
    public static int     getStandardConsumables() { return standardConsumables; }
    public static int     getPremiumConsumables()  { return premiumConsumables; }

    public static int getTotalConsumablesDisplay() {
        return goldenCarrotsUsed + standardConsumables + premiumConsumables;
    }

    public static double getTimeInSeconds() {
        if (battleStartMs < 0) return 0;
        return (System.currentTimeMillis() - battleStartMs) / 1000.0;
    }

    public static String getFormattedTime() {
        if (battleStartMs < 0) return "00:00.000";
        long elapsed = System.currentTimeMillis() - battleStartMs;
        long minutes = elapsed / 60000;
        long seconds = (elapsed % 60000) / 1000;
        long millis  = elapsed % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, millis);
    }

    public static int getConsumablesUsed() {
        return goldenCarrotsUsed + standardConsumables + premiumConsumables;
    }
}
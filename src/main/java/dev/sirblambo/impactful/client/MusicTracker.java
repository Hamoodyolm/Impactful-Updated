package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

public class MusicTracker {

    private static int  hitsInTier        = 0;
    private static int  ticksSinceLastHit = 0;
    private static boolean inCombat       = false;

    private static final int  MUSIC_HIT_DEBOUNCE_TICKS = 10;
    private static final int  DAMAGE_PACKET_WINDOW_TICKS = 5;
    private static long clientTicks          = 0;
    private static long lastMusicHitTick     = -MUSIC_HIT_DEBOUNCE_TICKS;
    private static long lastDamagePacketTick = -DAMAGE_PACKET_WINDOW_TICKS - 1;

    public static void onLocalDamagePacket(boolean fromPlayer) {
        lastDamagePacketTick = clientTicks;
        if (fromPlayer) onLocalPlayerHitByPlayer();
    }

    public static boolean tookDamagePacketRecently() {
        return clientTicks - lastDamagePacketTick <= 1;
    }

    public static void onLocalPresumedDeath() {
        if (inCombat || MusicManager.getCurrentTier() != null) reset();
    }

    public static void onLocalHealthDrop(boolean playerClose) {
        if (clientTicks - lastDamagePacketTick <= DAMAGE_PACKET_WINDOW_TICKS) return;
        if (playerClose) onLocalPlayerHitByPlayer();
    }

    private static void onLocalPlayerHitByPlayer() {
        if (!ModConfig.get().pvpMusic || MusicManager.isPreviewing()) return;
        if (MusicManager.getCurrentTier() == null) return;
        if (clientTicks - lastMusicHitTick < MUSIC_HIT_DEBOUNCE_TICKS) return;
        lastMusicHitTick = clientTicks;
        registerHit();
    }

    public static void onLocalPlayerAttackedPlayer(UUID targetUuid) {
        if (!ModConfig.get().pvpMusic || MusicManager.isPreviewing()) return;
        registerHit();
    }

    private static void registerHit() {
        hitsInTier++;
        ticksSinceLastHit = 0;
        inCombat = true;

        Tier current = MusicManager.getCurrentTier();
        Tier base    = current != null ? current : Tier.CALM;

        int needed = Math.max(1, ModConfig.get().getTierConfig(base.ordinal()).hitsToAdvance);
        if (hitsInTier < needed) return;

        Tier next = nextEnabledAbove(base);
        if (next == null) return;

        hitsInTier = 0;
        MusicManager.requestTier(next);
    }

    public static void tick() {
        clientTicks++;
        if (!ModConfig.get().pvpMusic || MusicManager.isPreviewing()) return;
        MusicManager.tickPendingTier();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        if (client.player.isDead() || client.player.isSpectator()) {
            if (inCombat || MusicManager.getCurrentTier() != null) reset();
            return;
        }

        double radius = ModConfig.get().musicProximityRadius;
        boolean playerNearby = !client.world.getEntitiesByClass(
                PlayerEntity.class,
                client.player.getBoundingBox().expand(radius),
                e -> e != client.player
        ).isEmpty();

        MusicManager.setPlayerNearby(playerNearby);

        if (!inCombat) return;

        ticksSinceLastHit++;

        Tier current = MusicManager.getCurrentTier();
        if (current == null) return;

        float decaySecs = Math.max(1f, ModConfig.get().getTierConfig(current.ordinal()).decaySecs);
        if (ticksSinceLastHit < Math.round(decaySecs * 20f)) return;

        hitsInTier        = 0;
        ticksSinceLastHit = 0;

        Tier next = prevEnabledBelow(current);

        if (next == null) {
            inCombat = false;
            MusicManager.fadeOutAll();
            return;
        }

        if (next == Tier.CALM) inCombat = false;
        MusicManager.requestTier(next);
    }

    private static Tier nextEnabledAbove(Tier from) {
        boolean[] enabled = ModConfig.get().tierEnabled;
        Tier[] tiers = Tier.values();
        for (int i = from.ordinal() + 1; i < tiers.length; i++) {
            if (enabled[i]) return tiers[i];
        }
        return null;
    }

    private static Tier prevEnabledBelow(Tier from) {
        boolean[] enabled = ModConfig.get().tierEnabled;
        Tier[] tiers = Tier.values();
        for (int i = from.ordinal() - 1; i >= 0; i--) {
            if (enabled[i]) return tiers[i];
        }
        return null;
    }

    public static void reset() {
        hitsInTier        = 0;
        ticksSinceLastHit = 0;
        inCombat          = false;
        MusicManager.fadeOutAll();
    }
}

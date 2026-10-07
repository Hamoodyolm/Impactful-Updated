package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.world.GameMode;

import java.util.*;

public class KillDetector {
    private static final Map<UUID, Float>   lastHealth       = new HashMap<>();
    private static final Set<UUID>          myHits           = new HashSet<>();
    private static final Map<UUID, Integer> hitAge           = new HashMap<>();
    private static final Map<UUID, Boolean> wasCrit          = new HashMap<>();
    private static final Map<UUID, Boolean> wasMaceSmash     = new HashMap<>();
    private static final Map<UUID, String>  hitEntityNames   = new HashMap<>();
    private static final Map<UUID, Boolean> hitEntityIsPlayer  = new HashMap<>();
    private static final Map<UUID, Boolean> hitEntityIsHostile = new HashMap<>();
    private static final Map<UUID, Float>   hitEstimate      = new HashMap<>();
    private static final Map<UUID, Float>   healthAtHit      = new HashMap<>();
    private static final Map<UUID, Integer> lastHurtTime     = new HashMap<>();
    private static final Map<UUID, Boolean> hadTotem         = new HashMap<>();
    private static final Map<UUID, Integer> totemPopDebounce = new HashMap<>();
    private static final Map<UUID, Float>   hitRawEstimate   = new HashMap<>();
    private static final Map<UUID, Float>   dealtSinceHit    = new HashMap<>();
    private static final Map<UUID, Integer> overflowPending  = new HashMap<>();
    private static final Map<UUID, Double>  opponentLastY    = new HashMap<>();
    private static final int MAX_HIT_AGE = 120;
    private static final int ESTIMATE_SETTLE_TICKS = 10;
    private static final int DAMAGE_CONFIRM_WINDOW = 20;
    private static final int SPECTATOR_KILL_WINDOW = 60;
    private static final int TOTEM_ATTACK_WINDOW = 40;
    private static final int TOTEM_POP_DEBOUNCE = 20;
    private static final int OVERFLOW_SETTLE_TICKS = 5;

    private static boolean healthPacketsWorking = false;
    private static boolean packetsConfirmedBlocked = false;

    public static void registerHit(UUID uuid, boolean isCritical, boolean isMaceSmash,
                                    String entityName, boolean isPlayer, boolean isHostile,
                                    float estimatedDamage) {
        settleOverflow(uuid);
        myHits.add(uuid);
        hitAge.put(uuid, 0);
        hitRawEstimate.put(uuid, estimatedDamage);
        dealtSinceHit.put(uuid, 0f);
        Float hpNow = lastHealth.get(uuid);
        if (hpNow != null) healthAtHit.put(uuid, hpNow);
        wasCrit.put(uuid, isCritical);
        wasMaceSmash.put(uuid, isMaceSmash);
        hitEntityNames.put(uuid, entityName);
        hitEntityIsPlayer.put(uuid, isPlayer);
        hitEntityIsHostile.put(uuid, isHostile);
        if (estimatedDamage > 0f) {
            if (packetsConfirmedBlocked && !healthPacketsWorking && BattleTracker.isInBattle()) {
                BattleTracker.registerEstimatedDamageDealt(estimatedDamage);
            } else {
                hitEstimate.merge(uuid, estimatedDamage, Float::sum);
            }
        }
    }

    public static void registerHitAndCheckAnyHit(UUID uuid, boolean isCritical, LivingEntity entity) {
        ModConfig config = ModConfig.get();
        if (config.enabled && config.triggerOnAnyHit) {
            boolean isPlayer  = entity instanceof PlayerEntity;
            boolean isHostile = entity instanceof HostileEntity;

            boolean entityAllowed = false;
            if (config.triggerOnAny) entityAllowed = true;
            if (config.triggerOnPlayers && isPlayer) entityAllowed = true;
            if (config.triggerOnHostile && isHostile) entityAllowed = true;

            if (entityAllowed) {
                FlashController.trigger(uuid);
            }
        }
    }

    public static void tick() {
        if (!ModConfig.get().enabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        ModConfig config = ModConfig.get();

        hitAge.replaceAll((uuid, age) -> age + 1);
        hitAge.entrySet().removeIf(e -> e.getValue() > MAX_HIT_AGE);
        myHits.removeIf(uuid -> !hitAge.containsKey(uuid));
        wasCrit.keySet().removeIf(uuid -> !hitAge.containsKey(uuid));
        wasMaceSmash.keySet().removeIf(uuid -> !hitAge.containsKey(uuid));
        hitEntityNames.keySet().removeIf(uuid -> !hitAge.containsKey(uuid));
        hitEntityIsPlayer.keySet().removeIf(uuid -> !hitAge.containsKey(uuid));
        hitEntityIsHostile.keySet().removeIf(uuid -> !hitAge.containsKey(uuid));
        hitEstimate.keySet().removeIf(uuid -> !hitAge.containsKey(uuid));
        healthAtHit.keySet().removeIf(uuid -> !hitAge.containsKey(uuid));
        lastHurtTime.keySet().removeIf(uuid -> !hitAge.containsKey(uuid));
        hitRawEstimate.keySet().removeIf(uuid -> !hitAge.containsKey(uuid));
        dealtSinceHit.keySet().removeIf(uuid -> !hitAge.containsKey(uuid));
        totemPopDebounce.replaceAll((k, v) -> v - 1);
        totemPopDebounce.entrySet().removeIf(e -> e.getValue() <= 0);

        Set<UUID> toRemove = new HashSet<>();

        List<LivingEntity> entities = client.world.getEntitiesByClass(
                LivingEntity.class,
                client.player.getBoundingBox().expand(64.0),
                e -> e != client.player
        );

        for (LivingEntity entity : entities) {
            UUID uuid = entity.getUuid();
            float hp = entity.getHealth();
            Float prevHp = lastHealth.get(uuid);

            boolean justDied = prevHp != null && prevHp > 0.0F && hp <= 0.0F;

            if (justDied && myHits.contains(uuid)) {
                boolean isPlayer  = entity instanceof PlayerEntity;
                boolean isHostile = entity instanceof HostileEntity;
                boolean crit      = Boolean.TRUE.equals(wasCrit.get(uuid));

                boolean entityAllowed = config.triggerOnAny
                        || (config.triggerOnPlayers && isPlayer)
                        || (config.triggerOnHostile && isHostile);

                boolean shouldFlash = entityAllowed && !config.triggerOnAnyHit
                        && (config.triggerOnKill
                            || (config.triggerOnCriticals && crit)
                            || (config.triggerOnMaceSmash && Boolean.TRUE.equals(wasMaceSmash.get(uuid))));

                registerKillingBlow(uuid, isPlayer, prevHp);
                boolean concludeBattle = BattleTracker.isInBattle() && isPlayer;

                if (shouldFlash) FlashController.trigger(uuid);

                if (concludeBattle) {
                    StatsManager.triggerVictory();
                    MusicTracker.reset();
                }
                clearHit(uuid);
                toRemove.add(uuid);
            } else if (justDied) {
                if (entity instanceof PlayerEntity && BattleTracker.isOpponent(uuid)) concludeOpponentDeath(uuid);
                toRemove.add(uuid);
            } else {
                if (prevHp != null && hp != prevHp) healthPacketsWorking = true;

                if (myHits.contains(uuid) && Boolean.TRUE.equals(hitEntityIsPlayer.get(uuid))) {
                    int ht = entity.hurtTime;
                    Integer prevHt = lastHurtTime.get(uuid);
                    Integer ageH = hitAge.get(uuid);
                    boolean recent = ageH != null && ageH <= DAMAGE_CONFIRM_WINDOW;
                    boolean damaged = (prevHt != null && ht > prevHt)
                            || (prevHp != null && hp < prevHp);
                    if (recent && damaged) {
                        BattleTracker.onFirstHit();
                        BattleTracker.addOpponent(uuid, entity.getName().getString());
                        MusicTracker.onLocalPlayerAttackedPlayer(uuid);
                    }
                    lastHurtTime.put(uuid, ht);
                }

                if (entity instanceof PlayerEntity) {
                    boolean hasTotem = entity.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)
                            || entity.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING);
                    Integer ageT = hitAge.get(uuid);
                    if (Boolean.TRUE.equals(hadTotem.get(uuid)) && !hasTotem
                            && myHits.contains(uuid) && ageT != null && ageT <= TOTEM_ATTACK_WINDOW) {
                        markOverflow(uuid);
                        fireTotemPop(uuid, entity);
                    }
                    hadTotem.put(uuid, hasTotem);
                }

                if (prevHp != null && prevHp < 1.0f && hp == 1.0f && myHits.contains(uuid)) {
                    markOverflow(uuid);
                    fireTotemPop(uuid, entity);
                }

                if (prevHp != null && hp < prevHp && myHits.contains(uuid)) {
                    BattleTracker.registerDamageDealt(prevHp - hp);
                    dealtSinceHit.merge(uuid, prevHp - hp, Float::sum);
                }

                lastHealth.put(uuid, hp);
            }
        }

        var netHandler = client.getNetworkHandler();
        if (netHandler != null) {
            for (UUID uuid : new ArrayList<>(myHits)) {
                if (!Boolean.TRUE.equals(hitEntityIsPlayer.get(uuid))) continue;
                Integer age = hitAge.get(uuid);
                if (age == null || age > SPECTATOR_KILL_WINDOW) continue;
                PlayerListEntry entry = netHandler.getPlayerListEntry(uuid);
                if (entry == null || entry.getGameMode() != GameMode.SPECTATOR) continue;

                boolean shouldFlash = (config.triggerOnAny || config.triggerOnPlayers)
                        && !config.triggerOnAnyHit
                        && (config.triggerOnKill
                            || (config.triggerOnCriticals && Boolean.TRUE.equals(wasCrit.get(uuid)))
                            || (config.triggerOnMaceSmash && Boolean.TRUE.equals(wasMaceSmash.get(uuid))));
                registerKillingBlow(uuid, true, lastHealth.get(uuid));
                boolean concludeBattle = BattleTracker.isInBattle();

                if (shouldFlash) FlashController.trigger(uuid);
                if (concludeBattle) {
                    StatsManager.triggerVictory();
                    MusicTracker.reset();
                }
                clearHit(uuid);
                toRemove.add(uuid);
            }

            for (UUID uuid : new ArrayList<>(BattleTracker.getOpponents().keySet())) {
                if (!BattleTracker.isInBattle()) break;
                PlayerListEntry entry = netHandler.getPlayerListEntry(uuid);
                if (entry != null && entry.getGameMode() == GameMode.SPECTATOR) {
                    concludeOpponentDeath(uuid);
                    toRemove.add(uuid);
                }
            }
        }

        Set<UUID> visibleUUIDs = new HashSet<>();
        for (LivingEntity e : entities) visibleUUIDs.add(e.getUuid());
        hadTotem.keySet().retainAll(visibleUUIDs);

        for (UUID uuid : new ArrayList<>(myHits)) {
            if (visibleUUIDs.contains(uuid)) continue;
            if (!lastHealth.containsKey(uuid)) continue;
            Integer age = hitAge.get(uuid);
            if (age == null || age > 20) continue;

            boolean isPlayer  = Boolean.TRUE.equals(hitEntityIsPlayer.get(uuid));
            boolean isHostile = Boolean.TRUE.equals(hitEntityIsHostile.get(uuid));
            boolean entityAllowed = config.triggerOnAny
                    || (config.triggerOnPlayers && isPlayer)
                    || (config.triggerOnHostile && isHostile);

            boolean shouldFlash = entityAllowed && !config.triggerOnAnyHit
                    && (config.triggerOnKill
                        || (config.triggerOnCriticals && Boolean.TRUE.equals(wasCrit.get(uuid)))
                        || (config.triggerOnMaceSmash && Boolean.TRUE.equals(wasMaceSmash.get(uuid))));

            registerKillingBlow(uuid, isPlayer, lastHealth.get(uuid));
            boolean concludeBattle = BattleTracker.isInBattle() && isPlayer;

            if (shouldFlash) FlashController.trigger(uuid);

            if (concludeBattle) {
                StatsManager.triggerVictory();
                MusicTracker.reset();
            }

            clearHit(uuid);
            toRemove.add(uuid);
        }

        int voidY = client.world.getBottomY() - 1;
        for (UUID uuid : new ArrayList<>(opponentLastY.keySet())) {
            if (visibleUUIDs.contains(uuid)) continue;
            Double y = opponentLastY.remove(uuid);
            if (y != null && y < voidY && BattleTracker.isOpponent(uuid)) {
                concludeOpponentDeath(uuid);
                toRemove.add(uuid);
            }
        }
        if (BattleTracker.isInBattle()) {
            for (LivingEntity e : entities) {
                if (BattleTracker.isOpponent(e.getUuid())) opponentLastY.put(e.getUuid(), e.getY());
            }
        } else {
            opponentLastY.clear();
        }

        for (UUID uuid : new ArrayList<>(overflowPending.keySet())) {
            if (overflowPending.merge(uuid, -1, Integer::sum) <= 0) settleOverflow(uuid);
        }

        if (healthPacketsWorking) {
            hitEstimate.clear();
        } else {
            for (UUID uuid : new ArrayList<>(hitEstimate.keySet())) {
                Integer age = hitAge.get(uuid);
                if (age != null && age >= ESTIMATE_SETTLE_TICKS) {
                    Float est = hitEstimate.remove(uuid);
                    if (est != null) BattleTracker.registerEstimatedDamageDealt(est);
                    packetsConfirmedBlocked = true;
                }
            }
        }

        toRemove.forEach(lastHealth::remove);
        lastHealth.keySet().removeIf(uuid ->
                client.world.getEntitiesByClass(LivingEntity.class,
                        client.player.getBoundingBox().expand(64.0),
                        e -> e.getUuid().equals(uuid)).isEmpty()
        );
    }

    public static void onGameMessage(Text message) {
        if (!ModConfig.get().enabled || !BattleTracker.isInBattle()) return;
        if (!(message.getContent() instanceof TranslatableTextContent content)) return;
        if (!content.getKey().startsWith("death.")) return;
        Object[] args = content.getArgs();
        if (args.length == 0) return;
        String victim = args[0] instanceof Text t ? t.getString() : String.valueOf(args[0]);
        for (Map.Entry<UUID, String> opp : new ArrayList<>(BattleTracker.getOpponents().entrySet())) {
            if (containsName(victim, opp.getValue())) {
                concludeOpponentDeath(opp.getKey());
                return;
            }
        }
    }

    private static boolean containsName(String text, String name) {
        int i = text.indexOf(name);
        while (i >= 0) {
            int end = i + name.length();
            boolean startOk = i == 0 || !isNameChar(text.charAt(i - 1));
            boolean endOk = end == text.length() || !isNameChar(text.charAt(end));
            if (startOk && endOk) return true;
            i = text.indexOf(name, i + 1);
        }
        return false;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static void concludeOpponentDeath(UUID uuid) {
        if (!BattleTracker.isInBattle()) return;
        StatsManager.triggerVictory();
        MusicTracker.reset();
        clearHit(uuid);
    }

    private static void fireTotemPop(UUID uuid, LivingEntity entity) {
        ModConfig config = ModConfig.get();
        if (!config.triggerOnTotemPop) return;
        if (totemPopDebounce.getOrDefault(uuid, 0) > 0) return;
        boolean isPlayer  = entity instanceof PlayerEntity;
        boolean isHostile = entity instanceof HostileEntity;
        boolean entityAllowed = config.triggerOnAny
                || (config.triggerOnPlayers && isPlayer)
                || (config.triggerOnHostile && isHostile);
        if (!entityAllowed) return;
        FlashController.trigger(uuid);
        totemPopDebounce.put(uuid, TOTEM_POP_DEBOUNCE);
    }

    private static void registerKillingBlow(UUID uuid, boolean isPlayer, Float remainingHp) {
        if (isPlayer) BattleTracker.onFirstHit();
        Float atHit = healthAtHit.get(uuid);
        if (remainingHp != null && atHit != null) remainingHp = Math.min(remainingHp, atHit);
        if (healthPacketsWorking && remainingHp != null && remainingHp > 0f) {
            BattleTracker.registerDamageDealt(remainingHp);
            Float est = hitRawEstimate.get(uuid);
            float dealt = dealtSinceHit.getOrDefault(uuid, 0f) + remainingHp;
            if (isRecentHit(uuid) && est != null && est > dealt) {
                BattleTracker.registerEstimatedDamageDealt(est - dealt);
            }
            hitEstimate.remove(uuid);
        } else {
            settleEstimate(uuid);
        }
    }

    private static boolean isRecentHit(UUID uuid) {
        Integer age = hitAge.get(uuid);
        return age != null && age <= DAMAGE_CONFIRM_WINDOW;
    }

    private static void markOverflow(UUID uuid) {
        if (!healthPacketsWorking || !isRecentHit(uuid) || !hitRawEstimate.containsKey(uuid)) return;
        overflowPending.putIfAbsent(uuid, OVERFLOW_SETTLE_TICKS);
    }

    private static void settleOverflow(UUID uuid) {
        if (overflowPending.remove(uuid) == null) return;
        Float est = hitRawEstimate.remove(uuid);
        float dealt = dealtSinceHit.getOrDefault(uuid, 0f);
        if (est != null && est > dealt) BattleTracker.registerEstimatedDamageDealt(est - dealt);
    }

    private static void settleEstimate(UUID uuid) {
        if (!healthPacketsWorking) {
            Float est = hitEstimate.get(uuid);
            if (est != null && est > 0f) BattleTracker.registerEstimatedDamageDealt(est);
        }
        hitEstimate.remove(uuid);
    }

    private static void clearHit(UUID uuid) {
        settleEstimate(uuid);
        healthAtHit.remove(uuid);
        myHits.remove(uuid);
        hitAge.remove(uuid);
        wasCrit.remove(uuid);
        wasMaceSmash.remove(uuid);
        hitEntityNames.remove(uuid);
        hitEntityIsPlayer.remove(uuid);
        hitEntityIsHostile.remove(uuid);
        lastHurtTime.remove(uuid);
        hitRawEstimate.remove(uuid);
        dealtSinceHit.remove(uuid);
        overflowPending.remove(uuid);
    }

    public static void reset() {
        lastHealth.clear();
        myHits.clear();
        hitAge.clear();
        wasCrit.clear();
        wasMaceSmash.clear();
        hitEntityNames.clear();
        hitEntityIsPlayer.clear();
        hitEntityIsHostile.clear();
        hitEstimate.clear();
        healthAtHit.clear();
        lastHurtTime.clear();
        hadTotem.clear();
        totemPopDebounce.clear();
        hitRawEstimate.clear();
        dealtSinceHit.clear();
        overflowPending.clear();
        opponentLastY.clear();
        healthPacketsWorking = false;
        packetsConfirmedBlocked = false;
    }
}

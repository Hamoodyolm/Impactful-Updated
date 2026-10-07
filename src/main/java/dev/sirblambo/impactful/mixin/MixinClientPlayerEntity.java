package dev.sirblambo.impactful.mixin;

import dev.sirblambo.impactful.client.BattleTracker;
import dev.sirblambo.impactful.client.DamageEstimator;
import dev.sirblambo.impactful.client.DeathFlashController;
import dev.sirblambo.impactful.client.IncomingDamageEstimator;
import dev.sirblambo.impactful.client.MusicTracker;
import dev.sirblambo.impactful.client.SoundManager;
import dev.sirblambo.impactful.client.StatsManager;
import dev.sirblambo.impactful.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.WindChargeEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class MixinClientPlayerEntity {

    private boolean deathTriggeredThisDeath = false;
    private ItemStack lastActiveItem = ItemStack.EMPTY;
    private float previousHealth = -1f;
    private float previousAbsorption = 0f;
    private boolean lastOffhandWasTotem = false;
    private boolean lastMainhandWasTotem = false;

    private static final double PVP_MUSIC_FALLBACK_RADIUS = 4.0;

    private static final float  LETHAL_HEALTH_THRESHOLD  = 4.0f;
    private static final int    LETHAL_WATCH_TICKS       = 20;
    private static final double LETHAL_TELEPORT_DISTANCE = 16.0;
    private static int    lethalWatchTicks  = 0;
    private static float  lethalWatchEstimate = 0f;
    private static float  lethalWatchObserved = 0f;
    private static Object lastWorld         = null;
    private static Vec3d  lastPos           = null;
    private static double lastFallDistance  = 0.0;
    private static final int PEARL_GRACE_TICKS = 100;
    private static boolean lastPearlCooling = false;
    private static int     ticksSincePearl  = PEARL_GRACE_TICKS;
    private static final int    POTION_GRACE_TICKS     = 20;
    private static final double POTION_NEARBY_RADIUS   = 6.0;
    private static final double PEARL_SEARCH_RADIUS    = 128.0;
    private static final float  RESET_HEAL_FRACTION    = 0.75f;
    private static int     ticksSincePotion = POTION_GRACE_TICKS;
    private static final int    WIND_CHARGE_SLACK_TICKS  = 5;
    private static final double WIND_CHARGE_SEARCH_RADIUS = 16.0;
    private static boolean lastWindCooling    = false;
    private static int     ticksSinceWindCharge = Integer.MAX_VALUE / 2;
    private static int     airTicks           = 0;
    private static boolean lethalWatchFromFall = false;

    @Inject(method = "updatePostDeath", at = @At("HEAD"))
    private void onClientDeath(CallbackInfo ci) {
        if (!deathTriggeredThisDeath) {
            deathTriggeredThisDeath = true;
            DeathFlashController.trigger();

            if (ModConfig.get().killSoundOnDeath) {
                SoundManager.playIfEnabled();
            }

            StatsManager.triggerDefeat();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        ClientPlayerEntity self = (ClientPlayerEntity)(Object) this;

        if (!self.isDead()) {
            deathTriggeredThisDeath = false;
        }

        float currentHealth     = self.getHealth();
        float currentAbsorption = self.getAbsorptionAmount();
        boolean offTotem  = self.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        boolean mainTotem = self.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING);

        boolean healthDropped = previousHealth > 0f && currentHealth < previousHealth;
        boolean died          = healthDropped && currentHealth <= 0f;
        boolean totemPopped   = healthDropped && currentHealth > 0f && currentHealth <= 1.0f
                && (lastOffhandWasTotem || lastMainhandWasTotem);
        boolean damageEvent   = healthDropped || MusicTracker.tookDamagePacketRecently();

        float observed = healthDropped ? previousHealth - currentHealth : 0f;
        if (damageEvent && !totemPopped && previousAbsorption > currentAbsorption) {
            observed += previousAbsorption - currentAbsorption;
        }

        if (observed > 0f) {
            if (healthDropped && !died) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null && mc.world != null) {
                    boolean playerClose = !mc.world.getEntitiesByClass(
                            PlayerEntity.class,
                            self.getBoundingBox().expand(PVP_MUSIC_FALLBACK_RADIUS),
                            e -> e != self && !e.isSpectator()
                    ).isEmpty();
                    MusicTracker.onLocalHealthDrop(playerClose);
                }
            }
            float taken = (died || totemPopped)
                    ? Math.max(observed, IncomingDamageEstimator.recentEstimate())
                    : observed;
            registerDamageTaken(self, taken);
        }

        if (!self.isDead()) {
            checkPresumedDeath(self, currentHealth, observed);
        }
        previousHealth     = currentHealth;
        previousAbsorption = currentAbsorption;

        if (BattleTracker.isInBattle() && lastOffhandWasTotem && !offTotem) {
            BattleTracker.registerTotemPop();
        }
        lastOffhandWasTotem  = offTotem;
        lastMainhandWasTotem = mainTotem;

        if (!BattleTracker.isInBattle()) return;

        ItemStack current = self.getActiveItem();
        if (!current.isEmpty() && lastActiveItem.isEmpty()) {
            if (BattleTracker.ALL_TRACKED.contains(current.getItem())) {
                BattleTracker.registerConsumable(current.getItem());
            }
        }
        lastActiveItem = current.copy();
    }

    private void registerDamageTaken(ClientPlayerEntity self, float amount) {
        if (BattleTracker.isInBattle()) BattleTracker.registerDamageTaken(amount);
    }

    private void checkPresumedDeath(ClientPlayerEntity self, float currentHealth, float observed) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d pos = self.getEntityPos();

        boolean damaged = observed > 0f || MusicTracker.tookDamagePacketRecently();
        float before = previousHealth >= 0f ? previousHealth : currentHealth;

        boolean windCooling = self.getItemCooldownManager().isCoolingDown(Items.WIND_CHARGE.getDefaultStack());
        boolean ownWindCharge = BattleTracker.isInBattle() && mc.world != null && !mc.world.getEntitiesByClass(
                WindChargeEntity.class,
                self.getBoundingBox().expand(WIND_CHARGE_SEARCH_RADIUS),
                w -> w.getOwner() == self
        ).isEmpty();
        if ((windCooling && !lastWindCooling) || ownWindCharge) ticksSinceWindCharge = 0;
        else if (ticksSinceWindCharge < Integer.MAX_VALUE / 2) ticksSinceWindCharge++;
        lastWindCooling = windCooling;

        boolean landed = self.isOnGround() && lastFallDistance > 0.0;
        boolean smashedThisFall = self.isOnGround() && DamageEstimator.consumeOwnMaceSmash();
        boolean fallMitigated = smashedThisFall
                || ticksSinceWindCharge <= airTicks + WIND_CHARGE_SLACK_TICKS
                || self.hasStatusEffect(StatusEffects.SLOW_FALLING)
                || self.isTouchingWater();
        airTicks = self.isOnGround() ? 0 : airTicks + 1;
        double fallDamage = landed ? IncomingDamageEstimator.ownFallDamage(self, lastFallDistance) : 0.0;
        boolean lethalFall = landed && !damaged && !fallMitigated
                && fallDamage >= currentHealth + self.getAbsorptionAmount();
        boolean inVoid     = mc.world != null && self.getY() < mc.world.getBottomY();
        boolean lowHit     = damaged && before > 0f && before <= LETHAL_HEALTH_THRESHOLD;

        if (lethalWatchTicks > 0 && lethalWatchFromFall && damaged) {
            lethalWatchTicks    = 0;
            lethalWatchFromFall = false;
            lethalWatchEstimate = 0f;
            lethalWatchObserved = 0f;
        }

        if ((lowHit || lethalFall || inVoid) && !lastOffhandWasTotem) {
            lethalWatchObserved = lethalWatchTicks > 0 ? lethalWatchObserved + observed : observed;
            lethalWatchFromFall = lethalFall && !lowHit && !inVoid;
            lethalWatchTicks    = LETHAL_WATCH_TICKS;
            lethalWatchEstimate = Math.max(lethalWatchEstimate, IncomingDamageEstimator.recentEstimate());
        }

        boolean pearlCooling = self.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL.getDefaultStack());
        boolean watching = lethalWatchTicks > 0 || BattleTracker.isInBattle();
        boolean ownPearlFlying = watching && mc.world != null && !mc.world.getEntitiesByClass(
                EnderPearlEntity.class,
                self.getBoundingBox().expand(PEARL_SEARCH_RADIUS),
                p -> p.getOwner() == self
        ).isEmpty();
        if ((pearlCooling && !lastPearlCooling) || ownPearlFlying) ticksSincePearl = 0;
        else if (ticksSincePearl < PEARL_GRACE_TICKS) ticksSincePearl++;
        lastPearlCooling = pearlCooling;

        boolean potionNearby = watching && mc.world != null && !mc.world.getEntitiesByClass(
                PotionEntity.class,
                self.getBoundingBox().expand(POTION_NEARBY_RADIUS),
                p -> true
        ).isEmpty();
        if (potionNearby) ticksSincePotion = 0;
        else if (ticksSincePotion < POTION_GRACE_TICKS) ticksSincePotion++;

        if (lethalWatchTicks > 0) {
            float maxHealth = self.getMaxHealth();
            boolean healed = ticksSincePotion >= POTION_GRACE_TICKS
                    && currentHealth - before >= maxHealth * RESET_HEAL_FRACTION
                    && currentHealth >= maxHealth - 0.5f;
            boolean worldChanged = lastWorld != null && mc.world != lastWorld;
            boolean teleported = ticksSincePearl >= PEARL_GRACE_TICKS
                    && lastPos != null
                    && pos.squaredDistanceTo(lastPos) > LETHAL_TELEPORT_DISTANCE * LETHAL_TELEPORT_DISTANCE;

            if (healed || worldChanged || teleported || self.isSpectator()) {
                float unseen = lethalWatchEstimate - lethalWatchObserved;
                if (unseen > 0f && BattleTracker.isInBattle()) {
                    BattleTracker.registerDamageTaken(unseen);
                }
                MusicTracker.onLocalPresumedDeath();
                if (BattleTracker.isInBattle()) StatsManager.triggerDefeat();
                lethalWatchTicks = 0;
            } else {
                lethalWatchTicks--;
            }
            if (lethalWatchTicks == 0) {
                lethalWatchEstimate = 0f;
                lethalWatchObserved = 0f;
                lethalWatchFromFall = false;
            }
        }

        lastWorld        = mc.world;
        lastPos          = pos;
        lastFallDistance = self.fallDistance;
    }
}

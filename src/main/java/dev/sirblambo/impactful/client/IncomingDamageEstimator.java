package dev.sirblambo.impactful.client;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.KineticWeaponComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class IncomingDamageEstimator {

    private static final double MACE_SMASH_MIN_FALL     = 1.5;
    private static final int    ASSUMED_DENSITY_LEVEL   = 3;
    private static final int    ASSUMED_SHARPNESS_LEVEL = 3;
    private static final int    ESTIMATE_WINDOW_TICKS   = 5;
    private static final double FALLBACK_RADIUS         = 4.0;

    private static final Map<UUID, Double> fallDistance = new HashMap<>();
    private static final Map<UUID, Double> lastY        = new HashMap<>();

    private static long  clientTicks      = 0;
    private static float lastEstimate     = 0f;
    private static long  lastEstimateTick = -ESTIMATE_WINDOW_TICKS - 1;

    public static float ownFallDamage(LivingEntity self, double fallDistance) {
        double unsafe = fallDistance - self.getAttributeValue(EntityAttributes.SAFE_FALL_DISTANCE);
        float damage = (float) Math.floor(unsafe * self.getAttributeValue(EntityAttributes.FALL_DAMAGE_MULTIPLIER));
        if (damage <= 0f) return 0f;

        StatusEffectInstance resistance = self.getStatusEffect(StatusEffects.RESISTANCE);
        if (resistance != null) {
            int reduction = (resistance.getAmplifier() + 1) * 5;
            damage = Math.max(damage * (25 - reduction) / 25f, 0f);
        }

        int protection = 0;
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (!slot.isArmorSlot()) continue;
            for (Object2IntMap.Entry<RegistryEntry<Enchantment>> e
                    : self.getEquippedStack(slot).getEnchantments().getEnchantmentEntries()) {
                if (e.getKey().matchesKey(Enchantments.FEATHER_FALLING)) protection += 3 * e.getIntValue();
                else if (e.getKey().matchesKey(Enchantments.PROTECTION)) protection += e.getIntValue();
            }
        }
        float f = Math.min(Math.max(protection, 0), 20);
        return damage * (1f - f / 25f);
    }

    public static void tick() {
        clientTicks++;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            fallDistance.clear();
            lastY.clear();
            return;
        }

        Set<UUID> seen = new HashSet<>();
        for (AbstractClientPlayerEntity p : client.world.getPlayers()) {
            if (p == client.player) continue;
            UUID id = p.getUuid();
            seen.add(id);
            double y = p.getY();
            Double prevY = lastY.put(id, y);
            if (p.isOnGround() || p.isTouchingWater() || p.isClimbing() || p.isGliding()) {
                fallDistance.put(id, 0.0);
            } else if (prevY != null && y < prevY) {
                fallDistance.merge(id, prevY - y, Double::sum);
            }
        }
        fallDistance.keySet().retainAll(seen);
        lastY.keySet().retainAll(seen);
    }

    public static void onDamagePacket(PlayerEntity attacker, RegistryEntry<DamageType> type) {
        lastEstimate     = attacker == null ? 0f : estimate(attacker, type);
        lastEstimateTick = clientTicks;
    }

    public static float recentEstimate() {
        if (clientTicks - lastEstimateTick <= ESTIMATE_WINDOW_TICKS) return lastEstimate;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return 0f;
        PlayerEntity nearest = null;
        double best = FALLBACK_RADIUS * FALLBACK_RADIUS;
        for (AbstractClientPlayerEntity p : client.world.getPlayers()) {
            if (p == client.player || p.isSpectator()) continue;
            double d = p.squaredDistanceTo(client.player);
            if (d <= best) {
                best = d;
                nearest = p;
            }
        }
        return nearest == null ? 0f : estimate(nearest, null);
    }

    public static float estimate(PlayerEntity attacker, RegistryEntry<DamageType> type) {
        ItemStack weapon = attacker.getMainHandStack();
        boolean mace  = weapon.isOf(Items.MACE);
        boolean spear = weapon.contains(DataComponentTypes.KINETIC_WEAPON);
        if (!mace && !spear) return 0f;

        boolean enchanted = weapon.hasEnchantments();

        boolean charge = type != null ? type.matchesKey(DamageTypes.SPEAR) : attacker.isUsingItem();
        if (spear && charge) {
            return (float) chargeDamage(attacker, weapon, enchanted);
        }

        double fall = fallDistance.getOrDefault(attacker.getUuid(), 0.0);
        double damage = weaponAttack(weapon);
        if (fall > 0.0) damage *= 1.5;

        if (spear && enchanted) damage += sharpnessBonus(ASSUMED_SHARPNESS_LEVEL);

        if (mace && fall > MACE_SMASH_MIN_FALL && !attacker.isGliding()) {
            damage += DamageEstimator.maceFallBonus(fall);
            if (enchanted) damage += ASSUMED_DENSITY_LEVEL * 0.5 * fall;
        }

        return (float) Math.max(0.0, damage);
    }

    private static double chargeDamage(PlayerEntity attacker, ItemStack weapon, boolean enchanted) {
        KineticWeaponComponent kinetic = weapon.get(DataComponentTypes.KINETIC_WEAPON);
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d look = attacker.getRotationVector();
        double attackerSpeed = look.dotProduct(movement(attacker));
        double targetSpeed   = client.player != null ? look.dotProduct(movement(client.player)) : 0.0;
        double relative      = Math.max(0.0, attackerSpeed - targetSpeed);

        double damage = attacker.getAttributeBaseValue(EntityAttributes.ATTACK_DAMAGE)
                + Math.floor(relative * kinetic.damageMultiplier());
        if (enchanted) damage += sharpnessBonus(ASSUMED_SHARPNESS_LEVEL);
        return damage;
    }

    private static Vec3d movement(Entity e) {
        Entity root = e.hasVehicle() ? e.getRootVehicle() : e;
        return new Vec3d(root.getX() - root.lastX, root.getY() - root.lastY, root.getZ() - root.lastZ)
                .multiply(20.0);
    }

    private static double weaponAttack(ItemStack weapon) {
        return weapon.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT)
                .applyOperations(EntityAttributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
    }

    private static double sharpnessBonus(int level) {
        return level > 0 ? 0.5 * level + 0.5 : 0.0;
    }

    public static void reset() {
        fallDistance.clear();
        lastY.clear();
        lastEstimate     = 0f;
        lastEstimateTick = -ESTIMATE_WINDOW_TICKS - 1;
    }
}

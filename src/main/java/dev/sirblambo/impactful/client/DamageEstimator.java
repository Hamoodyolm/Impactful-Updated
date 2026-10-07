package dev.sirblambo.impactful.client;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

public class DamageEstimator {

    private static final double MACE_SMASH_MIN_FALL      = 1.5;
    private static final int    ASSUMED_DENSITY_LEVEL    = 3;
    private static final double DENSITY_DAMAGE_PER_LEVEL = 0.5;

    private static boolean ownMaceSmashThisFall = false;

    public static void markOwnMaceSmash() {
        ownMaceSmashThisFall = true;
    }

    public static boolean consumeOwnMaceSmash() {
        boolean smashed = ownMaceSmashThisFall;
        ownMaceSmashThisFall = false;
        return smashed;
    }

    public static float estimate(PlayerEntity attacker, LivingEntity target,
                                 boolean isCrit, boolean isMaceSmash) {
        double base = attacker.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);

        if (isCrit) base *= 1.5;

        ItemStack weapon = attacker.getMainHandStack();
        double fall = attacker.fallDistance;
        if (isMaceSmash && weapon.isOf(Items.MACE) && fall > MACE_SMASH_MIN_FALL && !attacker.isGliding()) {
            base += maceFallBonus(fall);
            if (weapon.hasEnchantments()) {
                base += ASSUMED_DENSITY_LEVEL * DENSITY_DAMAGE_PER_LEVEL * fall;
            }
        }

        return (float) Math.max(0.0, base);
    }

    public static boolean isBlockedByShield(PlayerEntity attacker, LivingEntity target) {
        if (!target.isBlocking()) return false;
        Vec3d look = target.getRotationVector(0.0F, target.getHeadYaw());
        Vec3d diff = attacker.getEntityPos().subtract(target.getEntityPos());
        Vec3d flat = new Vec3d(diff.x, 0.0, diff.z);
        if (flat.lengthSquared() < 1.0E-6) return true;
        return flat.normalize().dotProduct(look) >= 0.0;
    }

    static double maceFallBonus(double fallDistance) {
        double d = Math.max(0.0, fallDistance);
        double first  = Math.min(d, 3.0) * 4.0;
        double second = Math.min(Math.max(d - 3.0, 0.0), 5.0) * 2.0;
        double third  = Math.max(d - 8.0, 0.0) * 1.0;
        return first + second + third;
    }
}

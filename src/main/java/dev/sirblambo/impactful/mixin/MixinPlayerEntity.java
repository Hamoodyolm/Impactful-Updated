package dev.sirblambo.impactful.mixin;

import dev.sirblambo.impactful.client.DamageEstimator;
import dev.sirblambo.impactful.client.KillDetector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public class MixinPlayerEntity {

    @Inject(method = "attack", at = @At("HEAD"))
    private void onAttack(Entity target, CallbackInfo ci) {
        if (!(target instanceof LivingEntity living)) return;
        PlayerEntity self = (PlayerEntity)(Object) this;

        boolean isCrit = self.fallDistance > 0.0F && !self.isOnGround()
                && !self.isClimbing() && !self.isTouchingWater()
                && !self.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS);

        boolean isMaceSmash = self.fallDistance > 0.0F && !self.isOnGround()
                && self.getMainHandStack().isOf(net.minecraft.item.Items.MACE);

        if (isMaceSmash && self.fallDistance > 1.5 && !self.isGliding()
                && self == MinecraftClient.getInstance().player) {
            DamageEstimator.markOwnMaceSmash();
        }

        boolean isPlayerTarget  = living instanceof PlayerEntity;
        boolean isHostileTarget = living instanceof HostileEntity;

        float estDamage = DamageEstimator.isBlockedByShield(self, living)
                ? 0f
                : DamageEstimator.estimate(self, living, isCrit, isMaceSmash);
        KillDetector.registerHit(living.getUuid(), isCrit, isMaceSmash,
                living.getName().getString(), isPlayerTarget, isHostileTarget, estDamage);
        KillDetector.registerHitAndCheckAnyHit(living.getUuid(), isCrit, living);
    }
}

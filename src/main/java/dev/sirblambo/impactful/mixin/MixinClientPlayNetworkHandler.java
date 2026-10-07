package dev.sirblambo.impactful.mixin;

import dev.sirblambo.impactful.client.BattleTracker;
import dev.sirblambo.impactful.client.IncomingDamageEstimator;
import dev.sirblambo.impactful.client.MusicTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientPlayNetworkHandler {

    @Inject(method = "onEntityDamage", at = @At("TAIL"))
    private void onEntityDamage(EntityDamageS2CPacket packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (packet.entityId() != client.player.getId()) return;

        Entity attacker = client.world.getEntityById(packet.sourceCauseId());
        PlayerEntity playerAttacker = attacker instanceof PlayerEntity p && p != client.player ? p : null;
        if (playerAttacker != null) {
            BattleTracker.onFirstHit();
            BattleTracker.addOpponent(playerAttacker.getUuid(), playerAttacker.getName().getString());
        }
        IncomingDamageEstimator.onDamagePacket(playerAttacker, packet.sourceType());
        MusicTracker.onLocalDamagePacket(playerAttacker != null);
    }
}

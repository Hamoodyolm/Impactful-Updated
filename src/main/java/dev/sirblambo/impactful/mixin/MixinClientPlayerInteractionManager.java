package dev.sirblambo.impactful.mixin;

import dev.sirblambo.impactful.client.BattleTracker;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class MixinClientPlayerInteractionManager {

    @Inject(method = "setGameMode", at = @At("HEAD"))
    private void onGameModeChange(GameMode gameMode, CallbackInfo ci) {
        if (gameMode == GameMode.SPECTATOR) BattleTracker.onLocalSpectator();
    }
}
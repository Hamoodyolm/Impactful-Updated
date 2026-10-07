package dev.sirblambo.impactful.mixin;

import dev.sirblambo.impactful.client.PauseMusicDucker;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinPauseDucker {

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onScreenOpen(net.minecraft.client.gui.screen.Screen screen, CallbackInfo ci) {
        if (screen != null && screen instanceof net.minecraft.client.gui.screen.GameMenuScreen) {
            PauseMusicDucker.onPause();
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onScreenClose(net.minecraft.client.gui.screen.Screen screen, CallbackInfo ci) {
        if (screen == null) {
            PauseMusicDucker.onResume();
        }
    }
}
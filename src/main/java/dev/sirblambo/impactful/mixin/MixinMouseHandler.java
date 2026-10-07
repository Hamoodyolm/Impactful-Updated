package dev.sirblambo.impactful.mixin;

import dev.sirblambo.impactful.client.FlashController;
import dev.sirblambo.impactful.config.ModConfig;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MixinMouseHandler {

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void freezeCamera(CallbackInfo ci) {
        if (FlashController.isActive() && ModConfig.get().freezeCamera) {
            ci.cancel();
        }
    }
}

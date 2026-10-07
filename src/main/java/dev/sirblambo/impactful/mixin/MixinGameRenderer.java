package dev.sirblambo.impactful.mixin;

import dev.sirblambo.impactful.client.FrozenView;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Inject(method = "renderWorld", at = @At("HEAD"), cancellable = true)
    private void drawFrozenView(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (FrozenView.drawFrozen()) ci.cancel();
    }

    @Inject(method = "renderWorld", at = @At("TAIL"))
    private void captureFrozenView(RenderTickCounter tickCounter, CallbackInfo ci) {
        FrozenView.captureIfNeeded();
    }
}

package dev.sirblambo.impactful.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.sirblambo.impactful.client.FlashController;
import dev.sirblambo.impactful.client.HitColorRenderer;
import dev.sirblambo.impactful.config.ModConfig;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {

    @Inject(method = "render", at = @At("TAIL"))
    private void afterRender(ObjectAllocator allocator, RenderTickCounter tickCounter,
                             boolean renderBlockOutline, Camera camera,
                             Matrix4f positionMatrix, Matrix4f basicProjectionMatrix,
                             Matrix4f projectionMatrix, GpuBufferSlice fogBuffer,
                             Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        if (FlashController.isActive() && ModConfig.get().flashHitEntity) {
            HitColorRenderer.applyPostEffect();
        }
    }
}
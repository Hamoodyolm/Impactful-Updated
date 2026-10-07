package dev.sirblambo.impactful.mixin;

import dev.sirblambo.impactful.client.PresenceClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public class MixinPlayerListHud {

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void addImpactfulIcon(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        cir.setReturnValue(PresenceClient.decorate(cir.getReturnValue(), entry.getProfile().id()));
    }
}

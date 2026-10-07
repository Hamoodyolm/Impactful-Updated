package dev.sirblambo.impactful.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import java.util.UUID;

public class HitColorRenderer {

    public static void setVictimGlowing(UUID uuid, boolean glowing) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        client.world.getEntitiesByClass(LivingEntity.class,
                client.player.getBoundingBox().expand(64.0),
                e -> e.getUuid().equals(uuid)
        ).stream().findFirst().ifPresent(entity -> {
            entity.setGlowing(glowing);
        });
    }

    public static void onReset() {
        UUID uuid = FlashController.getVictimUuid();
        if (uuid != null) {
            setVictimGlowing(uuid, false);
        }
    }

    public static void applyPostEffect() {
    }
}
package dev.sirblambo.impactful.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record KillFlashPayload(UUID victimUuid) implements CustomPayload {
    public static final CustomPayload.Id<KillFlashPayload> ID =
            new CustomPayload.Id<>(Identifier.of("impactful", "kill_flash"));

    public static final PacketCodec<PacketByteBuf, KillFlashPayload> CODEC =
            PacketCodec.of(
                    (value, buf) -> buf.writeUuid(value.victimUuid()),
                    (buf) -> new KillFlashPayload(buf.readUuid())
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
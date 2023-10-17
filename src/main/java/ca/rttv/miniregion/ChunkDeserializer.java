package ca.rttv.miniregion;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

public interface ChunkDeserializer {
    NbtCompound fromBuf(PacketByteBuf buf);

    static Identifier readIdentifier(PacketByteBuf buf) {
        var bytes = new byte[buf.bytesBefore((byte) 0)];
        buf.readBytes(bytes);
        buf.skipBytes(1);
        return new Identifier(new String(bytes, StandardCharsets.UTF_8));
    }
}

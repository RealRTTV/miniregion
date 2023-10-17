package ca.rttv.miniregion;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

public interface ChunkSerializer {
    PacketByteBuf toBuf(NbtCompound compound);

    static void writeIdentifier(Identifier identifier, PacketByteBuf buf) {
        if (identifier.getNamespace().equals("minecraft")) {
            buf.writeBytes(identifier.getPath().getBytes(StandardCharsets.UTF_8));
        } else {
            buf.writeBytes(identifier.toString().getBytes(StandardCharsets.UTF_8));
        }
        buf.writeByte(0);
    }
}

package ca.rttv.miniregion;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.ChunkPos;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface Region {
	void writeChunk(ChunkPos pos, ByteBuffer buffer) throws IOException;

	NbtCompound toCompound(ChunkPos pos) throws IOException;
}

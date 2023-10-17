package ca.rttv.miniregion.mixin;

import ca.rttv.miniregion.chunk.spec_3578;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.world.ThreadedChunkManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

@Mixin(ThreadedChunkManager.class)
abstract class ThreadedChunkManagerMixin {
	@Inject(method = "save(Lnet/minecraft/world/chunk/Chunk;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ThreadedChunkManager;setNbt(Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/nbt/NbtCompound;)V"), locals = LocalCapture.CAPTURE_FAILSOFT)
	private void save(Chunk chunk, CallbackInfoReturnable<Boolean> cir, ChunkPos chunkPos, ChunkStatus chunkStatus, NbtCompound nbtCompound) {
		PacketByteBuf original = new PacketByteBuf(Unpooled.buffer());
		original.writeNbt(nbtCompound);
		PacketByteBuf mini = new spec_3578().toBuf(nbtCompound);
		try {
			ByteArrayOutputStream bos1 = new ByteArrayOutputStream(original.readableBytes());
			GZIPOutputStream og = new GZIPOutputStream(bos1);
			og.write(original.array(), 0, original.readableBytes());
			og.close();
			int originalSize = bos1.toByteArray().length;

			ByteArrayOutputStream bos2 = new ByteArrayOutputStream(mini.readableBytes());
			GZIPOutputStream mi = new GZIPOutputStream(bos2);
			mi.write(mini.array(), 0, mini.readableBytes());
			mi.close();
			int newSize = bos2.toByteArray().length;

			System.out.printf("%08d bytes | %08d bytes | %.2f%% reduction%n", originalSize, newSize, (double) (originalSize - newSize) * 100.0d / (double) originalSize);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}

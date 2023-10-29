package ca.rttv.miniregion.mixin;

import ca.rttv.miniregion.chunk.spec_3578;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.world.ThreadedChunkManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.quiltmc.loader.api.QuiltLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

@Mixin(ThreadedChunkManager.class)
abstract class ThreadedChunkManagerMixin {
	@Unique
	AtomicInteger bytes = new AtomicInteger(0);
	@Unique
	AtomicInteger blocks = new AtomicInteger(0);

	@Unique
	AtomicInteger reduced = new AtomicInteger(0);
	@Unique
	AtomicInteger enlarged = new AtomicInteger(0);

	@Inject(method = "save(Lnet/minecraft/world/chunk/Chunk;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ThreadedChunkManager;setNbt(Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/nbt/NbtCompound;)V"), locals = LocalCapture.CAPTURE_FAILSOFT)
	private void save(Chunk chunk, CallbackInfoReturnable<Boolean> cir, ChunkPos chunkPos, ChunkStatus chunkStatus, NbtCompound nbt) {
		if (!nbt.contains("FullSerialization") || !QuiltLoader.isDevelopmentEnvironment()) {
			return;
		}

		PacketByteBuf original = new PacketByteBuf(Unpooled.buffer());
		original.writeNbt(nbt);
		PacketByteBuf mini = new spec_3578().toBuf(nbt);
//		PacketByteBuf copy = new PacketByteBuf(Unpooled.copiedBuffer(mini.array(), mini.arrayOffset(), mini.readableBytes()));
//		copy.readVarInt(); // for removing the version
//		NbtCompound mine = new spec_3578().fromBuf(copy);
//		if (!mine.equals(nbt)) {
//			System.out.println("==========");
//			System.out.println(nbt);
//			System.out.println(mine);
//			System.out.println("==========");
//			System.exit(1);
//		}
		try {
			ByteArrayOutputStream bos1 = new ByteArrayOutputStream(original.readableBytes());
			GZIPOutputStream og = new GZIPOutputStream(bos1);
			og.write(original.array(), original.arrayOffset(), original.readableBytes());
			og.close();
			int originalSize = bos1.toByteArray().length;
			int roundedSize = (originalSize + 4095) & (~4095);

			ByteArrayOutputStream bos2 = new ByteArrayOutputStream(mini.readableBytes());
			GZIPOutputStream mi = new GZIPOutputStream(bos2);
			mi.write(mini.array(), mini.arrayOffset(), mini.readableBytes());
			mi.close();
			int newSize = bos2.toByteArray().length;
			int newRoundedSize = (newSize + 1023) & (~1023);

			double reduction = (double) (originalSize - newSize) * 100.0d / (double) originalSize;
			int blocks2 = blocks.addAndGet(roundedSize);
			int bytes2 = bytes.addAndGet(newRoundedSize);
			int reduced2 = reduced.addAndGet(newSize);
			int enlarged2 = enlarged.addAndGet(originalSize);

			System.out.printf("%05d bytes | %05d bytes | %05.2f%% reduction | %05.2f%% global reduction | %05.2f%% global rounded reduction%n", originalSize, newSize, reduction, 100.0d * (double) (enlarged2 - reduced2) / (double) enlarged2, 100.0d * (double) (blocks2 - bytes2) / (double) blocks2);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}

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

	@Inject(method = "save(Lnet/minecraft/world/chunk/Chunk;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ThreadedChunkManager;setNbt(Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/nbt/NbtCompound;)V"), locals = LocalCapture.CAPTURE_FAILSOFT)
	private void save(Chunk chunk, CallbackInfoReturnable<Boolean> cir, ChunkPos chunkPos, ChunkStatus chunkStatus, NbtCompound nbt) {
		if (!nbt.contains("Status") || !QuiltLoader.isDevelopmentEnvironment()) {
			return;
		}

		PacketByteBuf original = new PacketByteBuf(Unpooled.buffer());
		original.writeNbt(nbt);
		PacketByteBuf mini = new spec_3578().toBuf(nbt);
		byte[] copyUnderlying = new byte[mini.readableBytes()];
		System.arraycopy(mini.array(), 0, copyUnderlying, 0, mini.readableBytes());
		PacketByteBuf copy = new PacketByteBuf(Unpooled.buffer(mini.readableBytes()));
		copy.writeBytes(copyUnderlying);
		copy.readByte(); // for removing the annotator
		copy.readVarInt(); // for removing the version
		NbtCompound out = new spec_3578().fromBuf(copy);
		if (!out.equals(nbt)) {
			System.out.println("==========");
			System.out.println(nbt);
			System.out.println(out);
			System.out.println("==========");
			System.exit(1);
		}
		try {
			ByteArrayOutputStream bos1 = new ByteArrayOutputStream(original.readableBytes());
			GZIPOutputStream og = new GZIPOutputStream(bos1);
			og.write(original.array(), 0, original.readableBytes());
			og.close();
			int originalSize = bos1.toByteArray().length;
			int roundedSize = (originalSize + 4095) & (~4095);

			ByteArrayOutputStream bos2 = new ByteArrayOutputStream(mini.readableBytes());
			GZIPOutputStream mi = new GZIPOutputStream(bos2);
			mi.write(mini.array(), 0, mini.readableBytes());
			mi.close();
			int newSize = bos2.toByteArray().length;
			int newRoundedSize = (newSize + 1023) & (~1023);

			double reduction = (double) (originalSize - newSize) * 100.0d / (double) originalSize;
			blocks.addAndGet(roundedSize);
			bytes.addAndGet(newRoundedSize);

			System.out.printf("%05d bytes | %05d bytes | %05.2f%% reduction | %05.2f%% global reduction%n", originalSize, newSize, reduction, 100.0d * (double) (blocks.get() - bytes.get()) / (double) bytes.get());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}

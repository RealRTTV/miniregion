package ca.rttv.miniregion.mixin;

import ca.rttv.miniregion.chunk.spec_3578;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtTagSizeTracker;
import net.minecraft.nbt.scanner.NbtScanner;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.world.storage.RegionBasedStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.*;

@Mixin(RegionBasedStorage.class)
abstract class RegionBasedStorageMixin {
	@Redirect(method = "getTagAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NbtIo;read(Ljava/io/DataInput;)Lnet/minecraft/nbt/NbtCompound;"))
	private NbtCompound getTagAt(DataInput stream) throws IOException {
		byte[] bytes = ((DataInputStream) stream).readAllBytes();
		if (bytes[0] == 0x6D) {
			PacketByteBuf buf = new PacketByteBuf(Unpooled.wrappedBuffer(bytes));
			buf.readByte();
			buf.readVarInt();
			return new spec_3578().fromBuf(buf);
		} else {
			return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)));
		}
	}

	@Redirect(method = "scanChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NbtIo;read(Ljava/io/DataInput;Lnet/minecraft/nbt/scanner/NbtScanner;Lnet/minecraft/nbt/NbtTagSizeTracker;)V"))
	private void scanChunk(DataInput stream, NbtScanner scanner, NbtTagSizeTracker nbtTagSizeTracker) throws IOException {
		byte[] bytes = ((DataInputStream) stream).readAllBytes();
		if (bytes[0] == 0x6D) {
			PacketByteBuf buf = new PacketByteBuf(Unpooled.wrappedBuffer(bytes));
			buf.readByte();
			buf.readVarInt();
			new spec_3578().fromBuf(buf).scanAsRoot(scanner);
		} else {
			NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)), scanner, NbtTagSizeTracker.method_53898());
		}
	}

	@Redirect(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NbtIo;write(Lnet/minecraft/nbt/NbtCompound;Ljava/io/DataOutput;)V"))
	private void write(NbtCompound nbt, DataOutput stream) throws IOException {
		if (nbt.contains("Status")) {
			PacketByteBuf buf = new spec_3578().toBuf(nbt);
			stream.write(buf.array(), buf.arrayOffset(), buf.readableBytes());
		} else {
			NbtIo.write(nbt, stream);
		}
	}
}

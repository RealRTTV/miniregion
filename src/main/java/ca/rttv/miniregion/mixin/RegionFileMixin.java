package ca.rttv.miniregion.mixin;

import net.minecraft.world.storage.ChunkStreamVersion;
import net.minecraft.world.storage.RegionFile;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.nio.IntBuffer;
import java.nio.file.Path;

@Mixin(RegionFile.class)
abstract class RegionFileMixin {
	@Shadow
	@Final
	private IntBuffer sectorData;

	@Shadow
	private static native int getOffset(int sectorData);

	@Shadow
	private static native int getSize(int sectorData);

	@Shadow
	private native int packSectorData(int offset, int size);

	@Unique
	private static final int BLOCK_SIZE = 1024;

	@Unique
	private boolean translateFromMca = false;

	@ModifyConstant(method = "<init>(Ljava/nio/file/Path;Ljava/nio/file/Path;Lnet/minecraft/world/storage/ChunkStreamVersion;Z)V", constant = @Constant(longValue = 4096, ordinal = 0))
	private long modifyInvalidBlockErrorMultiplier(long constant) {
		return BLOCK_SIZE;
	}

	@ModifyConstant(method = {"getChunkInputStream", "isChunkValid", "writeChunk", "fillLastSector"}, constant = @Constant(intValue = 4096))
	private int modifyBlockMultiplierDynamic(int constant) {
		return BLOCK_SIZE;
	}

	@ModifyConstant(method = "getSectorCount", constant = @Constant(intValue = 4096))
	private static int modifyBlockMultiplierStatic(int constant) {
		return BLOCK_SIZE;
	}

	@ModifyArg(method = "<init>(Ljava/nio/file/Path;Ljava/nio/file/Path;Lnet/minecraft/world/storage/ChunkStreamVersion;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/storage/SectorMap;allocate(II)V", ordinal = 0), index = 1)
	private int modifyFileReadOffset(int size) {
		return 8192 / BLOCK_SIZE;
	}

	@ModifyVariable(method = "<init>(Ljava/nio/file/Path;Ljava/nio/file/Path;Lnet/minecraft/world/storage/ChunkStreamVersion;Z)V", at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;isDirectory(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z"), index = 1, argsOnly = true)
	private Path modifyFileType(Path path) {
		String str = path.toString();
		String mcz = str.substring(0, str.length() - 4).concat(".mcz");
		File f = new File(str);
		if (f.exists()) {
			translateFromMca = true;
			f.renameTo(new File(mcz));
		}
		return Path.of(mcz);
	}

	@Inject(method = "<init>(Ljava/nio/file/Path;Ljava/nio/file/Path;Lnet/minecraft/world/storage/ChunkStreamVersion;Z)V", at = @At(value = "INVOKE_ASSIGN", target = "Ljava/nio/channels/FileChannel;read(Ljava/nio/ByteBuffer;J)I"))
	private void setTranslateFromMca(Path file, Path directory, ChunkStreamVersion outputChunkStreamVersion, boolean dsync, CallbackInfo ci) {
		if (translateFromMca) {
			translateFromMca = false;
			for (int i = 0; i < 1024; i++) {
				int data = sectorData.get(i);
				if (data != 0) {
					int offset = getOffset(data);
					int size = getSize(data);
					sectorData.put(i, packSectorData(offset * (4096 / BLOCK_SIZE), size * (4096 / BLOCK_SIZE)));
				}
			}
		}
	}
}

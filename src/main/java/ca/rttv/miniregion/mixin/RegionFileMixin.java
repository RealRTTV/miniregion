package ca.rttv.miniregion.mixin;

import net.minecraft.world.storage.RegionFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(RegionFile.class)
abstract class RegionFileMixin {
	@Unique
	private static final int BLOCK_SIZE = 1024;

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
}

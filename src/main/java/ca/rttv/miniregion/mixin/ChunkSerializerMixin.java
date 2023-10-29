package ca.rttv.miniregion.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.world.gen.BelowZeroRetrogen;
import net.minecraft.world.gen.chunk.BlendingData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ChunkSerializer.class)
abstract class ChunkSerializerMixin {
	@Inject(method = "serialize", at = @At("TAIL"), locals = LocalCapture.CAPTURE_FAILEXCEPTION)
	private static void serialize(ServerWorld world, Chunk chunk, CallbackInfoReturnable<NbtCompound> cir, ChunkPos chunkPos, NbtCompound nbt, BlendingData blendingData, BelowZeroRetrogen belowZeroRetrogen, UpgradeData upgradeData, ChunkSection[] chunkSections, NbtList nbtList, LightingProvider lightingProvider, Registry<?> registry, Codec<?> codec, boolean bl, NbtList nbtList2, NbtCompound nbtCompound4) {
		nbt.putBoolean("FullSerialization", true);
	}
}

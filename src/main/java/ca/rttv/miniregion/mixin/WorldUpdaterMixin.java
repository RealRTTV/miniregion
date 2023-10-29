package ca.rttv.miniregion.mixin;

import net.minecraft.world.updater.WorldUpdater;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.io.FilenameFilter;

@Mixin(WorldUpdater.class)
abstract class WorldUpdaterMixin {
	@ModifyArg(method = "getChunkPositions", at = @At(value = "INVOKE", target = "Ljava/io/File;listFiles(Ljava/io/FilenameFilter;)[Ljava/io/File;"), index = 0)
	private FilenameFilter getChunkPositions(FilenameFilter old) {
		return (dir, name) -> old.accept(dir, name) || name.endsWith(".mcz");
	}

	@ModifyArg(method = "<clinit>", at = @At(value = "INVOKE", target = "Ljava/util/regex/Pattern;compile(Ljava/lang/String;)Ljava/util/regex/Pattern;"), index = 0)
	private static String regionRegex(String regex) {
		return "^r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mc[az]$";
	}
}

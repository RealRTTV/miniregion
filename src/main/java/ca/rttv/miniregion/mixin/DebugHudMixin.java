package ca.rttv.miniregion.mixin;

import net.minecraft.client.gui.hud.debug.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(DebugHud.class)
abstract class DebugHudMixin {
	@ModifyArg(method = "getLeftText", at = @At(value = "INVOKE", target = "Ljava/lang/String;format(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", ordinal = 5), index = 1)
	private String modifyChunkText(String format) {
		return "Chunk: %d %d %d [%d %d in r.%d.%d.mcz]";
	}
}

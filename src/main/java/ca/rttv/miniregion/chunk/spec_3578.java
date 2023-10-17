package ca.rttv.miniregion.chunk;

import ca.rttv.miniregion.ChunkDeserializer;
import ca.rttv.miniregion.ChunkSerializer;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.GenerationStep;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.Set;

public class spec_3578 implements ChunkSerializer, ChunkDeserializer {

	@Override
	public NbtCompound fromBuf(PacketByteBuf buf) {
		return null;
	}

	@Override
	public PacketByteBuf toBuf(NbtCompound compound) {
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer(4096));

		buf.writeVarInt(3578);

		buf.writeVarInt(compound.getInt("xPos"));
		buf.writeVarInt(compound.getInt("yPos"));
		buf.writeVarInt(compound.getInt("zPos"));
		buf.writeVarLong(compound.getLong("LastUpdate")); // might be useless
		buf.writeVarLong(compound.getLong("InhabitedTime"));
		buf.writeVarInt(chunkStatusFromName(compound.getString("Status")));

		writeBlendingData(buf, compound);
		writeBelowZeroRetrogen(buf, compound);
		writeUpgradeData(buf, compound);
		writeSections(buf, compound);

		buf.writeBoolean(compound.contains("isLightOn"));

		writeBlockEntities(buf, compound);
		if (!compound.getString("Status").equals("minecraft:full")) {
			writeProtoData(buf, compound);
		}

		writeTicking(buf, compound);

		NbtList postProcessing = compound.getList("PostProcessing", NbtElement.SHORT_TYPE);
		buf.writeVarInt(postProcessing.size());
		for (NbtElement s : postProcessing) {
			buf.writeShort(((NbtShort) s).shortValue());
		}

		writeHeightmaps(buf, compound);

		// todo, is lazy
		buf.writeNbt(compound.get("structures"));

		return buf;
	}

	protected int chunkStatusFromName(String status) {
		return switch (status) {
			case "minecraft:empty" -> 0;
			case "minecraft:structure_starts" -> 1;
			case "minecraft:structure_references" -> 2;
			case "minecraft:biomes" -> 3;
			case "minecraft:noise" -> 4;
			case "minecraft:surface" -> 5;
			case "minecraft:carvers" -> 6;
			case "minecraft:features" -> 7;
			case "minecraft:initialize_light" -> 8;
			case "minecraft:light" -> 9;
			case "minecraft:spawn" -> 10;
			case "minecraft:full" -> 11;
			default -> throw new IllegalStateException("Unexpected value: '" + status + "' for chunk status");
		};
	}

	protected String chunkStatusToName(int status) {
		return switch (status) {
			case 0 -> "minecraft:empty";
			case 1 -> "minecraft:structure_starts";
			case 2 -> "minecraft:structure_references";
			case 3 -> "minecraft:biomes";
			case 4 -> "minecraft:noise";
			case 5 -> "minecraft:surface";
			case 6 -> "minecraft:carvers";
			case 7 -> "minecraft:features";
			case 8 -> "minecraft:initialize_light";
			case 9 -> "minecraft:light";
			case 10 -> "minecraft:spawn";
			case 11 -> "minecraft:full";
			default -> throw new IllegalStateException("Unexpected value: '" + status + "' for chunk status");
		};
	}

	protected void writeBlendingData(PacketByteBuf buf, NbtCompound compound) {
		if (compound.get("blending_data") instanceof NbtCompound blendingData) {
			buf.writeBoolean(true);
			buf.writeVarInt(blendingData.getInt("min_section"));
			buf.writeVarInt(blendingData.getInt("max_section"));
			// always 16 in length
			for (NbtElement e : blendingData.getList("heights", NbtElement.DOUBLE_TYPE)) {
				buf.writeDouble(((NbtDouble) e).doubleValue());
			}
		} else {
			buf.writeBoolean(false);
		}
	}

	protected void writeBelowZeroRetrogen(PacketByteBuf buf, NbtCompound compound) {
		if (compound.get("below_zero_retrogen") instanceof NbtCompound belowZeroRetrogen) {
			buf.writeBoolean(true);
			buf.writeVarInt(chunkStatusFromName(belowZeroRetrogen.getString("target_status")));
			// always 256 bits in length
			long[] bits = belowZeroRetrogen.getLongArray("missing_bedrock");
			buf.writeLong(bits.length == 0 ? 0 : bits[0]);
			buf.writeLong(bits.length <= 1 ? 0 : bits[1]);
			buf.writeLong(bits.length <= 2 ? 0 : bits[2]);
			buf.writeLong(bits.length <= 3 ? 0 : bits[3]);
		} else {
			buf.writeBoolean(false);
		}
	}

	protected void writeUpgradeData(PacketByteBuf buf, NbtCompound compound) {
		if (compound.get("UpgradeData") instanceof NbtCompound upgradeData) {
			buf.writeBoolean(true);
			NbtCompound indices = upgradeData.getCompound("Indices");
			var ref = new Object() {
				int curr = 0;
			};
			// better to have here rather than trailing zeros, it's shorter in almost all cases
			buf.writeVarInt(indices.getSize());
			indices.getKeys().stream().mapToInt(Integer::parseInt).sorted().forEach(idx -> {
				while (ref.curr < idx) {
					buf.writeVarInt(0);
					ref.curr++;
				}
				buf.writeIntArray(indices.getIntArray(Integer.toString(idx)));
			});
			buf.writeByte(0);
		} else {
			buf.writeBoolean(false);
		}
	}

	// optimize later
	protected void writeSections(PacketByteBuf buf, NbtCompound compound) {
		NbtList sections = compound.getList("sections", NbtElement.COMPOUND_TYPE);
		buf.writeVarInt(sections.size());
		for (NbtElement e : sections) {
			NbtCompound section = (NbtCompound) e;
			buf.writeByte(section.getByte("Y"));
			buf.writeOptional(Optional.ofNullable(section.get("BlockLight")), (buff, light) -> buff.writeBytes(((NbtByteArray) light).getByteArray()));
			buf.writeOptional(Optional.ofNullable(section.get("SkyLight")), (buff, light) -> buff.writeBytes(((NbtByteArray) light).getByteArray()));
			if (section.get("block_states") instanceof NbtCompound states) {
				buf.writeBoolean(true);

				NbtList palette = states.getList("Palette", NbtElement.COMPOUND_TYPE);
				buf.writeVarInt(palette.size());
				if (palette.size() > 1) {
					// maybe unnessesary
					buf.writeVarInt(states.getLongArray("data").length);
					ByteBuffer buffer = ByteBuffer.allocate(8 * states.getLongArray("data").length);
					buffer.asLongBuffer().put(states.getLongArray("data"));
					buf.writeBytes(buffer.array());
				}
				for (NbtElement e2 : palette) {
					NbtCompound state = (NbtCompound) e2;
					ChunkSerializer.writeIdentifier(new Identifier(state.getString("Name")), buf);
					if (state.get("Properties") instanceof NbtCompound properties) {
						buf.writeVarInt(properties.getSize());
						for (String name : properties.getKeys()) {
							buf.writeString(name);
							buf.writeString(properties.getString(name));
						}
					} else {
						buf.writeVarInt(0);
					}
				}

				NbtCompound biomes = section.getCompound("biomes");
				NbtList biomePalette = biomes.getList("Palette", NbtElement.STRING_TYPE);
				buf.writeVarInt(biomePalette.size());
				if (biomePalette.size() > 1) {
					// maybe unnessesary
					buf.writeVarInt(biomes.getLongArray("data").length);
					ByteBuffer buffer = ByteBuffer.allocate(8 * biomes.getLongArray("data").length);
					buffer.asLongBuffer().put(biomes.getLongArray("data"));
					buf.writeBytes(buffer.array());
				}
				for (NbtElement e2 : biomePalette) {
					NbtString biome = (NbtString) e2;
					ChunkSerializer.writeIdentifier(new Identifier(biome.asString()), buf);
				}
			} else {
				buf.writeBoolean(false);
			}
		}
	}

	protected void writeBlockEntities(PacketByteBuf buf, NbtCompound compound) {
		NbtList blockEntities = compound.getList("block_entities", NbtElement.COMPOUND_TYPE);
		buf.writeVarInt(blockEntities.size());
		for (NbtElement blockEntity : blockEntities) {
			buf.writeNbt(blockEntity);
		}
	}

	protected void writeProtoData(PacketByteBuf buf, NbtCompound compound) {
		NbtList entities = compound.getList("entities", NbtElement.COMPOUND_TYPE);
		buf.writeVarInt(entities.size());
		for (NbtElement entity : entities) {
			buf.writeNbt(entity);
		}
		NbtCompound carvingMasks = compound.getCompound("CarvingMasks");
		for (GenerationStep.Carver carver : new GenerationStep.Carver[]{GenerationStep.Carver.AIR, GenerationStep.Carver.LIQUID}) {
			if (carvingMasks.get(carver.toString()) instanceof NbtLongArray array) {
				buf.writeBoolean(true);
				ByteBuffer buffer = ByteBuffer.allocate(8 * array.size());
				buffer.asLongBuffer().put(array.getLongArray());
				buf.writeBytes(buffer.array());
			} else {
				buf.writeBoolean(false);
			}
		}
	}

	protected void writeTicking(PacketByteBuf buf, NbtCompound compound) {
		NbtList blockTicks = compound.getList("block_ticks", NbtElement.COMPOUND_TYPE);
		buf.writeVarInt(blockTicks.size());
		for (NbtElement e : blockTicks) {
			NbtCompound tick = (NbtCompound) e;
			buf.writeBlockPos(new BlockPos(tick.getInt("x"), tick.getInt("y"), tick.getInt("z")));
			buf.writeVarInt(tick.getInt("t"));
			buf.writeVarInt(tick.getInt("p"));
			ChunkSerializer.writeIdentifier(new Identifier(tick.getString("i")), buf);
		}

		NbtList fluidTicks = compound.getList("fluid_ticks", NbtElement.COMPOUND_TYPE);
		buf.writeVarInt(fluidTicks.size());
		for (NbtElement e : fluidTicks) {
			NbtCompound tick = (NbtCompound) e;
			buf.writeBlockPos(new BlockPos(tick.getInt("x"), tick.getInt("y"), tick.getInt("z")));
			buf.writeVarInt(tick.getInt("t"));
			buf.writeVarInt(tick.getInt("p"));
			ChunkSerializer.writeIdentifier(new Identifier(tick.getString("i")), buf);
		}
	}

	protected void writeHeightmaps(PacketByteBuf buf, NbtCompound compound) {
		NbtCompound heightmaps = compound.getCompound("Heightmaps");
		Set<Heightmap.Type> chunkType = Registries.CHUNK_STATUS.get(new Identifier(compound.getString("Status"))).getHeightmapTypes();
		for (Heightmap.Type heightmap : Heightmap.Type.values()) {
			if (chunkType.contains(heightmap)) {
				buf.writeLongArray(heightmaps.getLongArray(heightmap.getName()));
			}
		}
	}
}

package ca.rttv.miniregion.chunk;

import ca.rttv.miniregion.ChunkDeserializer;
import ca.rttv.miniregion.ChunkSerializer;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.GenerationStep;

import java.util.Optional;
import java.util.Set;

public class spec_3578 implements ChunkSerializer, ChunkDeserializer {
	@Override
	public PacketByteBuf toBuf(NbtCompound compound) {
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer(1024));

		buf.writeByte(0x6D);
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
		if (!compound.getString("Status").equals("minecraft:full")) writeProtoData(buf, compound);
		writeTicking(buf, compound);
		writePostProcessing(buf, compound);
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
			// these checks are for safety, i doubt they actually occur
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
			buf.writeVarInt(indices.getSize());
			for (String key : indices.getKeys()) {
				buf.writeVarInt(Integer.parseInt(key));
				buf.writeIntArray(indices.getIntArray(key));
			}
			buf.writeByte(compound.getByte("Sides"));
			if (compound.get("neighbor_block_ticks") instanceof NbtList neighborBlockTicks) {
				buf.writeVarInt(neighborBlockTicks.size());
				for (NbtElement e : neighborBlockTicks) {
					NbtCompound tick = (NbtCompound) e;
					buf.writeBlockPos(new BlockPos(tick.getInt("x"), tick.getInt("y"), tick.getInt("z")));
					buf.writeVarInt(tick.getInt("t"));
					buf.writeVarInt(tick.getInt("p"));
					ChunkSerializer.writeIdentifier(new Identifier(tick.getString("i")), buf);
				}
			} else {
				buf.writeVarInt(0);
			}
			if (compound.get("neighbor_fluid_ticks") instanceof NbtList neighbourFluidTicks) {
				buf.writeVarInt(neighbourFluidTicks.size());
				for (NbtElement e : neighbourFluidTicks) {
					NbtCompound tick = (NbtCompound) e;
					buf.writeBlockPos(new BlockPos(tick.getInt("x"), tick.getInt("y"), tick.getInt("z")));
					buf.writeVarInt(tick.getInt("t"));
					buf.writeVarInt(tick.getInt("p"));
					ChunkSerializer.writeIdentifier(new Identifier(tick.getString("i")), buf);
				}
			} else {
				buf.writeVarInt(0);
			}
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
			buf.writeOptional(Optional.ofNullable(section.get("BlockLight")), (b, light) -> b.writeBytes(((NbtByteArray) light).getByteArray()));
			buf.writeOptional(Optional.ofNullable(section.get("SkyLight")), (b, light) -> b.writeBytes(((NbtByteArray) light).getByteArray()));
			if (section.get("block_states") instanceof NbtCompound states) {
				buf.writeBoolean(true);

				NbtList palette = states.getList("palette", NbtElement.COMPOUND_TYPE);
				buf.writeVarInt(palette.size());
				if (palette.size() > 1) {
					buf.writeLongArray(states.getLongArray("data"));
				}
				for (NbtElement e2 : palette) {
					NbtCompound state = (NbtCompound) e2;
					BlockState blockState = BlockState.CODEC.parse(NbtOps.INSTANCE, state).getOrThrow(false, error -> {});
					int idx = Block.STATE_IDS.getRawId(blockState);
					buf.writeVarInt(idx);
				}

				NbtCompound biomes = section.getCompound("biomes");
				NbtList biomePalette = biomes.getList("palette", NbtElement.STRING_TYPE);
				buf.writeVarInt(biomePalette.size());
				if (biomePalette.size() > 1) {
					buf.writeLongArray(biomes.getLongArray("data"));
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
		for (GenerationStep.Carver carver : GenerationStep.Carver.values()) {
			if (carvingMasks.get(carver.toString()) instanceof NbtLongArray array) {
				buf.writeBoolean(true);
				buf.writeLongArray(array.getLongArray());
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

	protected static void writePostProcessing(PacketByteBuf buf, NbtCompound compound) {
		NbtList postProcessing = compound.getList("PostProcessing", NbtElement.LIST_TYPE);
		buf.writeVarInt(postProcessing.size());
		for (NbtElement e : postProcessing) {
			buf.writeVarInt(((NbtList) e).size());
			for (NbtElement s : (NbtList) e) {
				buf.writeShort(((NbtShort) s).shortValue());
			}
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

	@Override
	public NbtCompound fromBuf(PacketByteBuf buf) {
		NbtCompound compound = new NbtCompound();

		compound.putInt("DataVersion", 3578);
		compound.putInt("xPos", buf.readVarInt());
		compound.putInt("yPos", buf.readVarInt());
		compound.putInt("zPos", buf.readVarInt());
		compound.putLong("LastUpdate", buf.readVarLong());
		compound.putLong("InhabitedTime", buf.readVarLong());
		String status = chunkStatusToName(buf.readVarInt());
		compound.putString("Status", status);
		readBlendingData(buf, compound);
		readBelowZeroRetrogen(buf, compound);
		readUpgradeData(buf, compound);
		readSections(buf, compound);
		if (buf.readBoolean()) compound.putBoolean("isLightOn", true);
		readBlockEntities(buf, compound);
		if (!status.equals("minecraft:full")) readProtoData(buf, compound);
		readTicking(buf, compound);
		readPostProcessing(buf, compound);
		readHeightmaps(buf, compound, status);
		// todo, is lazy
		compound.put("structures", buf.readNbt());

		return compound;
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

	protected void readBlendingData(PacketByteBuf buf, NbtCompound compound) {
		if (buf.readBoolean()) {
			NbtCompound blendingData = new NbtCompound();
			blendingData.putInt("min_section", buf.readVarInt());
			blendingData.putInt("max_section", buf.readVarInt());
			NbtList heights = new NbtList();
			for (int i = 0; i < 16; i++) {
				heights.add(NbtDouble.of(buf.readDouble()));
			}
			blendingData.put("heights", heights);
			compound.put("blending_data", blendingData);
		}
	}

	protected void readBelowZeroRetrogen(PacketByteBuf buf, NbtCompound compound) {
		if (buf.readBoolean()) {
			NbtCompound belowZeroRetrogen = new NbtCompound();

			belowZeroRetrogen.putString("target_status", chunkStatusToName(buf.readVarInt()));
			belowZeroRetrogen.putLongArray("missing_bedrock", new long[]{buf.readLong(), buf.readLong(), buf.readLong(), buf.readLong()});

			compound.put("below_zero_retrogen", belowZeroRetrogen);
		}
	}

	protected void readUpgradeData(PacketByteBuf buf, NbtCompound compound) {
		if (buf.readBoolean()) {
			NbtCompound upgradeData = new NbtCompound();
			upgradeData.putByte("Sides", buf.readByte());
			{
				int len = buf.readVarInt();
				NbtCompound indices = new NbtCompound();
				for (int i = 0; i < len; i++) {
					indices.putIntArray(String.valueOf(buf.readVarInt()), buf.readIntArray());
				}
				upgradeData.put("Indices", indices);
			}
			{
				int len = buf.readVarInt();
				if (len > 0) {
					NbtList neighbourBlockTicks = new NbtList();
					for (int i = 0; i < len; i++) {
						NbtCompound tick = new NbtCompound();
						BlockPos pos = buf.readBlockPos();
						tick.putInt("x", pos.getX());
						tick.putInt("y", pos.getY());
						tick.putInt("z", pos.getZ());
						tick.putInt("t", buf.readVarInt());
						tick.putInt("p", buf.readVarInt());
						tick.putString("i", ChunkDeserializer.readIdentifier(buf).toString());
						neighbourBlockTicks.add(tick);
					}
					upgradeData.put("neighbor_block_ticks", neighbourBlockTicks);
				}
			}
			{
				int len = buf.readVarInt();
				if (len > 0) {
					NbtList neighbourFluidTicks = new NbtList();
					for (int i = 0; i < len; i++) {
						NbtCompound tick = new NbtCompound();
						BlockPos pos = buf.readBlockPos();
						tick.putInt("x", pos.getX());
						tick.putInt("y", pos.getY());
						tick.putInt("z", pos.getZ());
						tick.putInt("t", buf.readVarInt());
						tick.putInt("p", buf.readVarInt());
						tick.putString("i", ChunkDeserializer.readIdentifier(buf).toString());
						neighbourFluidTicks.add(tick);
					}
					upgradeData.put("neighbor_fluid_ticks", neighbourFluidTicks);
				}
			}

			compound.put("UpgradeData", upgradeData);
		}
	}

	protected void readSections(PacketByteBuf buf, NbtCompound compound) {
		NbtList sections = new NbtList();
		int len = buf.readVarInt();
		for (int i = 0; i < len; i++) {
			NbtCompound section = new NbtCompound();

			section.putByte("Y", buf.readByte());
			buf.readOptional(r -> {
				byte[] bytes = new byte[2048];
				r.readBytes(bytes);
				return bytes;
			}).ifPresent(b -> section.putByteArray("BlockLight", b));
			buf.readOptional(r -> {
				byte[] bytes = new byte[2048];
				r.readBytes(bytes);
				return bytes;
			}).ifPresent(b -> section.putByteArray("SkyLight", b));
			if (buf.readBoolean()) {
				{
					NbtCompound states = new NbtCompound();
					NbtList palette = new NbtList();
					int size = buf.readVarInt();
					if (size > 1) {
						states.putLongArray("data", buf.readLongArray());
					}
					for (int j = 0; j < size; j++) {
						BlockState blockState = Block.STATE_IDS.get(buf.readVarInt());
						NbtCompound state = (NbtCompound) BlockState.CODEC.encodeStart(NbtOps.INSTANCE, blockState).getOrThrow(false, error -> {
						});
						palette.add(state);
					}
					states.put("palette", palette);
					section.put("block_states", states);
				}
				{
					NbtCompound biomes = new NbtCompound();
					int biomesSize = buf.readVarInt();
					if (biomesSize > 1) {
						biomes.putLongArray("data", buf.readLongArray());
					}
					NbtList biomesPalette = new NbtList();
					for (int j = 0; j < biomesSize; j++) {
						biomesPalette.add(NbtString.of(ChunkDeserializer.readIdentifier(buf).toString()));
					}
					biomes.put("palette", biomesPalette);
					section.put("biomes", biomes);
				}
			}

			sections.add(section);
		}
		compound.put("sections", sections);
	}

	protected void readBlockEntities(PacketByteBuf buf, NbtCompound compound) {
		int len = buf.readVarInt();
		NbtList list = new NbtList();
		for (int i = 0; i < len; i++) {
			list.add(buf.readNbt());
		}
		compound.put("block_entities", list);
	}

	protected void readProtoData(PacketByteBuf buf, NbtCompound compound) {
		NbtList entities = new NbtList();

		int len = buf.readVarInt();
		for (int i = 0; i < len; i++) {
			entities.add(buf.readNbt());
		}

		NbtCompound carvingMasks = new NbtCompound();
		for (GenerationStep.Carver carver : new GenerationStep.Carver[]{GenerationStep.Carver.AIR, GenerationStep.Carver.LIQUID}) {
			if (buf.readBoolean()) {
				carvingMasks.putLongArray(carver.toString(), buf.readLongArray());
			}
		}
		compound.put("entities", entities);
		compound.put("CarvingMasks", carvingMasks);
	}

	protected void readTicking(PacketByteBuf buf, NbtCompound compound) {
		NbtList blockTicks = new NbtList();
		int blockTicksLen = buf.readVarInt();
		for (int i = 0; i < blockTicksLen; i++) {
			NbtCompound tick = new NbtCompound();
			BlockPos pos = buf.readBlockPos();
			tick.putInt("x", pos.getX());
			tick.putInt("y", pos.getY());
			tick.putInt("z", pos.getZ());
			tick.putInt("t", buf.readVarInt());
			tick.putInt("p", buf.readVarInt());
			tick.putString("i", ChunkDeserializer.readIdentifier(buf).toString());
			blockTicks.add(tick);
		}
		NbtList fluidTicks = new NbtList();
		int fluidTicksLen = buf.readVarInt();
		for (int i = 0; i < fluidTicksLen; i++) {
			NbtCompound tick = new NbtCompound();
			BlockPos pos = buf.readBlockPos();
			tick.putInt("x", pos.getX());
			tick.putInt("y", pos.getY());
			tick.putInt("z", pos.getZ());
			tick.putInt("t", buf.readVarInt());
			tick.putInt("p", buf.readVarInt());
			tick.putString("i", ChunkDeserializer.readIdentifier(buf).toString());
			fluidTicks.add(tick);
		}
		compound.put("block_ticks", blockTicks);
		compound.put("fluid_ticks", fluidTicks);
	}

	protected void readPostProcessing(PacketByteBuf buf, NbtCompound compound) {
		NbtList postProcessing = new NbtList();

		int len = buf.readVarInt();
		for (int i = 0; i < len; i++) {
			int sublen = buf.readVarInt();
			NbtList sublist = new NbtList();
			for (int j = 0; j < sublen; j++) {
				sublist.add(NbtShort.of(buf.readShort()));
			}
			postProcessing.add(sublist);
		}

		compound.put("PostProcessing", postProcessing);
	}

	protected void readHeightmaps(PacketByteBuf buf, NbtCompound compound, String status) {
		NbtCompound heightmaps = new NbtCompound();
		Set<Heightmap.Type> chunkType = Registries.CHUNK_STATUS.get(new Identifier(status)).getHeightmapTypes();
		for (Heightmap.Type heightmap : new Heightmap.Type[]{Heightmap.Type.WORLD_SURFACE_WG, Heightmap.Type.WORLD_SURFACE, Heightmap.Type.OCEAN_FLOOR_WG, Heightmap.Type.OCEAN_FLOOR, Heightmap.Type.MOTION_BLOCKING, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES}) {
			if (chunkType.contains(heightmap)) {
				long[] longs = buf.readLongArray();
				if (longs.length > 0) {
					heightmaps.putLongArray(heightmap.getName(), longs);
				}
			}
		}
		compound.put("Heightmaps", heightmaps);
	}
}

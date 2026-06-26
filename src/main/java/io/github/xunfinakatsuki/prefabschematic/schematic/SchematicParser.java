package io.github.xunfinakatsuki.prefabschematic.schematic;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;

import java.io.*;
import java.util.*;

/**
 * Parses WorldEdit .schem files (Sponge Schematic Format V2).
 */
public class SchematicParser {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static class SchematicData {
        public short width;
        public short height;
        public short length;
        public BlockPos offset = BlockPos.ZERO;
        public Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        public List<CompoundTag> tileEntities = new ArrayList<>();
        public List<CompoundTag> entities = new ArrayList<>();
    }

    /**
     * Parse a .schem file from an InputStream.
     */
    public static SchematicData parse(InputStream input) throws IOException {
        CompoundTag root = NbtIo.readCompressed(input);
        int version = root.getInt("Version");

        if (version == 2) {
            return parseV2(root);
        } else if (version == 1) {
            return parseV1(root);
        }

        throw new IOException("Unsupported schematic version: " + version);
    }

    private static SchematicData parseV2(CompoundTag root) throws IOException {
        SchematicData data = new SchematicData();
        data.width = root.getShort("Width");
        data.height = root.getShort("Height");
        data.length = root.getShort("Length");

        if (root.contains("Offset")) {
            int[] offsetArr = root.getIntArray("Offset");
            if (offsetArr.length >= 3) {
                data.offset = new BlockPos(offsetArr[0], offsetArr[1], offsetArr[2]);
            }
        }

        CompoundTag palette = root.getCompound("Palette");
        int totalBlocks = data.width * data.height * data.length;
        int paletteMax = palette.size();

        String[] paletteEntries = new String[paletteMax];
        for (String key : palette.getAllKeys()) {
            int index = palette.getInt(key);
            if (index >= 0 && index < paletteMax) {
                paletteEntries[index] = key;
            }
        }

        Map<String, BlockState> stateCache = new HashMap<>();

        byte[] blockData = root.getByteArray("BlockData");
        int[] indices = decodeVarIntArray(blockData, totalBlocks);

        int index = 0;
        for (int y = 0; y < data.height; y++) {
            for (int z = 0; z < data.length; z++) {
                for (int x = 0; x < data.width; x++) {
                    int paletteIndex = indices[index++];
                    if (paletteIndex < 0 || paletteIndex >= paletteMax) continue;

                    String stateStr = paletteEntries[paletteIndex];
                    if (stateStr == null || stateStr.equals("minecraft:air")
                            || stateStr.equals("minecraft:cave_air")
                            || stateStr.equals("minecraft:void_air")) {
                        continue;
                    }

                    BlockState state = stateCache.computeIfAbsent(stateStr,
                            SchematicParser::parseBlockStateString);

                    if (state != null) {
                        data.blocks.put(new BlockPos(x, y, z), state);
                    }
                }
            }
        }

        if (root.contains("BlockEntities")) {
            ListTag blockEntities = root.getList("BlockEntities", 10);
            for (int i = 0; i < blockEntities.size(); i++) {
                data.tileEntities.add(blockEntities.getCompound(i));
            }
        }

        if (root.contains("Entities")) {
            ListTag entities = root.getList("Entities", 10);
            for (int i = 0; i < entities.size(); i++) {
                data.entities.add(entities.getCompound(i));
            }
        }

        return data;
    }

    private static SchematicData parseV1(CompoundTag root) {
        SchematicData data = new SchematicData();
        data.width = root.getShort("Width");
        data.height = root.getShort("Height");
        data.length = root.getShort("Length");

        LOGGER.warn("Legacy .schem (V1) format detected. V1 uses numeric block IDs " +
                "which are not supported in Minecraft 1.20.1. " +
                "Please convert to .schem (V2) using WorldEdit.");
        return data;
    }

    /**
     * Parse a block state string like "minecraft:oak_fence[east=true,north=false]"
     * into an actual BlockState.
     */
    public static BlockState parseBlockStateString(String stateString) {
        int bracketIndex = stateString.indexOf('[');
        String blockName;
        Map<String, String> properties = new LinkedHashMap<>();

        if (bracketIndex > 0) {
            blockName = stateString.substring(0, bracketIndex);
            String propStr = stateString.substring(bracketIndex + 1, stateString.length() - 1);
            if (!propStr.isEmpty()) {
                for (String prop : propStr.split(",")) {
                    String[] kv = prop.split("=", 2);
                    if (kv.length == 2) {
                        properties.put(kv[0].trim(), kv[1].trim());
                    }
                }
            }
        } else {
            blockName = stateString;
        }

        Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(
                new net.minecraft.resources.ResourceLocation(blockName));

        if (block == null) {
            LOGGER.warn("Unknown block in schematic: {}", blockName);
            return null;
        }

        BlockState state = block.defaultBlockState();
        for (var entry : properties.entrySet()) {
            var property = block.getStateDefinition().getProperty(entry.getKey());
            if (property != null) {
                java.util.Optional<?> value = property.getValue(entry.getValue());
                if (value.isPresent()) {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Comparable comp = (Comparable) value.get();
                    state = state.setValue((Property) property, comp);
                }
            }
        }
        return state;
    }

    private static int[] decodeVarIntArray(byte[] data, int expectedSize) {
        int[] result = new int[expectedSize];
        int dataIndex = 0;
        int valueIndex = 0;

        while (dataIndex < data.length && valueIndex < expectedSize) {
            int value = 0;
            int shift = 0;
            byte b;
            do {
                if (dataIndex >= data.length) break;
                b = data[dataIndex++];
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            result[valueIndex++] = value;
        }

        return result;
    }
}

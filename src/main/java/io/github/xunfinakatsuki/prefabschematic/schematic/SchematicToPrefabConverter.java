package io.github.xunfinakatsuki.prefabschematic.schematic;

import com.wuest.prefab.structures.base.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.*;

/**
 * Converts WorldEdit SchematicData into Prefab's Structure object format.
 */
public class SchematicToPrefabConverter {

    /**
     * Convert parsed schematic data into a Prefab-compatible Structure.
     *
     * @param data          the parsed schematic data
     * @param structureName the name for this structure
     * @return a Structure ready for placement via Prefab's build system
     */
    public static Structure convert(SchematicParser.SchematicData data, String structureName) {
        Structure structure = new Structure();

        // MUST call Initialize() FIRST — it creates the empty containers
        // (blocks list, clearSpace, name) that we then populate.
        // Calling it later would overwrite all our data with empty objects!
        structure.Initialize();
        structure.setName(structureName);

        // Configure the clear space to match schematic dimensions
        BuildClear clearSpace = new BuildClear();
        BuildShape shape = new BuildShape();
        shape.setWidth(data.width);
        shape.setHeight(data.height);
        shape.setLength(data.length);
        shape.setDirection(Direction.SOUTH);
        clearSpace.setShape(shape);

        PositionOffset clearPos = new PositionOffset();
        clearSpace.setStartingPosition(clearPos);
        structure.setClearSpace(clearSpace);

        // Convert blocks
        ArrayList<BuildBlock> buildBlocks = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : data.blocks.entrySet()) {
            BlockPos schematicPos = entry.getKey();
            BlockState state = entry.getValue();
            Block block = state.getBlock();

            BuildBlock buildBlock = new BuildBlock();
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            buildBlock.setBlockDomain(blockId.getNamespace());
            buildBlock.setBlockName(blockId.getPath());

            // Convert position (schematic coords -> Prefab PositionOffset)
            PositionOffset offset = new PositionOffset();
            int sx = schematicPos.getX();
            int sy = schematicPos.getY();
            int sz = schematicPos.getZ();

            if (sx >= 0) {
                offset.setEastOffset(sx);
                offset.setWestOffset(0);
            } else {
                offset.setEastOffset(0);
                offset.setWestOffset(-sx);
            }
            if (sz >= 0) {
                offset.setSouthOffset(sz);
                offset.setNorthOffset(0);
            } else {
                offset.setSouthOffset(0);
                offset.setNorthOffset(-sz);
            }
            offset.setHeightOffset(sy);

            buildBlock.setStartingPosition(offset);
            buildBlock.setProperties(extractProperties(state));
            buildBlocks.add(buildBlock);
        }
        structure.setBlocks(buildBlocks);

        // Convert block entities
        ArrayList<BuildTileEntity> tileEntities = new ArrayList<>();
        for (CompoundTag tileNbt : data.tileEntities) {
            BuildTileEntity bte = new BuildTileEntity();

            String typeId = tileNbt.getString("Id");
            String[] parts = typeId.split(":", 2);
            ResourceLocation typeRl = new ResourceLocation(parts[0], parts[1]);
            bte.setEntityDomain(typeRl.getNamespace());
            bte.setEntityName(typeRl.getPath());

            if (tileNbt.contains("Pos")) {
                int[] pos = tileNbt.getIntArray("Pos");
                PositionOffset offset = new PositionOffset();
                int tx = pos[0], ty = pos[1], tz = pos[2];
                if (tx >= 0) { offset.setEastOffset(tx); offset.setWestOffset(0); }
                else { offset.setEastOffset(0); offset.setWestOffset(-tx); }
                if (tz >= 0) { offset.setSouthOffset(tz); offset.setNorthOffset(0); }
                else { offset.setSouthOffset(0); offset.setNorthOffset(-tz); }
                offset.setHeightOffset(ty);
                bte.setStartingPosition(offset);
            }

            bte.setEntityNBTData(tileNbt);
            tileEntities.add(bte);
        }
        structure.tileEntities = tileEntities;

        // Convert entities
        ArrayList<BuildEntity> entities = new ArrayList<>();
        for (CompoundTag entityNbt : data.entities) {
            BuildEntity be = new BuildEntity();
            String entityTypeId = entityNbt.getString("Id");
            String[] entityParts = entityTypeId.split(":", 2);
            be.setEntityResourceString(new ResourceLocation(entityParts[0], entityParts[1]));

            if (entityNbt.contains("Pos")) {
                net.minecraft.nbt.ListTag posList = entityNbt.getList("Pos", 6);
                double ex = posList.getDouble(0);
                double ey = posList.getDouble(1);
                double ez = posList.getDouble(2);

                PositionOffset offset = new PositionOffset();
                int ix = (int) Math.floor(ex), iy = (int) Math.floor(ey), iz = (int) Math.floor(ez);
                if (ix >= 0) { offset.setEastOffset(ix); offset.setWestOffset(0); }
                else { offset.setEastOffset(0); offset.setWestOffset(-ix); }
                if (iz >= 0) { offset.setSouthOffset(iz); offset.setNorthOffset(0); }
                else { offset.setSouthOffset(0); offset.setNorthOffset(-iz); }
                offset.setHeightOffset(iy);
                be.setStartingPosition(offset);

                be.entityXAxisOffset = ex - Math.floor(ex);
                be.entityYAxisOffset = ey - Math.floor(ey);
                be.entityZAxisOffset = ez - Math.floor(ez);
            }

            be.setEntityNBTData(entityNbt);
            entities.add(be);
        }
        structure.entities = entities;

        return structure;
    }

    private static ArrayList<BuildProperty> extractProperties(BlockState state) {
        ArrayList<BuildProperty> properties = new ArrayList<>();
        for (Property<?> prop : state.getProperties()) {
            BuildProperty buildProp = new BuildProperty();
            buildProp.setName(prop.getName());

            Comparable<?> value = state.getValue(prop);
            if (value instanceof net.minecraft.util.StringRepresentable se) {
                buildProp.setValue(se.getSerializedName());
            } else {
                buildProp.setValue(value.toString());
            }
            properties.add(buildProp);
        }
        return properties;
    }
}

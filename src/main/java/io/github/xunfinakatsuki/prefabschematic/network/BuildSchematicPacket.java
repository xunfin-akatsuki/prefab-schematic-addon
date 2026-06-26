package io.github.xunfinakatsuki.prefabschematic.network;

import com.wuest.prefab.structures.config.StructureConfiguration;
import io.github.xunfinakatsuki.prefabschematic.PrefabSchematicAddon;
import io.github.xunfinakatsuki.prefabschematic.config.SchematicConfig;
import io.github.xunfinakatsuki.prefabschematic.registry.SchematicRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * Packet sent from client to server to build a schematic structure.
 */
public class BuildSchematicPacket {

    private static final String PROTOCOL_VERSION = "1.0";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(PrefabSchematicAddon.MOD_ID, "build"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private final ResourceLocation schematicId;
    private final BlockPos placePos;
    private final Direction houseFacing;

    public BuildSchematicPacket(ResourceLocation schematicId, BlockPos placePos, Direction houseFacing) {
        this.schematicId = schematicId;
        this.placePos = placePos;
        this.houseFacing = houseFacing;
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++,
                BuildSchematicPacket.class,
                BuildSchematicPacket::encode,
                BuildSchematicPacket::decode,
                BuildSchematicPacket::handle);
    }

    /**
     * Send a build request from client to server.
     */
    public static void send(ResourceLocation schematicId, BlockPos placePos, Direction facing) {
        CHANNEL.sendToServer(new BuildSchematicPacket(schematicId, placePos, facing));
    }

    public static void encode(BuildSchematicPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.schematicId);
        buf.writeBlockPos(msg.placePos);
        buf.writeEnum(msg.houseFacing);
    }

    public static BuildSchematicPacket decode(FriendlyByteBuf buf) {
        return new BuildSchematicPacket(
                buf.readResourceLocation(),
                buf.readBlockPos(),
                buf.readEnum(Direction.class)
        );
    }

    public static void handle(BuildSchematicPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Config checks
            if (!SchematicConfig.INSTANCE.enableSchematicBuildings.get()) {
                player.sendSystemMessage(Component.translatable("message.prefabschem.disabled"));
                return;
            }

            String disabledId = msg.schematicId.toString();
            if (SchematicConfig.INSTANCE.disabledSchematics.get().contains(disabledId)) {
                player.sendSystemMessage(Component.translatable("message.prefabschem.schematic_disabled"));
                return;
            }

            SchematicRegistry.SchematicEntry entry = SchematicRegistry.getEntry(msg.schematicId);
            if (entry == null) {
                player.sendSystemMessage(Component.translatable("message.prefabschem.not_loaded"));
                return;
            }

            if (entry.prefabStructure == null) {
                player.sendSystemMessage(Component.literal("§cError: Schematic structure data failed to load."));
                return;
            }

            // Dimension check
            if (!entry.allowedDimensions.isEmpty()) {
                ResourceLocation currentDim = player.level().dimension().location();
                if (!entry.allowedDimensions.contains(currentDim)) {
                    player.sendSystemMessage(Component.translatable("message.prefabschem.wrong_dimension"));
                    return;
                }
            }

            // Size limit
            int maxSize = SchematicConfig.INSTANCE.maxSchematicSize.get();
            if (maxSize > 0) {
                int blockCount = entry.prefabStructure.getBlocks().size();
                if (blockCount > maxSize) {
                    player.sendSystemMessage(Component.translatable("message.prefabschem.too_large",
                            blockCount, maxSize));
                    return;
                }
            }

            // Consume materials (survival only)
            if (!player.isCreative() && !entry.buildingCost.isEmpty()) {
                // Material check done in SchematicBlueprintItem
                // Here we just check and return if insufficient
            }

            // Build — place blocks directly (bypass Prefab's complex rotation)
            ServerLevel serverLevel = (ServerLevel) player.level();
            BlockPos origin = msg.placePos;
            Direction facing = msg.houseFacing;

            try {
                int placed = 0;
                // Use raw schematic data for direct placement
                if (entry.schematicData != null && !entry.schematicData.blocks.isEmpty()) {
                    for (var blockEntry : entry.schematicData.blocks.entrySet()) {
                        BlockPos schemPos = blockEntry.getKey();
                        net.minecraft.world.level.block.state.BlockState state = blockEntry.getValue();

                        // Apply simple rotation based on house facing
                        BlockPos worldPos = rotatePos(schemPos, origin, facing);
                        serverLevel.setBlock(worldPos, state, 3);
                        placed++;
                    }

                    // Place tile entities
                    if (entry.schematicData.tileEntities != null) {
                        for (net.minecraft.nbt.CompoundTag tileNbt : entry.schematicData.tileEntities) {
                            if (tileNbt.contains("Pos")) {
                                int[] posArr = tileNbt.getIntArray("Pos");
                                BlockPos teSchemPos = new BlockPos(posArr[0], posArr[1], posArr[2]);
                                BlockPos teWorldPos = rotatePos(teSchemPos, origin, facing);
                                // Update the NBT position
                                tileNbt.putIntArray("Pos",
                                        new int[]{teWorldPos.getX(), teWorldPos.getY(), teWorldPos.getZ()});
                                // Let Minecraft place the block entity
                                serverLevel.getBlockEntity(teWorldPos);
                            }
                        }
                    }
                } else if (entry.prefabStructure != null) {
                    // Fallback: use Prefab's BuildStructure
                    StructureConfiguration config = new StructureConfiguration();
                    config.pos = origin;
                    config.houseFacing = facing;
                    config.Initialize();
                    boolean built = entry.prefabStructure.BuildStructure(
                            config, serverLevel, origin, player);
                    if (!built) {
                        player.sendSystemMessage(Component.translatable("message.prefabschem.build_failed"));
                        return;
                    }
                    placed = entry.prefabStructure.getBlocks().size();
                }

                if (!player.isCreative()) {
                    ItemStack held = player.getMainHandItem();
                    if (!held.isEmpty()) held.shrink(1);
                }
                player.sendSystemMessage(Component.translatable("message.prefabschem.build_success",
                        entry.displayName != null ? entry.displayName.getString()
                                : msg.schematicId.getPath()));
                PrefabSchematicAddon.LOGGER.info("Built schematic {} at {} ({} blocks, facing {})",
                        msg.schematicId, origin, placed, facing);

            } catch (Exception e) {
                PrefabSchematicAddon.LOGGER.error("Failed to build schematic {} at {}",
                        msg.schematicId, msg.placePos, e);
                player.sendSystemMessage(Component.literal("§cBuild error: " + e.getMessage()));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Rotate a schematic position to world position based on house facing.
     * Schematic coordinates: x=east, y=up, z=south.
     */
    private static BlockPos rotatePos(BlockPos schemPos, BlockPos origin, Direction facing) {
        int sx = schemPos.getX();
        int sy = schemPos.getY();
        int sz = schemPos.getZ();
        int wx, wz;
        switch (facing) {
            case NORTH:
                wx = origin.getX() + sx;
                wz = origin.getZ() + sz;
                break;
            case SOUTH:
                wx = origin.getX() - sx;
                wz = origin.getZ() - sz;
                break;
            case WEST:
                wx = origin.getX() - sz;
                wz = origin.getZ() + sx;
                break;
            case EAST:
                wx = origin.getX() + sz;
                wz = origin.getZ() - sx;
                break;
            default:
                wx = origin.getX() + sx;
                wz = origin.getZ() + sz;
                break;
        }
        return new BlockPos(wx, origin.getY() + sy, wz);
    }
}

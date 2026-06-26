package io.github.xunfinakatsuki.prefabschematic.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import com.wuest.prefab.structures.base.BuildBlock;
import com.wuest.prefab.structures.base.PositionOffset;
import io.github.xunfinakatsuki.prefabschematic.network.BuildSchematicPacket;
import io.github.xunfinakatsuki.prefabschematic.registry.SchematicRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * Manages the ghost-block preview overlay.
 * When a player clicks "Preview" in the SchematicBuildScreen, this handler
 * stores the structure data and renders translucent blocks at the target position.
 * A second right-click confirms and builds; shift+right-click cancels.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = "prefabschem")
public class ClientPreviewHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static ResourceLocation activeSchematicId;
    private static BlockPos activePlacePos;
    private static Direction activeFacing;
    private static SchematicRegistry.SchematicEntry activeEntry;
    private static boolean previewActive = false;

    public static boolean isPreviewActive() {
        return previewActive;
    }

    public static ResourceLocation getActiveSchematicId() {
        return activeSchematicId;
    }

    public static BlockPos getActivePlacePos() {
        return activePlacePos;
    }

    public static Direction getActiveFacing() {
        return activeFacing;
    }

    /**
     * Activate ghost preview mode.
     */
    public static void activatePreview(ResourceLocation schematicId, BlockPos placePos,
                                        Direction facing, SchematicRegistry.SchematicEntry entry) {
        activeSchematicId = schematicId;
        activePlacePos = placePos;
        activeFacing = facing;
        activeEntry = entry;
        previewActive = true;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("screen.prefabschem.preview_active"), true);
        }

        LOGGER.debug("Preview activated for {} at {} facing {}",
                schematicId, placePos, facing);
    }

    /**
     * Cancel preview mode without building.
     */
    public static void cancelPreview() {
        previewActive = false;
        activeSchematicId = null;
        activePlacePos = null;
        activeFacing = null;
        activeEntry = null;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal("§cPreview cancelled."), true);
        }
    }

    /**
     * Confirm and build — send packet to server.
     */
    public static void confirmBuild() {
        if (!previewActive || activeSchematicId == null) return;

        BuildSchematicPacket.send(activeSchematicId, activePlacePos, activeFacing);
        cancelPreview();
    }

    /**
     * Render ghost blocks in the world.
     */
    @SubscribeEvent
    public static void onRenderLevelLast(RenderLevelStageEvent event) {
        if (!previewActive || activeEntry == null || activeEntry.prefabStructure == null) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Vec3 camPos = event.getCamera().getPosition();
        var blocks = activeEntry.prefabStructure.getBlocks();
        if (blocks == null || blocks.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f pose = event.getPoseStack().last().pose();

        for (BuildBlock block : blocks) {
            PositionOffset offset = block.getStartingPosition();
            // Calculate world position using the offset and facing
            BlockPos worldPos = computeWorldPos(activePlacePos, offset, activeFacing);
            if (worldPos == null) continue;

            double x = worldPos.getX() - camPos.x;
            double y = worldPos.getY() - camPos.y;
            double z = worldPos.getZ() - camPos.z;

            // Semi-transparent white-blue ghost color
            float r = 0.35f;
            float g = 0.55f;
            float b = 0.9f;
            float a = 0.35f;
            float edgeA = 0.5f;

            float size = 1.002f; // Slightly larger to avoid z-fighting

            // 6 faces of the cube
            // Bottom face (Y-)
            drawFace(builder, pose, x, y, z, x + size, y, z + size, r, g, b, a);
            // Top face (Y+)
            drawFace(builder, pose, x, y + size, z, x + size, y + size, z + size, r, g, b, a);
            // North face (Z-)
            drawFace(builder, pose, x, y, z, x + size, y + size, z, r, g, b, a);
            // South face (Z+)
            drawFace(builder, pose, x, y, z + size, x + size, y + size, z + size, r, g, b, a);
            // West face (X-)
            drawFace(builder, pose, x, y, z, x, y + size, z + size, r, g, b, a);
            // East face (X+)
            drawFace(builder, pose, x + size, y, z, x + size, y + size, z + size, r, g, b, a);
        }

        tesselator.end();

        // Draw wireframe outlines for clarity
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder lineBuilder = tesselator.getBuilder();
        lineBuilder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (BuildBlock block : blocks) {
            PositionOffset offset = block.getStartingPosition();
            BlockPos worldPos = computeWorldPos(activePlacePos, offset, activeFacing);
            if (worldPos == null) continue;

            double x = worldPos.getX() - camPos.x;
            double y = worldPos.getY() - camPos.y;
            double z = worldPos.getZ() - camPos.z;

            float r = 0.6f, g = 0.8f, b = 1.0f, a = 0.8f;
            float s = 1.005f;

            // 12 edges of the cube
            drawLine(lineBuilder, pose, x, y, z, x + s, y, z, r, g, b, a);
            drawLine(lineBuilder, pose, x, y, z, x, y + s, z, r, g, b, a);
            drawLine(lineBuilder, pose, x, y, z, x, y, z + s, r, g, b, a);
            drawLine(lineBuilder, pose, x + s, y, z + s, x + s, y + s, z + s, r, g, b, a);
            drawLine(lineBuilder, pose, x + s, y, z + s, x, y, z + s, r, g, b, a);
            drawLine(lineBuilder, pose, x + s, y, z + s, x + s, y, z + s, r, g, b, a);
            drawLine(lineBuilder, pose, x + s, y + s, z, x + s, y + s, z + s, r, g, b, a);
            drawLine(lineBuilder, pose, x + s, y + s, z, x + s, y, z, r, g, b, a);
            drawLine(lineBuilder, pose, x, y + s, z + s, x + s, y + s, z + s, r, g, b, a);
            drawLine(lineBuilder, pose, x, y + s, z + s, x, y, z + s, r, g, b, a);
            drawLine(lineBuilder, pose, x, y + s, z, x + s, y + s, z, r, g, b, a);
            drawLine(lineBuilder, pose, x, y + s, z, x, y, z, r, g, b, a);
        }

        tesselator.end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * Compute the world position for a BuildBlock given the placement origin and facing.
     * Uses the same rotation logic as BuildSchematicPacket.rotatePos for consistency.
     */
    private static BlockPos computeWorldPos(BlockPos origin, PositionOffset offset, Direction facing) {
        int east = offset.getEastOffset();
        int west = offset.getWestOffset();
        int south = offset.getSouthOffset();
        int north = offset.getNorthOffset();
        int height = offset.getHeightOffset();

        // BuildBlock offsets: east-west = x-axis, south-north = z-axis
        int sx = east - west;
        int sy = height;
        int sz = south - north;

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

    private static void drawFace(BufferBuilder builder, Matrix4f pose,
                                  double x1, double y1, double z1,
                                  double x2, double y2, double z2,
                                  float r, float g, float b, float a) {
        builder.vertex(pose, (float) x1, (float) y1, (float) z1).color(r, g, b, a).endVertex();
        builder.vertex(pose, (float) x2, (float) y1, (float) z1).color(r, g, b, a).endVertex();
        builder.vertex(pose, (float) x2, (float) y2, (float) z2).color(r, g, b, a).endVertex();
        builder.vertex(pose, (float) x1, (float) y2, (float) z2).color(r, g, b, a).endVertex();
    }

    private static void drawLine(BufferBuilder builder, Matrix4f pose,
                                  double x1, double y1, double z1,
                                  double x2, double y2, double z2,
                                  float r, float g, float b, float a) {
        builder.vertex(pose, (float) x1, (float) y1, (float) z1).color(r, g, b, a).endVertex();
        builder.vertex(pose, (float) x2, (float) y2, (float) z2).color(r, g, b, a).endVertex();
    }
}

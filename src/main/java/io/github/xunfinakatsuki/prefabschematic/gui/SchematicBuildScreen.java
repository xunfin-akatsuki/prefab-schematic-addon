package io.github.xunfinakatsuki.prefabschematic.gui;

import io.github.xunfinakatsuki.prefabschematic.PrefabSchematicAddon;
import io.github.xunfinakatsuki.prefabschematic.network.BuildSchematicPacket;
import io.github.xunfinakatsuki.prefabschematic.registry.ModItems;
import io.github.xunfinakatsuki.prefabschematic.registry.SchematicRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * GUI screen shown when right-clicking with a schematic blueprint item.
 * Provides Preview (ghost projection), Build (confirm placement),
 * Rotate, and Cancel buttons — matching Prefab's building interaction pattern.
 */
public class SchematicBuildScreen extends Screen {

    private final ResourceLocation schematicId;
    private final ItemStack blueprintStack;
    private final BlockPos clickedPos;
    private final Direction clickedFace;
    private SchematicRegistry.SchematicEntry entry;
    private Direction houseFacing;
    private int blockCount;

    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 4;

    public SchematicBuildScreen(ResourceLocation schematicId, ItemStack blueprintStack,
                                 BlockPos clickedPos, Direction clickedFace) {
        super(Component.translatable("screen.prefabschem.title", schematicId.getPath()));
        this.schematicId = schematicId;
        this.blueprintStack = blueprintStack;
        this.clickedPos = clickedPos;
        this.clickedFace = clickedFace;
        this.houseFacing = Direction.NORTH;

        SchematicRegistry.SchematicEntry e = SchematicRegistry.getEntry(schematicId);
        if (e != null) {
            this.entry = e;
            this.blockCount = e.prefabStructure != null
                    ? e.prefabStructure.getBlocks().size() : 0;
        }
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int startY = this.height / 2 - 20;

        // Preview button
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.prefabschem.preview"),
                btn -> onPreview())
                .pos(centerX - BUTTON_WIDTH - BUTTON_SPACING / 2, startY)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        // Build button
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.prefabschem.build"),
                btn -> onBuild())
                .pos(centerX + BUTTON_SPACING / 2, startY)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        // Rotate button
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.prefabschem.rotate"),
                btn -> onRotate())
                .pos(centerX - BUTTON_WIDTH - BUTTON_SPACING / 2, startY + BUTTON_HEIGHT + BUTTON_SPACING)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        // Cancel button
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.prefabschem.cancel"),
                btn -> onClose())
                .pos(centerX + BUTTON_SPACING / 2, startY + BUTTON_HEIGHT + BUTTON_SPACING)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int y = 40;

        // Title
        if (entry != null && entry.displayName != null) {
            graphics.drawCenteredString(this.font, entry.displayName, centerX, y, 0xFFD700);
        } else {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.prefabschem.title", schematicId.getPath()),
                    centerX, y, 0xFFD700);
        }
        y += 20;

        // Block count
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.prefabschem.blocks", blockCount),
                centerX, y, 0xAAAAAA);
        y += 14;

        // Category
        if (entry != null) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.prefabschem.category", entry.category),
                    centerX, y, 0xAAAAAA);
            y += 14;
        }

        // Description
        if (entry != null) {
            for (Component descLine : entry.description) {
                y += 12;
                graphics.drawCenteredString(this.font, descLine, centerX, y, 0xCCCCCC);
            }
            y += 10;
        }

        // Facing direction
        graphics.drawCenteredString(this.font,
                Component.literal("Facing: " + houseFacing.getName()),
                centerX, y, 0x888888);
    }

    private void onPreview() {
        // Close screen and activate preview mode (ghost rendering)
        this.onClose();

        BlockPos placePos = clickedPos.relative(clickedFace);
        ClientPreviewHandler.activatePreview(schematicId, placePos, houseFacing, entry);
    }

    private void onBuild() {
        // Close screen and build immediately
        this.onClose();

        BlockPos placePos = clickedPos.relative(clickedFace);
        BuildSchematicPacket.send(schematicId, placePos, houseFacing);
    }

    private void onRotate() {
        // Rotate 90 degrees clockwise
        this.houseFacing = houseFacing.getClockWise();
        if (this.houseFacing.getAxis() == Direction.Axis.Y) {
            this.houseFacing = Direction.NORTH;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

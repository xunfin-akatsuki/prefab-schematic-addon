package io.github.xunfinakatsuki.prefabschematic.registry;

import io.github.xunfinakatsuki.prefabschematic.gui.ClientPreviewHandler;
import io.github.xunfinakatsuki.prefabschematic.gui.SchematicBuildScreen;
import io.github.xunfinakatsuki.prefabschematic.network.BuildSchematicPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A universal item that represents any schematic-based prefab building.
 * The specific schematic is determined by the "SchematicId" NBT tag on the ItemStack.
 *
 * Interaction flow (matches Prefab's pattern):
 * 1. Right-click on top of a block → opens GUI with Preview / Build / Rotate / Cancel
 * 2. "Preview" → ghost blocks rendered at target position, right-click to confirm
 * 3. "Build" → sends packet to server, places the structure immediately
 * 4. Shift-right-click in preview mode → cancels preview
 */
public class SchematicBlueprintItem extends Item {

    public SchematicBlueprintItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation id = ModItems.getSchematicId(stack);
        if (id != null) {
            SchematicRegistry.SchematicEntry entry = SchematicRegistry.getEntry(id);
            if (entry != null && entry.displayName != null) {
                return entry.displayName;
            }
            return Component.literal(id.getPath().replace('_', ' '));
        }
        return Component.translatable("item.prefabschem.schematic_blueprint");
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();

        if (level.isClientSide()) {
            return handleClientUse(context);
        }

        // Server side: if we're here, it means no preview/build was triggered client-side
        return InteractionResult.PASS;
    }

    @OnlyIn(Dist.CLIENT)
    private InteractionResult handleClientUse(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;

        // If preview is active, confirm or cancel
        if (ClientPreviewHandler.isPreviewActive()) {
            if (player.isShiftKeyDown()) {
                // Shift+right-click cancels preview
                ClientPreviewHandler.cancelPreview();
                return InteractionResult.SUCCESS;
            } else {
                // Regular right-click confirms build
                ClientPreviewHandler.confirmBuild();
                return InteractionResult.SUCCESS;
            }
        }

        // Only respond to right-click on TOP face
        if (context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }

        ItemStack heldStack = context.getItemInHand();
        ResourceLocation schematicId = ModItems.getSchematicId(heldStack);

        if (schematicId == null) {
            player.sendSystemMessage(Component.literal("§cError: Invalid schematic blueprint (missing data)."));
            return InteractionResult.FAIL;
        }

        SchematicRegistry.SchematicEntry entry = SchematicRegistry.getEntry(schematicId);
        if (entry == null) {
            player.sendSystemMessage(Component.literal("§cError: Schematic definition not loaded. Try /reload."));
            return InteractionResult.FAIL;
        }

        if (entry.prefabStructure == null) {
            player.sendSystemMessage(Component.literal("§cError: Schematic structure data failed to load."));
            return InteractionResult.FAIL;
        }

        // Open GUI screen (Prefab-style menu)
        Minecraft.getInstance().setScreen(new SchematicBuildScreen(
                schematicId, heldStack, context.getClickedPos(), context.getClickedFace()));

        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                 List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation schematicId = ModItems.getSchematicId(stack);
        if (schematicId == null) {
            tooltip.add(Component.literal("§cInvalid blueprint").setStyle(Style.EMPTY));
            return;
        }

        SchematicRegistry.SchematicEntry entry = SchematicRegistry.getEntry(schematicId);
        if (entry == null) {
            tooltip.add(Component.literal("§c[Definition not loaded]").setStyle(Style.EMPTY));
            return;
        }

        for (Component descLine : entry.description) {
            tooltip.add(descLine);
        }

        if (entry.prefabStructure != null) {
            int blockCount = entry.prefabStructure.getBlocks().size();
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("Blocks: " + blockCount)
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY)));
        }

        if (!entry.buildingCost.isEmpty()) {
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("§6Required Materials:"));
            for (var cost : entry.buildingCost.entrySet()) {
                String itemName = cost.getKey().toString();
                tooltip.add(Component.literal("  - " + cost.getValue() + "x " + itemName)
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));
            }
        }

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("[" + entry.category + "]")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.BLUE)));
    }
}

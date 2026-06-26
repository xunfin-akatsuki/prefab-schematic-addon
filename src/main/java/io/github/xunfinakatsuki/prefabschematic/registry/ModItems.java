package io.github.xunfinakatsuki.prefabschematic.registry;

import io.github.xunfinakatsuki.prefabschematic.PrefabSchematicAddon;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Handles item registration and the creative tab for schematic-based structures.
 * Uses a single "Schematic Blueprint" item type with NBT to distinguish
 * different schematics — supporting fully dynamic data pack-driven content.
 */
public class ModItems {

    public static RegistryObject<Item> SCHEMATIC_BLUEPRINT;
    public static RegistryObject<CreativeModeTab> SCHEMATIC_TAB;

    /**
     * Register items and creative tab. Called from main mod class constructor.
     */
    public static void register() {
        SCHEMATIC_BLUEPRINT = PrefabSchematicAddon.ITEMS.register(
                "schematic_blueprint",
                () -> new SchematicBlueprintItem(new Item.Properties().stacksTo(1)));

        SCHEMATIC_TAB = PrefabSchematicAddon.CREATIVE_TABS.register(
                "schematic_tab",
                () -> CreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.prefabschem"))
                        .icon(() -> {
                            var entries = SchematicRegistry.getAllEntries();
                            if (!entries.isEmpty()) {
                                var first = entries.iterator().next();
                                return createBlueprintStack(first.id);
                            }
                            return new ItemStack(SCHEMATIC_BLUEPRINT.get());
                        })
                        .displayItems((params, output) -> {
                            var sorted = new ArrayList<>(SchematicRegistry.getAllEntries());
                            sorted.sort(Comparator
                                    .comparing((SchematicRegistry.SchematicEntry e) -> e.category)
                                    .thenComparing(e -> e.id.getPath()));

                            for (var entry : sorted) {
                                output.accept(createBlueprintStack(entry.id));
                            }
                        })
                        .build());
    }

    /**
     * Called by SchematicRegistry after reload to signal that items have changed.
     * Currently a no-op since the creative tab reads from the registry dynamically.
     */
    public static void refreshSchematicItems() {
        // Creative tab displayItems reads from SchematicRegistry directly,
        // so no explicit refresh is needed.
    }

    /**
     * Create an ItemStack for a specific schematic, storing the ID in NBT.
     */
    public static ItemStack createBlueprintStack(ResourceLocation schematicId) {
        ItemStack stack = new ItemStack(SCHEMATIC_BLUEPRINT.get());
        stack.getOrCreateTag().putString("SchematicId",
                schematicId.getNamespace() + ":" + schematicId.getPath());
        return stack;
    }

    /**
     * Extract the schematic ID from an item stack's NBT.
     */
    @Nullable
    public static ResourceLocation getSchematicId(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("SchematicId")) {
            String raw = stack.getTag().getString("SchematicId");
            String[] parts = raw.split(":", 2);
            return new ResourceLocation(parts[0], parts[1]);
        }
        return null;
    }
}

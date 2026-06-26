package io.github.xunfinakatsuki.prefabschematic;

import com.mojang.logging.LogUtils;
import io.github.xunfinakatsuki.prefabschematic.config.SchematicConfig;
import io.github.xunfinakatsuki.prefabschematic.network.BuildSchematicPacket;
import io.github.xunfinakatsuki.prefabschematic.registry.ModItems;
import io.github.xunfinakatsuki.prefabschematic.registry.SchematicRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(PrefabSchematicAddon.MOD_ID)
public class PrefabSchematicAddon {
    public static final String MOD_ID = "prefabschem";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public PrefabSchematicAddon() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::onCommonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);

        // Register the universal blueprint item and creative tab
        ModItems.register();

        // Register config (1.20.1 compatible API)
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                ModConfig.Type.COMMON, SchematicConfig.SPEC);

        // Register for game events (reload listener, etc.)
        MinecraftForge.EVENT_BUS.register(this);

        // Register network channel for client→server build packets
        BuildSchematicPacket.register();
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Prefab Schematic Addon initializing...");
        event.enqueueWork(() -> {
            LOGGER.info("Prefab Schematic Addon ready. Waiting for resource reload to load schematics...");
        });
    }

    /**
     * Register the schematic reload listener when the server is about to start.
     */
    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        LOGGER.info("Registering schematic definition loader...");
        event.addListener(SchematicRegistry.createReloadListener());
    }
}

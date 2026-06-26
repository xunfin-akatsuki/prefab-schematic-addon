package io.github.xunfinakatsuki.prefabschematic.registry;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import io.github.xunfinakatsuki.prefabschematic.PrefabSchematicAddon;
import io.github.xunfinakatsuki.prefabschematic.config.SchematicConfig;
import io.github.xunfinakatsuki.prefabschematic.schematic.SchematicParser;
import io.github.xunfinakatsuki.prefabschematic.schematic.SchematicToPrefabConverter;
import com.wuest.prefab.structures.base.Structure;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Registry that manages all schematic-based prefab entries.
 * Loaded from data packs via {@code data/<namespace>/prefab_schematics/*.json}.
 */
public class SchematicRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    private static final Map<ResourceLocation, SchematicEntry> ENTRIES = new LinkedHashMap<>();
    private static final Map<String, List<ResourceLocation>> CATEGORIES = new LinkedHashMap<>();

    /**
     * A single schematic entry with all metadata.
     */
    public static class SchematicEntry {
        public ResourceLocation id;
        public String schematicFile;
        public Component displayName;
        public List<Component> description = new ArrayList<>();
        public String category = "misc";
        public boolean hasRecipe;
        public JsonObject recipeJson;
        public Map<ResourceLocation, Integer> buildingCost = new LinkedHashMap<>();
        public Set<ResourceLocation> allowedDimensions = new HashSet<>();
        public boolean canRotate = true;
        public Structure prefabStructure;
        /** Raw parsed schematic data for direct block placement (bypasses Prefab rotation). */
        public SchematicParser.SchematicData schematicData;
    }

    // ========== Public API ==========

    public static SchematicEntry getEntry(ResourceLocation id) { return ENTRIES.get(id); }

    public static Collection<SchematicEntry> getAllEntries() {
        return Collections.unmodifiableCollection(ENTRIES.values());
    }

    public static Set<ResourceLocation> getAllIds() {
        return Collections.unmodifiableSet(ENTRIES.keySet());
    }

    public static List<ResourceLocation> getByCategory(String category) {
        return CATEGORIES.getOrDefault(category, Collections.emptyList());
    }

    public static Set<String> getCategories() {
        return Collections.unmodifiableSet(CATEGORIES.keySet());
    }

    public static SchematicReloadListener createReloadListener() {
        return new SchematicReloadListener();
    }

    // ========== Reload Listener ==========

    public static class SchematicReloadListener extends SimpleJsonResourceReloadListener {
        public SchematicReloadListener() {
            super(GSON, "prefab_schematics");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> definitions,
                            ResourceManager resourceManager, ProfilerFiller profiler) {
            ENTRIES.clear();
            CATEGORIES.clear();

            for (var entry : definitions.entrySet()) {
                ResourceLocation id = entry.getKey();
                try {
                    JsonObject json = entry.getValue().getAsJsonObject();
                    SchematicEntry schemEntry = parseDefinition(id, json);

                    loadSchematicStructure(schemEntry, resourceManager);

                    ENTRIES.put(id, schemEntry);
                    CATEGORIES.computeIfAbsent(schemEntry.category,
                            k -> new ArrayList<>()).add(id);

                    LOGGER.info("Loaded prefab schematic: {} -> {} ({} blocks)",
                            id, schemEntry.schematicFile,
                            schemEntry.prefabStructure != null
                                    ? schemEntry.prefabStructure.getBlocks().size() : 0);

                } catch (Exception e) {
                    LOGGER.error("Failed to load prefab schematic definition: {}", id, e);
                }
            }

            LOGGER.info("Loaded {} prefab schematics across {} categories",
                    ENTRIES.size(), CATEGORIES.size());

            // Auto-discover .schem files from WorldEdit's schematics directory
            scanWorldEditSchematics();

            ModItems.refreshSchematicItems();
        }

        private SchematicEntry parseDefinition(ResourceLocation id, JsonObject json) {
            SchematicEntry entry = new SchematicEntry();
            entry.id = id;
            entry.schematicFile = json.get("schematic_file").getAsString();

            if (json.has("display_name")) {
                entry.displayName = Component.Serializer.fromJson(json.get("display_name"));
            } else {
                entry.displayName = Component.literal(id.getPath());
            }

            if (json.has("description")) {
                JsonArray descArr = json.getAsJsonArray("description");
                for (JsonElement elem : descArr) {
                    entry.description.add(Component.Serializer.fromJson(elem));
                }
            }

            if (json.has("category")) {
                entry.category = json.get("category").getAsString();
            }

            if (json.has("recipe")) {
                entry.recipeJson = json.getAsJsonObject("recipe");
                entry.hasRecipe = true;
            }

            if (json.has("building_cost")) {
                JsonObject costObj = json.getAsJsonObject("building_cost");
                for (var costEntry : costObj.entrySet()) {
                    String[] keyParts = costEntry.getKey().split(":", 2);
                    ResourceLocation itemId = new ResourceLocation(keyParts[0], keyParts[1]);
                    int count = costEntry.getValue().getAsInt();
                    entry.buildingCost.put(itemId, count);
                }
            }

            if (json.has("config")) {
                JsonObject config = json.getAsJsonObject("config");
                if (config.has("allow_in_dimensions")) {
                    for (JsonElement dim : config.getAsJsonArray("allow_in_dimensions")) {
                        String[] dimParts = dim.getAsString().split(":", 2);
                        entry.allowedDimensions.add(new ResourceLocation(dimParts[0], dimParts[1]));
                    }
                }
                if (config.has("can_rotate")) {
                    entry.canRotate = config.get("can_rotate").getAsBoolean();
                }
            }

            return entry;
        }

        private void loadSchematicStructure(SchematicEntry entry, ResourceManager resourceManager) {
            // First try: data pack resource (e.g. data/<namespace>/schematics/<file>)
            if (resourceManager != null) {
                ResourceLocation schemLocation = new ResourceLocation(
                        entry.id.getNamespace(), "schematics/" + entry.schematicFile);

                try {
                    var resourceOpt = resourceManager.getResource(schemLocation);
                    if (resourceOpt.isPresent()) {
                        try (InputStream input = resourceOpt.get().open()) {
                            SchematicParser.SchematicData data = SchematicParser.parse(input);
                            entry.schematicData = data;
                            entry.prefabStructure = SchematicToPrefabConverter.convert(
                                    data, entry.id.getPath());
                            LOGGER.debug("Converted schematic {} ({}x{}x{}) from data pack to Prefab Structure with {} blocks",
                                    schemLocation, data.width, data.height, data.length,
                                    entry.prefabStructure.getBlocks().size());
                            return;
                        }
                    }
                } catch (IOException e) {
                    LOGGER.warn("Failed to load schematic from data pack: {}", schemLocation, e);
                }
            }

            // Second try: filesystem fallback — <gameDir>/config/worldedit/schematics/<file>
            Path worldEditSchematicsDir = FMLPaths.GAMEDIR.get().resolve("config/worldedit/schematics");
            Path schemFile = worldEditSchematicsDir.resolve(entry.schematicFile);

            if (Files.exists(schemFile)) {
                try (InputStream input = new BufferedInputStream(Files.newInputStream(schemFile))) {
                    SchematicParser.SchematicData data = SchematicParser.parse(input);
                    entry.schematicData = data;
                    entry.prefabStructure = SchematicToPrefabConverter.convert(
                            data, entry.id.getPath());
                    LOGGER.info("Loaded schematic from filesystem: {} ({}x{}x{}), {} blocks",
                            schemFile, data.width, data.height, data.length,
                            entry.prefabStructure.getBlocks().size());
                } catch (IOException e) {
                    LOGGER.error("Failed to load schematic from filesystem: {}", schemFile, e);
                }
            } else {
                LOGGER.error("Schematic file not found in data pack or filesystem: {} (looked on disk at {})",
                        entry.schematicFile, schemFile);
            }
        }

        /**
         * Scan config/worldedit/schematics/ for .schem files without matching
         * JSON definitions and auto-register them with sensible defaults.
         */
        private void scanWorldEditSchematics() {
            Path worldEditSchematicsDir = FMLPaths.GAMEDIR.get().resolve("config/worldedit/schematics");

            if (!Files.isDirectory(worldEditSchematicsDir)) {
                return;
            }

            try (DirectoryStream<Path> files = Files.newDirectoryStream(worldEditSchematicsDir, "*.schem")) {
                for (Path schemPath : files) {
                    String fileName = schemPath.getFileName().toString();

                    // Check if any existing entry already references this file
                    boolean alreadyRegistered = ENTRIES.values().stream()
                            .anyMatch(e -> fileName.equals(e.schematicFile));

                    if (alreadyRegistered) {
                        continue;
                    }

                    // Create an auto-entry for this schematic
                    String stem = fileName.substring(0, fileName.lastIndexOf('.'));
                    ResourceLocation id = new ResourceLocation(PrefabSchematicAddon.MOD_ID, stem);

                    // Check disabled list
                    if (SchematicConfig.INSTANCE.disabledSchematics.get().contains(id.toString())) {
                        LOGGER.info("Auto-discovered schematic {} is disabled in config, skipping", id);
                        continue;
                    }

                    SchematicEntry entry = new SchematicEntry();
                    entry.id = id;
                    entry.schematicFile = fileName;
                    entry.displayName = Component.literal(
                            stem.substring(0, 1).toUpperCase() + stem.substring(1).replace('_', ' '));
                    entry.category = "worldedit";

                    loadSchematicStructure(entry, null);

                    // Also parse directly for raw block data
                    try (InputStream is = new BufferedInputStream(Files.newInputStream(schemPath))) {
                        entry.schematicData = SchematicParser.parse(is);
                    } catch (IOException ignored) {}

                    if (entry.prefabStructure != null) {
                        int maxSize = SchematicConfig.INSTANCE.maxSchematicSize.get();
                        if (maxSize > 0 && entry.prefabStructure.getBlocks().size() > maxSize) {
                            LOGGER.warn("Auto-discovered schematic {} exceeds max size ({} > {}), skipping",
                                    id, entry.prefabStructure.getBlocks().size(), maxSize);
                            continue;
                        }

                        ENTRIES.put(id, entry);
                        CATEGORIES.computeIfAbsent(entry.category, k -> new ArrayList<>()).add(id);
                        LOGGER.info("Auto-registered schematic from WorldEdit: {} -> {} ({} blocks)",
                                id, fileName, entry.prefabStructure.getBlocks().size());
                    } else {
                        LOGGER.warn("Failed to parse auto-discovered schematic: {}", fileName);
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Failed to scan WorldEdit schematics directory: {}", worldEditSchematicsDir, e);
            }
        }
    }
}

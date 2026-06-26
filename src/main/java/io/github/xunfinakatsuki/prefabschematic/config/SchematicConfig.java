package io.github.xunfinakatsuki.prefabschematic.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public class SchematicConfig {

    public static final ForgeConfigSpec SPEC;
    public static final SchematicConfig INSTANCE;

    // === General ===
    public final ForgeConfigSpec.BooleanValue enableSchematicBuildings;
    public final ForgeConfigSpec.IntValue maxSchematicSize;

    // === Restrictions ===
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> disabledSchematics;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> dimensionBlacklist;

    static {
        Pair<SchematicConfig, ForgeConfigSpec> pair =
                new ForgeConfigSpec.Builder().configure(SchematicConfig::new);
        SPEC = pair.getRight();
        INSTANCE = pair.getLeft();
    }

    private SchematicConfig(ForgeConfigSpec.Builder builder) {
        builder.push("General");

        enableSchematicBuildings = builder
                .comment("Master switch to enable or disable all schematic-based prefab buildings.")
                .define("enableSchematicBuildings", true);

        maxSchematicSize = builder
                .comment("Maximum number of blocks allowed in a single schematic. " +
                        "0 = no limit.")
                .defineInRange("maxSchematicSize", 50000, 0, Integer.MAX_VALUE);

        builder.pop();

        builder.push("Restrictions");

        disabledSchematics = builder
                .comment("List of schematic IDs to disable. " +
                        "Format: 'namespace:path' (e.g., 'prefabschem:my_house').")
                .defineList("disabledSchematics", ArrayList::new,
                        obj -> obj instanceof String && ((String) obj).contains(":"));

        dimensionBlacklist = builder
                .comment("Dimensions where schematic buildings cannot be placed.")
                .defineList("dimensionBlacklist",
                        ArrayList::new,
                        obj -> obj instanceof String && ((String) obj).contains(":"));

        builder.pop();
    }
}

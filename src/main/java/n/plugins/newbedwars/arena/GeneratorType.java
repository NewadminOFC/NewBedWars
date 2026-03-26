package n.plugins.newbedwars.arena;

import org.bukkit.Material;

public enum GeneratorType {
    IRON("Ferro", Material.IRON_INGOT, "generators.iron.interval-seconds", 1),
    GOLD("Ouro", Material.GOLD_INGOT, "generators.gold.interval-seconds", 4),
    DIAMOND("Diamante", Material.DIAMOND, "generators.diamond.interval-seconds", 10),
    EMERALD("Esmeralda", Material.EMERALD, "generators.emerald.interval-seconds", 15);

    private final String displayName;
    private final Material dropMaterial;
    private final String configPath;
    private final int defaultInterval;

    GeneratorType(String displayName, Material dropMaterial, String configPath, int defaultInterval) {
        this.displayName = displayName;
        this.dropMaterial = dropMaterial;
        this.configPath = configPath;
        this.defaultInterval = defaultInterval;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getDropMaterial() {
        return dropMaterial;
    }

    public String getConfigPath() {
        return configPath;
    }

    public int getDefaultInterval() {
        return defaultInterval;
    }
}

package n.plugins.newbedwars.arena;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import n.plugins.newbedwars.util.CuboidRegion;
import org.bukkit.Location;

public class ArenaTeam {

    private final TeamColor color;
    private final Map<GeneratorType, List<GeneratorPoint>> generators;
    private Location spawnLocation;
    private BedData bedData;
    private Location itemShopLocation;
    private Location upgradeShopLocation;
    private CuboidRegion islandRegion;
    private CuboidRegion protectionRegion;
    private boolean bedDestroyed;
    private boolean eliminated;
    private boolean confirmed;
    private boolean sharpenedSwords;
    private int protectionTier;
    private int maniacMinerTier;
    private boolean healPool;

    public ArenaTeam(TeamColor color) {
        this.color = color;
        this.generators = new EnumMap<GeneratorType, List<GeneratorPoint>>(GeneratorType.class);
        for (GeneratorType type : GeneratorType.values()) {
            this.generators.put(type, new ArrayList<GeneratorPoint>());
        }
    }

    public TeamColor getColor() {
        return color;
    }

    public Location getSpawnLocation() {
        return spawnLocation == null ? null : spawnLocation.clone();
    }

    public void setSpawnLocation(Location spawnLocation) {
        this.spawnLocation = spawnLocation == null ? null : spawnLocation.clone();
        this.confirmed = false;
    }

    public BedData getBedData() {
        return bedData;
    }

    public void setBedData(BedData bedData) {
        this.bedData = bedData;
        this.confirmed = false;
    }

    public Location getItemShopLocation() {
        return itemShopLocation == null ? null : itemShopLocation.clone();
    }

    public void setItemShopLocation(Location itemShopLocation) {
        this.itemShopLocation = itemShopLocation == null ? null : itemShopLocation.clone();
        this.confirmed = false;
    }

    public Location getUpgradeShopLocation() {
        return upgradeShopLocation == null ? null : upgradeShopLocation.clone();
    }

    public void setUpgradeShopLocation(Location upgradeShopLocation) {
        this.upgradeShopLocation = upgradeShopLocation == null ? null : upgradeShopLocation.clone();
        this.confirmed = false;
    }

    public CuboidRegion getIslandRegion() {
        return islandRegion;
    }

    public void setIslandRegion(CuboidRegion islandRegion) {
        this.islandRegion = islandRegion;
        this.confirmed = false;
    }

    public CuboidRegion getProtectionRegion() {
        return protectionRegion;
    }

    public void setProtectionRegion(CuboidRegion protectionRegion) {
        this.protectionRegion = protectionRegion;
        this.confirmed = false;
    }

    public void setSingleGenerator(GeneratorType type, Location location) {
        List<GeneratorPoint> points = generators.get(type);
        points.clear();
        if (location != null) {
            points.add(new GeneratorPoint(type, location, color));
        }
        this.confirmed = false;
    }

    public List<GeneratorPoint> getGenerators(GeneratorType type) {
        return generators.get(type);
    }

    public Map<GeneratorType, List<GeneratorPoint>> getAllGenerators() {
        return generators;
    }

    public boolean isBedDestroyed() {
        return bedDestroyed;
    }

    public void setBedDestroyed(boolean bedDestroyed) {
        this.bedDestroyed = bedDestroyed;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public void resetRuntime() {
        this.bedDestroyed = false;
        this.eliminated = false;
        this.sharpenedSwords = false;
        this.protectionTier = 0;
        this.maniacMinerTier = 0;
        this.healPool = false;
    }

    public boolean hasSharpenedSwords() {
        return sharpenedSwords;
    }

    public void setSharpenedSwords(boolean sharpenedSwords) {
        this.sharpenedSwords = sharpenedSwords;
    }

    public int getProtectionTier() {
        return protectionTier;
    }

    public void setProtectionTier(int protectionTier) {
        this.protectionTier = protectionTier;
    }

    public int getManiacMinerTier() {
        return maniacMinerTier;
    }

    public void setManiacMinerTier(int maniacMinerTier) {
        this.maniacMinerTier = maniacMinerTier;
    }

    public boolean hasHealPool() {
        return healPool;
    }

    public void setHealPool(boolean healPool) {
        this.healPool = healPool;
    }

    public boolean isSetupComplete() {
        return getMissingSetup().isEmpty();
    }

    public List<String> getMissingSetup() {
        List<String> missing = new ArrayList<String>();

        if (spawnLocation == null) {
            missing.add("Spawn do time");
        }
        if (bedData == null || !bedData.isConfigured()) {
            missing.add("Cama");
        }
        if (generators.get(GeneratorType.IRON).isEmpty()) {
            missing.add("Gerador de ferro");
        }
        if (generators.get(GeneratorType.GOLD).isEmpty()) {
            missing.add("Gerador de ouro");
        }
        if (itemShopLocation == null) {
            missing.add("Loja de itens");
        }
        if (upgradeShopLocation == null) {
            missing.add("Loja de upgrades");
        }
        if (islandRegion == null || !islandRegion.isComplete()) {
            missing.add("Regiao da ilha");
        }
        if (protectionRegion == null || !protectionRegion.isComplete()) {
            missing.add("Protecao inicial");
        }

        return missing;
    }

    public String getProgressLine() {
        List<String> missing = getMissingSetup();
        if (missing.isEmpty()) {
            return "§aCompleto";
        }
        return "§cFaltando: " + Arrays.toString(missing.toArray()).replace("[", "").replace("]", "");
    }
    public void copySetupFrom(ArenaTeam source) {
        if (source == null) {
            return;
        }

        this.spawnLocation = source.getSpawnLocation();
        BedData sourceBed = source.getBedData();
        this.bedData = sourceBed == null ? null : new BedData(sourceBed.getHead(), sourceBed.getFoot());
        this.itemShopLocation = source.getItemShopLocation();
        this.upgradeShopLocation = source.getUpgradeShopLocation();
        this.islandRegion = source.getIslandRegion() == null ? null : new CuboidRegion(source.getIslandRegion().getPos1(), source.getIslandRegion().getPos2());
        this.protectionRegion = source.getProtectionRegion() == null ? null : new CuboidRegion(source.getProtectionRegion().getPos1(), source.getProtectionRegion().getPos2());
        this.confirmed = source.isConfirmed();

        for (GeneratorType type : GeneratorType.values()) {
            List<GeneratorPoint> targetPoints = generators.get(type);
            targetPoints.clear();
            for (GeneratorPoint point : source.getGenerators(type)) {
                targetPoints.add(new GeneratorPoint(point.getType(), point.getLocation(), point.getOwner()));
            }
        }

        resetRuntime();
    }
}

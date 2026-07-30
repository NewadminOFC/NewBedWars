package n.plugins.newbedwars.manager;

import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaState;
import n.plugins.newbedwars.arena.ArenaTeam;
import n.plugins.newbedwars.arena.GeneratorPoint;
import n.plugins.newbedwars.arena.GeneratorType;
import n.plugins.newbedwars.arena.TeamColor;
import n.plugins.newbedwars.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class GeneratorManager {

    private final NewBedWars plugin;
    private int taskId = -1;
    private final int[] cachedIntervals = new int[4];
    private final int[] cachedMaxNearby = new int[4];
    private long configCacheTime;
    private boolean cachedMergeDrops = true;
    private boolean cachedSkipUnloadedChunks = true;

    public GeneratorManager(NewBedWars plugin) {
        this.plugin = plugin;
    }

    private void refreshConfigCache() {
        long now = System.currentTimeMillis();
        if (now - configCacheTime < 5000L) {
            return;
        }
        configCacheTime = now;
        GeneratorType[] types = GeneratorType.values();
        for (int i = 0; i < types.length; i++) {
            cachedIntervals[i] = plugin.getConfig().getInt(types[i].getConfigPath(), types[i].getDefaultInterval());
            cachedMaxNearby[i] = plugin.getConfig().getInt(types[i].getConfigPath().replace("interval-seconds", "max-nearby-items"), 16);
        }
        cachedMergeDrops = plugin.getConfig().getBoolean("performance.generators.merge-drops", true);
        cachedSkipUnloadedChunks = plugin.getConfig().getBoolean("performance.generators.skip-unloaded-chunks", true);
    }

    public void start() {
        if (taskId != -1) {
            return;
        }

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                tickGenerators();
            }
        }, 20L, 20L);
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void tickGenerators() {
        refreshConfigCache();
        for (Arena arena : plugin.getArenaManager().getRuntimeArenasView()) {
            if (arena.getState() != ArenaState.INGAME) {
                continue;
            }

            for (TeamColor color : plugin.getTeamManager().getActiveColors(arena)) {
                ArenaTeam team = arena.getTeam(color);
                if (team == null) {
                    continue;
                }

                spawnPoints(arena, team.getGenerators(GeneratorType.IRON), GeneratorType.IRON, arena.getElapsedTime());
                spawnPoints(arena, team.getGenerators(GeneratorType.GOLD), GeneratorType.GOLD, arena.getElapsedTime());
            }

            spawnPoints(arena, arena.getGlobalGenerators(GeneratorType.DIAMOND), GeneratorType.DIAMOND, arena.getElapsedTime());
            spawnPoints(arena, arena.getGlobalGenerators(GeneratorType.EMERALD), GeneratorType.EMERALD, arena.getElapsedTime());
        }
    }

    private void spawnPoints(Arena arena, java.util.List<GeneratorPoint> points, GeneratorType type, int elapsedTime) {
        int interval = cachedIntervals[type.ordinal()];
        if (interval <= 0 || elapsedTime % interval != 0) {
            return;
        }

        for (GeneratorPoint point : points) {
            Location spawnLocation = point == null ? null : arena.getMatchLocation(LocationUtil.normalizeGeneratorLocation(point.getLocation()));
            if (spawnLocation == null || spawnLocation.getWorld() == null) {
                continue;
            }

            if (cachedSkipUnloadedChunks
                && !spawnLocation.getWorld().isChunkLoaded(spawnLocation.getBlockX() >> 4, spawnLocation.getBlockZ() >> 4)) {
                continue;
            }

            int amountToDrop = prepareNearbyStack(spawnLocation, type);
            if (amountToDrop <= 0) {
                continue;
            }

            Item dropped = spawnLocation.getWorld().dropItem(spawnLocation, new ItemStack(type.getDropMaterial(), amountToDrop));
            dropped.setVelocity(ZERO_VECTOR);
            dropped.setPickupDelay(0);
        }
    }

    private static final Vector ZERO_VECTOR = new Vector(0.0D, 0.0D, 0.0D);

    private int prepareNearbyStack(Location location, GeneratorType type) {
        int max = Math.max(1, cachedMaxNearby[type.ordinal()]);
        int amount = 0;
        Material targetMaterial = type.getDropMaterial();
        Item mergeTarget = null;

        for (org.bukkit.entity.Entity entity : location.getWorld().getNearbyEntities(location, 1.5D, 1.5D, 1.5D)) {
            if (!(entity instanceof Item)) {
                continue;
            }

            Item item = (Item) entity;
            if (item.getItemStack().getType() == targetMaterial) {
                amount += item.getItemStack().getAmount();
                if (amount >= max) {
                    return 0;
                }
                if (cachedMergeDrops
                    && mergeTarget == null
                    && item.getItemStack().getAmount() < item.getItemStack().getMaxStackSize()) {
                    mergeTarget = item;
                }
            }
        }

        if (cachedMergeDrops && mergeTarget != null) {
            ItemStack stack = mergeTarget.getItemStack();
            if (stack.getAmount() < stack.getMaxStackSize() && amount < max) {
                stack.setAmount(stack.getAmount() + 1);
                mergeTarget.setItemStack(stack);
                return 0;
            }
        }
        return 1;
    }

    public void invalidateConfigurationCache() {
        configCacheTime = 0L;
    }
}

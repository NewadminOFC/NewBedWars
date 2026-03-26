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

    public GeneratorManager(NewBedWars plugin) {
        this.plugin = plugin;
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
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            if (arena.getState() != ArenaState.INGAME) {
                continue;
            }

            for (TeamColor color : plugin.getTeamManager().getActiveColors()) {
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
        int interval = plugin.getConfig().getInt(type.getConfigPath(), type.getDefaultInterval());
        if (interval <= 0 || elapsedTime % interval != 0) {
            return;
        }

        for (GeneratorPoint point : points) {
            Location spawnLocation = point == null ? null : arena.getMatchLocation(LocationUtil.normalizeGeneratorLocation(point.getLocation()));
            if (spawnLocation == null || spawnLocation.getWorld() == null) {
                continue;
            }

            if (tooManyNearbyItems(spawnLocation, type)) {
                continue;
            }

            Item dropped = spawnLocation.getWorld().dropItem(spawnLocation, new ItemStack(type.getDropMaterial(), 1));
            dropped.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
            dropped.setPickupDelay(0);
        }
    }

    private boolean tooManyNearbyItems(Location location, GeneratorType type) {
        int max = plugin.getConfig().getInt(type.getConfigPath().replace("interval-seconds", "max-nearby-items"), 16);
        int amount = 0;

        for (org.bukkit.entity.Entity entity : location.getWorld().getNearbyEntities(location, 1.75D, 1.75D, 1.75D)) {
            if (!(entity instanceof Item)) {
                continue;
            }

            Item item = (Item) entity;
            Material material = item.getItemStack().getType();
            if (material == type.getDropMaterial()) {
                amount += item.getItemStack().getAmount();
            }
        }

        return amount >= max;
    }
}

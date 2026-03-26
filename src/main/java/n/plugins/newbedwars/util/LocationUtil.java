package n.plugins.newbedwars.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;

public final class LocationUtil {

    private LocationUtil() {
    }

    public static void saveLocation(ConfigurationSection section, String path, Location location) {
        if (location == null) {
            section.set(path, null);
            return;
        }

        section.set(path + ".world", location.getWorld().getName());
        section.set(path + ".x", location.getX());
        section.set(path + ".y", location.getY());
        section.set(path + ".z", location.getZ());
        section.set(path + ".yaw", location.getYaw());
        section.set(path + ".pitch", location.getPitch());
    }

    public static Location loadLocation(ConfigurationSection section, String path) {
        if (!section.isConfigurationSection(path)) {
            return null;
        }

        String worldName = section.getString(path + ".world");
        World world = ensureWorldLoaded(worldName);
        if (world == null) {
            return null;
        }

        double x = section.getDouble(path + ".x");
        double y = section.getDouble(path + ".y");
        double z = section.getDouble(path + ".z");
        float yaw = (float) section.getDouble(path + ".yaw");
        float pitch = (float) section.getDouble(path + ".pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    public static Location centerBlock(Location location) {
        if (location == null) {
            return null;
        }
        return new Location(location.getWorld(), location.getBlockX() + 0.5D, location.getBlockY(), location.getBlockZ() + 0.5D, location.getYaw(), location.getPitch());
    }

    public static Location topCenter(Location location) {
        if (location == null) {
            return null;
        }
        return new Location(location.getWorld(), location.getBlockX() + 0.5D, location.getBlockY() + 1.0D, location.getBlockZ() + 0.5D, location.getYaw(), location.getPitch());
    }

    public static Location generatorDropLocation(Location location) {
        if (location == null) {
            return null;
        }
        return new Location(location.getWorld(), location.getBlockX() + 0.5D, location.getBlockY() + 1.15D, location.getBlockZ() + 0.5D, location.getYaw(), location.getPitch());
    }

    public static Location normalizeGeneratorLocation(Location location) {
        if (location == null) {
            return null;
        }

        double yFraction = location.getY() - Math.floor(location.getY());
        if (yFraction < 0.05D) {
            return generatorDropLocation(location);
        }
        return location.clone();
    }

    public static Location npcSpawnLocation(Location location) {
        if (location == null) {
            return null;
        }
        return new Location(location.getWorld(), location.getBlockX() + 0.5D, location.getBlockY() + 1.0D, location.getBlockZ() + 0.5D, location.getYaw(), location.getPitch());
    }

    public static Location relocate(Location location, World world) {
        if (location == null || world == null) {
            return location == null ? null : location.clone();
        }

        return new Location(world, location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public static CuboidRegion relocate(CuboidRegion region, World world) {
        if (region == null || world == null) {
            return region;
        }

        Location pos1 = relocate(region.getPos1(), world);
        Location pos2 = relocate(region.getPos2(), world);
        return pos1 == null || pos2 == null ? null : new CuboidRegion(pos1, pos2);
    }

    public static World ensureWorldLoaded(String worldName) {
        if (worldName == null || worldName.trim().isEmpty()) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return world;
        }

        try {
            return Bukkit.createWorld(new WorldCreator(worldName));
        } catch (Exception exception) {
            return null;
        }
    }

    public static boolean sameBlock(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) {
            return false;
        }

        return first.getWorld().getName().equalsIgnoreCase(second.getWorld().getName())
            && first.getBlockX() == second.getBlockX()
            && first.getBlockY() == second.getBlockY()
            && first.getBlockZ() == second.getBlockZ();
    }
}

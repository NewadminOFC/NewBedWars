package n.plugins.newbedwars.util;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public class CuboidRegion {

    private final Location pos1;
    private final Location pos2;

    public CuboidRegion(Location pos1, Location pos2) {
        this.pos1 = pos1 == null ? null : pos1.clone();
        this.pos2 = pos2 == null ? null : pos2.clone();
    }

    public Location getPos1() {
        return pos1 == null ? null : pos1.clone();
    }

    public Location getPos2() {
        return pos2 == null ? null : pos2.clone();
    }

    public boolean isComplete() {
        return pos1 != null && pos2 != null && pos1.getWorld() != null && pos2.getWorld() != null;
    }

    public String getWorldName() {
        return isComplete() ? pos1.getWorld().getName() : null;
    }

    public boolean contains(Location location) {
        if (!isComplete() || location == null || location.getWorld() == null) {
            return false;
        }

        if (!location.getWorld().getName().equalsIgnoreCase(getWorldName())) {
            return false;
        }

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        return location.getBlockX() >= minX && location.getBlockX() <= maxX
            && location.getBlockY() >= minY && location.getBlockY() <= maxY
            && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
    }

    public List<Block> getBlocks() {
        List<Block> blocks = new ArrayList<Block>();
        if (!isComplete()) {
            return blocks;
        }

        World world = Bukkit.getWorld(getWorldName());
        if (world == null) {
            return blocks;
        }

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    blocks.add(world.getBlockAt(x, y, z));
                }
            }
        }
        return blocks;
    }
}

package n.plugins.newbedwars.util;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public class CuboidRegion {

    private final Location pos1;
    private final Location pos2;
    private final String worldName;
    private int minX, minY, minZ, maxX, maxY, maxZ;
    private boolean boundsComputed;

    public CuboidRegion(Location pos1, Location pos2) {
        this(pos1, pos2, resolveWorldName(pos1, pos2));
    }

    public CuboidRegion(Location pos1, Location pos2, String worldName) {
        this.pos1 = pos1 == null ? null : pos1.clone();
        this.pos2 = pos2 == null ? null : pos2.clone();
        this.worldName = worldName;
    }

    private static String resolveWorldName(Location first, Location second) {
        if (first != null && first.getWorld() != null) {
            return first.getWorld().getName();
        }
        if (second != null && second.getWorld() != null) {
            return second.getWorld().getName();
        }
        return null;
    }

    private void ensureBounds() {
        if (boundsComputed) {
            return;
        }
        minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        boundsComputed = true;
    }

    public Location getPos1() {
        return pos1 == null ? null : pos1.clone();
    }

    public Location getPos2() {
        return pos2 == null ? null : pos2.clone();
    }

    public boolean isComplete() {
        return pos1 != null && pos2 != null && worldName != null && !worldName.trim().isEmpty();
    }

    public String getWorldName() {
        return isComplete() ? worldName : null;
    }

    public boolean contains(Location location) {
        if (!isComplete() || location == null || location.getWorld() == null) {
            return false;
        }

        if (!location.getWorld().getName().equalsIgnoreCase(getWorldName())) {
            return false;
        }

        ensureBounds();
        return location.getBlockX() >= minX && location.getBlockX() <= maxX
            && location.getBlockY() >= minY && location.getBlockY() <= maxY
            && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
    }

    public long getVolume() {
        if (!isComplete()) {
            return 0L;
        }
        ensureBounds();
        return (long) (maxX - minX + 1)
            * (long) (maxY - minY + 1)
            * (long) (maxZ - minZ + 1);
    }

    /**
     * Iterates lazily so large setup regions are never materialized as a giant
     * list on the server thread.
     */
    public Iterator<Block> blockIterator() {
        if (!isComplete()) {
            return Collections.<Block>emptyList().iterator();
        }
        World world = Bukkit.getWorld(getWorldName());
        if (world == null) {
            return Collections.<Block>emptyList().iterator();
        }

        ensureBounds();
        final World iteratorWorld = world;
        return new Iterator<Block>() {
            private int x = minX;
            private int y = minY;
            private int z = minZ;
            private boolean hasNext = true;

            @Override
            public boolean hasNext() {
                return hasNext;
            }

            @Override
            public Block next() {
                if (!hasNext) {
                    throw new NoSuchElementException();
                }
                Block block = iteratorWorld.getBlockAt(x, y, z);
                advance();
                return block;
            }

            private void advance() {
                if (z < maxZ) {
                    z++;
                    return;
                }
                z = minZ;
                if (y < maxY) {
                    y++;
                    return;
                }
                y = minY;
                if (x < maxX) {
                    x++;
                    return;
                }
                hasNext = false;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}

package n.plugins.newbedwars.util;

import org.bukkit.Location;
import org.bukkit.block.Block;

public class BlockPosition {

    private final String world;
    private final int x;
    private final int y;
    private final int z;

    public BlockPosition(String world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static BlockPosition fromLocation(Location location) {
        return new BlockPosition(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static BlockPosition fromBlock(Block block) {
        return fromLocation(block.getLocation());
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof BlockPosition)) {
            return false;
        }

        BlockPosition that = (BlockPosition) object;
        return x == that.x && y == that.y && z == that.z && world.equals(that.world);
    }

    @Override
    public int hashCode() {
        int result = world.hashCode();
        result = 31 * result + x;
        result = 31 * result + y;
        result = 31 * result + z;
        return result;
    }
}

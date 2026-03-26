package n.plugins.newbedwars.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

public class BlockSnapshot {

    private final BlockPosition position;
    private final Material type;
    private final byte data;

    public BlockSnapshot(BlockState state) {
        this.position = BlockPosition.fromLocation(state.getLocation());
        this.type = state.getType();
        this.data = state.getRawData();
    }

    public BlockPosition getPosition() {
        return position;
    }

    public void restore() {
        World world = Bukkit.getWorld(position.getWorld());
        if (world == null) {
            return;
        }

        Block block = world.getBlockAt(position.getX(), position.getY(), position.getZ());
        block.setType(type);
        block.setData(data);
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(position.getWorld());
        return world == null ? null : new Location(world, position.getX(), position.getY(), position.getZ());
    }
}

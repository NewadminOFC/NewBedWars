package n.plugins.newbedwars.util;

import n.plugins.newbedwars.arena.BedData;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public final class BedUtil {

    private BedUtil() {
    }

    public static boolean isBed(Block block) {
        return block != null && block.getType() == Material.BED_BLOCK;
    }

    public static BedData resolveBedData(Block clicked) {
        if (!isBed(clicked)) {
            return null;
        }

        byte data = clicked.getData();
        boolean head = (data & 0x8) == 0x8;
        BlockFace direction = fromData((byte) (data & 0x7));
        Block other = head ? clicked.getRelative(opposite(direction)) : clicked.getRelative(direction);

        if (other.getType() != Material.BED_BLOCK) {
            return null;
        }

        return head
            ? new BedData(clicked.getLocation(), other.getLocation())
            : new BedData(other.getLocation(), clicked.getLocation());
    }

    private static BlockFace fromData(byte data) {
        switch (data) {
            case 0:
                return BlockFace.SOUTH;
            case 1:
                return BlockFace.WEST;
            case 2:
                return BlockFace.NORTH;
            case 3:
                return BlockFace.EAST;
            default:
                return BlockFace.NORTH;
        }
    }

    private static BlockFace opposite(BlockFace face) {
        switch (face) {
            case NORTH:
                return BlockFace.SOUTH;
            case SOUTH:
                return BlockFace.NORTH;
            case WEST:
                return BlockFace.EAST;
            case EAST:
                return BlockFace.WEST;
            default:
                return BlockFace.SELF;
        }
    }
}

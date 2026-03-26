package n.plugins.newbedwars.arena;

import n.plugins.newbedwars.util.LocationUtil;
import org.bukkit.Location;

public class BedData {

    private final Location head;
    private final Location foot;

    public BedData(Location head, Location foot) {
        this.head = head == null ? null : head.clone();
        this.foot = foot == null ? null : foot.clone();
    }

    public Location getHead() {
        return head == null ? null : head.clone();
    }

    public Location getFoot() {
        return foot == null ? null : foot.clone();
    }

    public boolean isConfigured() {
        return head != null && foot != null;
    }

    public boolean matches(Location location) {
        return LocationUtil.sameBlock(head, location) || LocationUtil.sameBlock(foot, location);
    }
}

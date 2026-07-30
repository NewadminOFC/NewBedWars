package n.plugins.newbedwars.arena;

import org.bukkit.Location;

public class GeneratorPoint {

    private final GeneratorType type;
    private final Location location;
    private final TeamColor owner;

    public GeneratorPoint(GeneratorType type, Location location, TeamColor owner) {
        this.type = type;
        this.location = location == null ? null : location.clone();
        this.owner = owner;
    }

    public GeneratorType getType() {
        return type;
    }

    public Location getLocation() {
        return location == null ? null : location.clone();
    }

    public TeamColor getOwner() {
        return owner;
    }
}

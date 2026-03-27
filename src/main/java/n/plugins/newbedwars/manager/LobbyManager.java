package n.plugins.newbedwars.manager;

import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class LobbyManager {

    private final NewBedWars plugin;

    public LobbyManager(NewBedWars plugin) {
        this.plugin = plugin;
    }

    public void setLobby(Location location) {
        LocationUtil.saveLocation(plugin.getConfig(), "lobby.spawn", location);
        plugin.saveConfig();
        applyWorldRules(location == null ? null : location.getWorld());
    }

    public Location getLobby() {
        return LocationUtil.loadLocation(plugin.getConfig(), "lobby.spawn");
    }

    public boolean hasLobby() {
        return getLobby() != null;
    }

    public boolean teleportToLobby(Player player) {
        Location lobby = getLobby();
        if (lobby == null) {
            return false;
        }

        player.teleport(lobby);
        return true;
    }

    public Location getMainWorldSpawn() {
        World world = LocationUtil.ensureWorldLoaded("world");
        if (world != null) {
            return world.getSpawnLocation();
        }

        return getLobby();
    }

    public boolean teleportToMainWorld(Player player) {
        Location target = getMainWorldSpawn();
        if (target == null) {
            return false;
        }

        player.teleport(target);
        return true;
    }

    public void applyConfiguredLobbyWorldRules() {
        Location lobby = getLobby();
        if (lobby == null || lobby.getWorld() == null) {
            return;
        }

        applyWorldRules(lobby.getWorld());
    }

    private void applyWorldRules(World world) {
        if (world == null) {
            return;
        }

        try {
            world.setGameRuleValue("doMobSpawning", "false");
        } catch (Exception ignored) {
        }

        try {
            world.setGameRuleValue("doDaylightCycle", "false");
        } catch (Exception ignored) {
        }

        try {
            world.setGameRuleValue("doWeatherCycle", "false");
        } catch (Exception ignored) {
        }

        world.setSpawnFlags(false, false);
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(0);
        world.setTime(1000L);
    }
}

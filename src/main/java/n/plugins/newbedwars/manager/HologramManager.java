package n.plugins.newbedwars.manager;

import java.util.HashMap;
import java.util.Map;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaTeam;
import n.plugins.newbedwars.arena.TeamColor;
import n.plugins.newbedwars.npc.NpcHologram;
import n.plugins.newbedwars.util.ChatUtil;
import n.plugins.newbedwars.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

public class HologramManager {

    private final NewBedWars plugin;
    private final Map<String, NpcHologram> teamChestHolograms;

    public HologramManager(NewBedWars plugin) {
        this.plugin = plugin;
        this.teamChestHolograms = new HashMap<String, NpcHologram>();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("settings.holograms", false);
    }

    public boolean isTeamChestHologramEnabled() {
        return plugin.getConfig().getBoolean("settings.team-chest-holograms", true);
    }

    public void refreshArenaChestHolograms(Arena arena) {
        if (arena == null) {
            return;
        }

        clearArenaChestHolograms(arena);
        if (!isTeamChestHologramEnabled()) {
            return;
        }

        for (TeamColor color : plugin.getTeamManager().getActiveColors()) {
            ArenaTeam team = arena.getTeam(color);
            if (team == null || team.getTeamChestLocation() == null) {
                continue;
            }

            Location location = arena.getMatchLocation(team.getTeamChestLocation());
            if (location == null || location.getWorld() == null) {
                continue;
            }

            NpcHologram hologram = new NpcHologram();
            hologram.addLine(spawnLine(LocationUtil.topCenter(location).add(0.0D, 0.95D, 0.0D),
                ChatUtil.color(plugin.getConfig().getString("team-chest-hologram.top", "&6&lBAU"))));
            hologram.addLine(spawnLine(LocationUtil.topCenter(location).add(0.0D, 0.70D, 0.0D),
                ChatUtil.color(plugin.getConfig().getString("team-chest-hologram.bottom", "&eClique para guardar"))));
            teamChestHolograms.put(buildKey(arena, color), hologram);
        }
    }

    public void clearArenaChestHolograms(Arena arena) {
        if (arena == null) {
            return;
        }

        for (TeamColor color : plugin.getTeamManager().getActiveColors()) {
            clearTeamChestHologram(arena, color);
        }
    }

    public void shutdown() {
        for (NpcHologram hologram : teamChestHolograms.values()) {
            if (hologram != null) {
                hologram.clear();
            }
        }
        teamChestHolograms.clear();
    }

    private void clearTeamChestHologram(Arena arena, TeamColor color) {
        NpcHologram hologram = teamChestHolograms.remove(buildKey(arena, color));
        if (hologram != null) {
            hologram.clear();
        }
    }

    private String buildKey(Arena arena, TeamColor color) {
        return arena.getName().toLowerCase() + ":" + color.name();
    }

    private ArmorStand spawnLine(Location location, String text) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setCustomNameVisible(true);
        stand.setCustomName(text);
        stand.setBasePlate(false);
        stand.setArms(false);
        return stand;
    }
}

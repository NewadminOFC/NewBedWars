package n.plugins.newbedwars.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaTeam;
import n.plugins.newbedwars.arena.BedWarsMode;
import n.plugins.newbedwars.arena.TeamColor;
import org.bukkit.Location;

public class TeamManager {

    public enum TeamSelectionResult {
        SUCCESS,
        ALREADY_SELECTED,
        TEAM_OCCUPIED,
        INVALID
    }

    private final NewBedWars plugin;

    public TeamManager(NewBedWars plugin) {
        this.plugin = plugin;
    }

    public List<TeamColor> getActiveColors() {
        return BedWarsMode.ONE_VS_ONE.getActiveColors();
    }

    public List<TeamColor> getActiveColors(Arena arena) {
        return arena == null ? getActiveColors() : arena.getMode().getActiveColors();
    }

    public boolean isActiveColor(TeamColor color) {
        return color != null && getActiveColors().contains(color);
    }

    public boolean isActiveColor(Arena arena, TeamColor color) {
        return color != null && getActiveColors(arena).contains(color);
    }

    public int getArenaCapacity() {
        return BedWarsMode.ONE_VS_ONE.getMaxPlayers();
    }

    public int getArenaCapacity(Arena arena) {
        return arena == null ? getArenaCapacity() : arena.getMode().getMaxPlayers();
    }

    public int getTeamSize(Arena arena) {
        return arena == null ? BedWarsMode.ONE_VS_ONE.getTeamSize() : arena.getMode().getTeamSize();
    }

    public TeamColor assignNextAvailableTeam(Arena arena, UUID uniqueId) {
        for (TeamColor color : getActiveColors(arena)) {
            if (getTeamMemberCount(arena, color) < getTeamSize(arena)) {
                arena.getPlayerTeams().put(uniqueId, color);
                return color;
            }
        }
        return null;
    }

    public TeamSelectionResult selectTeam(Arena arena, UUID uniqueId, TeamColor color) {
        if (arena == null || uniqueId == null || !isActiveColor(arena, color)) {
            return TeamSelectionResult.INVALID;
        }

        TeamColor current = arena.getPlayerTeams().get(uniqueId);
        if (current == color) {
            return TeamSelectionResult.ALREADY_SELECTED;
        }

        if (!isTeamAvailable(arena, uniqueId, color)) {
            return TeamSelectionResult.TEAM_OCCUPIED;
        }

        arena.getPlayerTeams().put(uniqueId, color);
        return TeamSelectionResult.SUCCESS;
    }

    public UUID getOccupant(Arena arena, TeamColor color) {
        List<UUID> members = getPlayersInTeam(arena, color);
        return members.isEmpty() ? null : members.get(0);
    }

    public List<UUID> getPlayersInTeam(Arena arena, TeamColor color) {
        List<UUID> players = new ArrayList<UUID>();
        if (arena == null || color == null) {
            return players;
        }

        for (Map.Entry<UUID, TeamColor> entry : arena.getPlayerTeams().entrySet()) {
            if (entry.getValue() == color && arena.getPlayers().contains(entry.getKey())) {
                players.add(entry.getKey());
            }
        }

        return players;
    }

    public int getTeamMemberCount(Arena arena, TeamColor color) {
        return getPlayersInTeam(arena, color).size();
    }

    public boolean isTeamAvailable(Arena arena, UUID uniqueId, TeamColor color) {
        List<UUID> members = getPlayersInTeam(arena, color);
        if (members.contains(uniqueId)) {
            return true;
        }
        return members.size() < getTeamSize(arena);
    }

    public void unassign(Arena arena, UUID uniqueId) {
        arena.getPlayerTeams().remove(uniqueId);
        updateEliminationState(arena);
    }

    public ArenaTeam getTeam(Arena arena, UUID uniqueId) {
        TeamColor color = arena.getPlayerTeams().get(uniqueId);
        return color == null ? null : arena.getTeam(color);
    }

    public TeamColor getColor(Arena arena, UUID uniqueId) {
        return arena.getPlayerTeams().get(uniqueId);
    }

    public ArenaTeam getTeamByBedLocation(Arena arena, Location location) {
        for (TeamColor color : getActiveColors(arena)) {
            ArenaTeam team = arena.getTeam(color);
            if (team == null || team.getBedData() == null) {
                continue;
            }

            Location head = arena.getMatchLocation(team.getBedData().getHead());
            Location foot = arena.getMatchLocation(team.getBedData().getFoot());
            if (n.plugins.newbedwars.util.LocationUtil.sameBlock(head, location)
                || n.plugins.newbedwars.util.LocationUtil.sameBlock(foot, location)) {
                return team;
            }
        }
        return null;
    }

    public List<ArenaTeam> getAliveTeams(Arena arena) {
        updateEliminationState(arena);
        List<ArenaTeam> alive = new ArrayList<ArenaTeam>();
        for (TeamColor color : getActiveColors(arena)) {
            ArenaTeam team = arena.getTeam(color);
            if (team != null && !team.isEliminated()) {
                alive.add(team);
            }
        }
        return alive;
    }

    public void updateEliminationState(Arena arena) {
        for (Map.Entry<TeamColor, ArenaTeam> entry : arena.getTeams().entrySet()) {
            TeamColor color = entry.getKey();
            ArenaTeam team = entry.getValue();

            if (!isActiveColor(arena, color)) {
                team.setEliminated(true);
                continue;
            }

            boolean activeMember = false;
            for (Map.Entry<UUID, TeamColor> playerEntry : arena.getPlayerTeams().entrySet()) {
                if (playerEntry.getValue() != color) {
                    continue;
                }

                if (!arena.getPlayers().contains(playerEntry.getKey())) {
                    continue;
                }

                if (plugin.getGameManager().isRespawning(playerEntry.getKey())) {
                    activeMember = true;
                    break;
                }

                if (arena.getSpectators().contains(playerEntry.getKey())) {
                    continue;
                }

                activeMember = true;
                break;
            }

            team.setEliminated(!activeMember);
        }
    }

    public String getDisplay(Arena arena, UUID uniqueId) {
        TeamColor color = getColor(arena, uniqueId);
        return color == null ? "\u00A77Sem time" : color.getColoredName();
    }
}

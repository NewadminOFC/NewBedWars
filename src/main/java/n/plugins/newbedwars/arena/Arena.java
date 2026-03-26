package n.plugins.newbedwars.arena;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import n.plugins.newbedwars.util.BlockPosition;
import n.plugins.newbedwars.util.BlockSnapshot;
import n.plugins.newbedwars.util.CuboidRegion;
import n.plugins.newbedwars.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;

public class Arena {

    private final String name;
    private final String worldName;
    private final Map<TeamColor, ArenaTeam> teams;
    private final Map<GeneratorType, List<GeneratorPoint>> globalGenerators;
    private final Set<UUID> players;
    private final Set<UUID> spectators;
    private final Map<UUID, TeamColor> playerTeams;
    private final Map<BlockPosition, BlockSnapshot> blockSnapshots;
    private final Set<BlockPosition> placedBlocks;
    private String activeWorldName;
    private Location waitingSpawn;
    private CuboidRegion waitingRegion;
    private ArenaState state;
    private boolean ready;
    private int countdown;
    private int elapsedTime;
    private int endCountdown;

    public Arena(String name, String worldName) {
        this.name = name;
        this.worldName = worldName;
        this.teams = new EnumMap<TeamColor, ArenaTeam>(TeamColor.class);
        for (TeamColor color : TeamColor.values()) {
            this.teams.put(color, new ArenaTeam(color));
        }
        this.globalGenerators = new EnumMap<GeneratorType, List<GeneratorPoint>>(GeneratorType.class);
        this.globalGenerators.put(GeneratorType.DIAMOND, new ArrayList<GeneratorPoint>());
        this.globalGenerators.put(GeneratorType.EMERALD, new ArrayList<GeneratorPoint>());
        this.players = new HashSet<UUID>();
        this.spectators = new HashSet<UUID>();
        this.playerTeams = new HashMap<UUID, TeamColor>();
        this.blockSnapshots = new LinkedHashMap<BlockPosition, BlockSnapshot>();
        this.placedBlocks = new HashSet<BlockPosition>();
        this.state = ArenaState.WAITING;
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public World getWorld() {
        return LocationUtil.ensureWorldLoaded(worldName);
    }

    public String getActiveWorldName() {
        return activeWorldName;
    }

    public void setActiveWorldName(String activeWorldName) {
        this.activeWorldName = activeWorldName;
    }

    public void clearActiveWorld() {
        this.activeWorldName = null;
    }

    public boolean hasActiveWorld() {
        return activeWorldName != null && !activeWorldName.trim().isEmpty();
    }

    public World getActiveWorld() {
        return hasActiveWorld() ? LocationUtil.ensureWorldLoaded(activeWorldName) : null;
    }

    public World getMatchWorld() {
        World activeWorld = getActiveWorld();
        return activeWorld != null ? activeWorld : getWorld();
    }

    public Location getMatchLocation(Location location) {
        World matchWorld = getMatchWorld();
        return matchWorld == null ? (location == null ? null : location.clone()) : LocationUtil.relocate(location, matchWorld);
    }

    public CuboidRegion getMatchRegion(CuboidRegion region) {
        World matchWorld = getMatchWorld();
        return matchWorld == null ? region : LocationUtil.relocate(region, matchWorld);
    }

    public Location getWaitingSpawn() {
        return waitingSpawn == null ? null : waitingSpawn.clone();
    }

    public void setWaitingSpawn(Location waitingSpawn) {
        this.waitingSpawn = waitingSpawn == null ? null : waitingSpawn.clone();
        this.ready = false;
    }

    public CuboidRegion getWaitingRegion() {
        return waitingRegion;
    }

    public void setWaitingRegion(CuboidRegion waitingRegion) {
        this.waitingRegion = waitingRegion;
        this.ready = false;
    }

    public Map<TeamColor, ArenaTeam> getTeams() {
        return teams;
    }

    public ArenaTeam getTeam(TeamColor color) {
        return teams.get(color);
    }

    public List<GeneratorPoint> getGlobalGenerators(GeneratorType type) {
        List<GeneratorPoint> list = globalGenerators.get(type);
        return list == null ? new ArrayList<GeneratorPoint>() : list;
    }

    public void addGlobalGenerator(GeneratorType type, Location location) {
        List<GeneratorPoint> list = globalGenerators.get(type);
        if (list == null) {
            list = new ArrayList<GeneratorPoint>();
            globalGenerators.put(type, list);
        }
        list.add(new GeneratorPoint(type, location, null));
        this.ready = false;
    }

    public void clearGlobalGenerators(GeneratorType type) {
        List<GeneratorPoint> list = globalGenerators.get(type);
        if (list != null) {
            list.clear();
        }
        this.ready = false;
    }

    public ArenaState getState() {
        return state;
    }

    public void setState(ArenaState state) {
        this.state = state;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public Set<UUID> getPlayers() {
        return players;
    }

    public Set<UUID> getSpectators() {
        return spectators;
    }

    public Map<UUID, TeamColor> getPlayerTeams() {
        return playerTeams;
    }

    public int getPlayerCount() {
        return players.size();
    }

    public int getAlivePlayers() {
        return players.size() - spectators.size();
    }

    public boolean isFull(int maxPlayers) {
        return getPlayerCount() >= maxPlayers;
    }

    public int getCountdown() {
        return countdown;
    }

    public void setCountdown(int countdown) {
        this.countdown = countdown;
    }

    public int getElapsedTime() {
        return elapsedTime;
    }

    public void setElapsedTime(int elapsedTime) {
        this.elapsedTime = elapsedTime;
    }

    public int getEndCountdown() {
        return endCountdown;
    }

    public void setEndCountdown(int endCountdown) {
        this.endCountdown = endCountdown;
    }

    public void addPlayer(UUID uniqueId) {
        this.players.add(uniqueId);
        this.spectators.remove(uniqueId);
    }

    public void removePlayer(UUID uniqueId) {
        this.players.remove(uniqueId);
        this.spectators.remove(uniqueId);
        this.playerTeams.remove(uniqueId);
    }

    public Player getAnyAlivePlayer() {
        for (UUID uniqueId : players) {
            if (spectators.contains(uniqueId)) {
                continue;
            }

            Player player = Bukkit.getPlayer(uniqueId);
            if (player != null && player.isOnline()) {
                return player;
            }
        }
        return null;
    }

    public Location getSpectatorSpawn() {
        Player alive = getAnyAlivePlayer();
        if (alive != null) {
            return alive.getLocation();
        }
        if (getWaitingSpawn() != null) {
            return getMatchLocation(getWaitingSpawn());
        }

        World world = getMatchWorld();
        return world == null ? null : world.getSpawnLocation();
    }

    public void registerSnapshot(BlockState state) {
        BlockPosition position = BlockPosition.fromLocation(state.getLocation());
        if (!blockSnapshots.containsKey(position)) {
            blockSnapshots.put(position, new BlockSnapshot(state));
        }
    }

    public void restoreSnapshots() {
        Collection<BlockSnapshot> values = new ArrayList<BlockSnapshot>(blockSnapshots.values());
        for (BlockSnapshot snapshot : values) {
            snapshot.restore();
        }
        blockSnapshots.clear();
    }

    public void clearSnapshots() {
        blockSnapshots.clear();
    }

    public void addPlacedBlock(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        placedBlocks.add(BlockPosition.fromLocation(location));
    }

    public boolean isPlacedBlock(Location location) {
        return location != null && location.getWorld() != null && placedBlocks.contains(BlockPosition.fromLocation(location));
    }

    public void removePlacedBlock(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        placedBlocks.remove(BlockPosition.fromLocation(location));
    }

    public void clearPlacedBlocks() {
        placedBlocks.clear();
    }

    public void resetGameRuntime() {
        this.state = ArenaState.WAITING;
        this.countdown = 0;
        this.elapsedTime = 0;
        this.endCountdown = 0;
        this.players.clear();
        this.spectators.clear();
        this.playerTeams.clear();
        this.restoreSnapshots();
        this.clearPlacedBlocks();
        this.clearActiveWorld();
        for (ArenaTeam team : teams.values()) {
            team.resetRuntime();
        }
    }

    public List<String> validateSetup() {
        List<String> issues = new ArrayList<String>();

        if (getWorld() == null) {
            issues.add("Mundo da arena nao carregado");
        }
        if (waitingSpawn == null) {
            issues.add("Spawn de espera nao configurado");
        }
        if (waitingRegion == null || !waitingRegion.isComplete()) {
            issues.add("Area de espera nao configurada");
        }

        for (TeamColor color : TeamColor.getOneVsOneColors()) {
            ArenaTeam team = teams.get(color);
            if (!team.isSetupComplete()) {
                issues.add("Time " + color.getDisplayName() + " incompleto");
            } else if (!team.isConfirmed()) {
                issues.add("Time " + color.getDisplayName() + " nao confirmado");
            }
        }

        if (getGlobalGenerators(GeneratorType.DIAMOND).isEmpty()) {
            issues.add("Geradores de diamante nao configurados");
        }
        if (getGlobalGenerators(GeneratorType.EMERALD).isEmpty()) {
            issues.add("Geradores de esmeralda nao configurados");
        }

        return issues;
    }
}

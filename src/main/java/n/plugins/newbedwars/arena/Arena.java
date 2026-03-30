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
    private final String templateName;
    private final String worldName;
    private final boolean runtimeInstance;
    private final Map<TeamColor, ArenaTeam> teams;
    private final Map<GeneratorType, List<GeneratorPoint>> globalGenerators;
    private final Set<UUID> players;
    private final Set<UUID> spectators;
    private final Map<UUID, TeamColor> playerTeams;
    private final Map<UUID, Integer> playerArmorTiers;
    private final Map<UUID, Integer> playerPickaxeTiers;
    private final Map<UUID, Integer> playerAxeTiers;
    private final Map<UUID, Integer> playerKills;
    private final Map<UUID, Integer> playerFinalKills;
    private final Map<BlockPosition, BlockSnapshot> blockSnapshots;
    private final Set<BlockPosition> placedBlocks;
    private BedWarsMode mode;
    private String activeWorldName;
    private Location waitingSpawn;
    private CuboidRegion waitingRegion;
    private Double antiVoidY;
    private ArenaState state;
    private boolean ready;
    private boolean matchStarted;
    private int countdown;
    private int elapsedTime;
    private int endCountdown;

    public Arena(String name, String worldName) {
        this(name, worldName, name, false);
    }

    public Arena(String name, String worldName, String templateName, boolean runtimeInstance) {
        this.name = name;
        this.templateName = templateName == null || templateName.trim().isEmpty() ? name : templateName;
        this.worldName = worldName;
        this.runtimeInstance = runtimeInstance;
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
        this.playerArmorTiers = new HashMap<UUID, Integer>();
        this.playerPickaxeTiers = new HashMap<UUID, Integer>();
        this.playerAxeTiers = new HashMap<UUID, Integer>();
        this.playerKills = new HashMap<UUID, Integer>();
        this.playerFinalKills = new HashMap<UUID, Integer>();
        this.blockSnapshots = new LinkedHashMap<BlockPosition, BlockSnapshot>();
        this.placedBlocks = new HashSet<BlockPosition>();
        this.mode = BedWarsMode.ONE_VS_ONE;
        this.state = ArenaState.WAITING;
        this.matchStarted = false;
    }

    public String getName() {
        return name;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getDisplayName() {
        return templateName;
    }

    public String getWorldName() {
        return worldName;
    }

    public boolean isRuntimeInstance() {
        return runtimeInstance;
    }

    public BedWarsMode getMode() {
        return mode == null ? BedWarsMode.ONE_VS_ONE : mode;
    }

    public void setMode(BedWarsMode mode) {
        this.mode = mode == null ? BedWarsMode.ONE_VS_ONE : mode;
        this.ready = false;
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

    public Double getAntiVoidY() {
        return antiVoidY;
    }

    public void setAntiVoidY(Double antiVoidY) {
        this.antiVoidY = antiVoidY;
    }

    public boolean hasAntiVoidY() {
        return antiVoidY != null;
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

    public boolean hasStartedMatch() {
        return matchStarted;
    }

    public void markMatchStarted() {
        this.matchStarted = true;
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
        this.playerArmorTiers.remove(uniqueId);
        this.playerPickaxeTiers.remove(uniqueId);
        this.playerAxeTiers.remove(uniqueId);
    }

    public int getArmorTier(UUID uniqueId) {
        Integer tier = playerArmorTiers.get(uniqueId);
        return tier == null ? 0 : tier.intValue();
    }

    public void setArmorTier(UUID uniqueId, int tier) {
        if (uniqueId == null) {
            return;
        }

        playerArmorTiers.put(uniqueId, Integer.valueOf(Math.max(0, tier)));
    }

    public void clearArmorTiers() {
        playerArmorTiers.clear();
    }

    public int getPickaxeTier(UUID uniqueId) {
        Integer tier = playerPickaxeTiers.get(uniqueId);
        return tier == null ? 0 : tier.intValue();
    }

    public void setPickaxeTier(UUID uniqueId, int tier) {
        if (uniqueId == null) {
            return;
        }

        playerPickaxeTiers.put(uniqueId, Integer.valueOf(Math.max(0, tier)));
    }

    public void clearPickaxeTiers() {
        playerPickaxeTiers.clear();
    }

    public int getAxeTier(UUID uniqueId) {
        Integer tier = playerAxeTiers.get(uniqueId);
        return tier == null ? 0 : tier.intValue();
    }

    public void setAxeTier(UUID uniqueId, int tier) {
        if (uniqueId == null) {
            return;
        }

        playerAxeTiers.put(uniqueId, Integer.valueOf(Math.max(0, tier)));
    }

    public void clearAxeTiers() {
        playerAxeTiers.clear();
    }

    public Map<UUID, Integer> getPlayerKills() {
        return playerKills;
    }

    public Map<UUID, Integer> getPlayerFinalKills() {
        return playerFinalKills;
    }

    public int getKillCount(UUID uniqueId) {
        Integer kills = playerKills.get(uniqueId);
        return kills == null ? 0 : kills.intValue();
    }

    public int getFinalKillCount(UUID uniqueId) {
        Integer kills = playerFinalKills.get(uniqueId);
        return kills == null ? 0 : kills.intValue();
    }

    public void addKill(UUID uniqueId) {
        if (uniqueId == null) {
            return;
        }

        playerKills.put(uniqueId, Integer.valueOf(getKillCount(uniqueId) + 1));
    }

    public void addFinalKill(UUID uniqueId) {
        if (uniqueId == null) {
            return;
        }

        playerFinalKills.put(uniqueId, Integer.valueOf(getFinalKillCount(uniqueId) + 1));
    }

    public void clearKillStats() {
        playerKills.clear();
        playerFinalKills.clear();
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
        if (getWaitingSpawn() != null) {
            return getMatchLocation(getWaitingSpawn());
        }

        Player alive = getAnyAlivePlayer();
        if (alive != null) {
            return alive.getLocation();
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
        this.playerArmorTiers.clear();
        this.playerPickaxeTiers.clear();
        this.playerAxeTiers.clear();
        this.clearKillStats();
        this.restoreSnapshots();
        this.clearPlacedBlocks();
        this.clearActiveWorld();
        if (!runtimeInstance) {
            this.matchStarted = false;
        }
        for (ArenaTeam team : teams.values()) {
            team.resetRuntime();
        }
    }

    public Arena createRuntimeCopy(String runtimeName) {
        Arena copy = new Arena(runtimeName, worldName, templateName, true);
        copy.mode = getMode();
        copy.waitingSpawn = getWaitingSpawn();
        copy.waitingRegion = waitingRegion == null ? null : new CuboidRegion(waitingRegion.getPos1(), waitingRegion.getPos2());
        copy.antiVoidY = antiVoidY;
        copy.ready = ready;
        copy.matchStarted = false;

        for (TeamColor color : TeamColor.values()) {
            ArenaTeam source = teams.get(color);
            ArenaTeam target = copy.getTeam(color);
            if (source != null && target != null) {
                target.copySetupFrom(source);
            }
        }

        for (GeneratorType type : new GeneratorType[] {GeneratorType.DIAMOND, GeneratorType.EMERALD}) {
            copy.clearGlobalGenerators(type);
            for (GeneratorPoint point : getGlobalGenerators(type)) {
                copy.getGlobalGenerators(type).add(new GeneratorPoint(type, point.getLocation(), point.getOwner()));
            }
        }

        copy.setReady(isReady());
        copy.setState(ArenaState.WAITING);
        return copy;
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

        for (TeamColor color : getMode().getActiveColors()) {
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

package n.plugins.newbedwars.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaState;
import n.plugins.newbedwars.arena.ArenaTeam;
import n.plugins.newbedwars.arena.TeamColor;
import n.plugins.newbedwars.model.PlayerSnapshot;
import n.plugins.newbedwars.util.ItemBuilder;
import n.plugins.newbedwars.util.ChatUtil;
import n.plugins.newbedwars.util.CuboidRegion;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public class GameManager {

    private final NewBedWars plugin;
    private final Map<UUID, PlayerSnapshot> snapshots;
    private final Map<UUID, PendingRespawn> pendingRespawns;
    private final Map<UUID, Integer> respawnTasks;
    private int taskId = -1;

    private static class PendingRespawn {

        private final Location spectatorLocation;
        private final Location respawnLocation;
        private final boolean finalDeath;

        private PendingRespawn(Location spectatorLocation, Location respawnLocation, boolean finalDeath) {
            this.spectatorLocation = spectatorLocation == null ? null : spectatorLocation.clone();
            this.respawnLocation = respawnLocation == null ? null : respawnLocation.clone();
            this.finalDeath = finalDeath;
        }

        public Location getSpectatorLocation() {
            return spectatorLocation == null ? null : spectatorLocation.clone();
        }

        public Location getRespawnLocation() {
            return respawnLocation == null ? null : respawnLocation.clone();
        }

        public boolean isFinalDeath() {
            return finalDeath;
        }
    }

    public GameManager(NewBedWars plugin) {
        this.plugin = plugin;
        this.snapshots = new HashMap<UUID, PlayerSnapshot>();
        this.pendingRespawns = new HashMap<UUID, PendingRespawn>();
        this.respawnTasks = new HashMap<UUID, Integer>();
    }

    public void start() {
        if (taskId != -1) {
            return;
        }

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                tickArenas();
            }
        }, 20L, 20L);
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        for (Arena arena : new ArrayList<Arena>(plugin.getArenaManager().getRuntimeArenas())) {
            resetArena(arena);
        }
    }

    public int getArenaCapacity() {
        return plugin.getTeamManager().getArenaCapacity();
    }

    public boolean isRespawning(UUID uniqueId) {
        return uniqueId != null && pendingRespawns.containsKey(uniqueId);
    }

    public void giveWaitingLobbyItems(Player player) {
        if (player == null) {
            return;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (!isPreGameArena(arena)) {
            return;
        }

        int leaveSlot = resolveHotbarSlot("pre-game-items.leave-arena.slot", 8);
        player.getInventory().setItem(leaveSlot, createLeaveArenaItem());

        int selectorSlot = resolveHotbarSlot("pre-game-items.team-selector.slot", 0);
        player.getInventory().setItem(selectorSlot, createTeamSelectorItem(arena, player.getUniqueId()));
        player.updateInventory();
    }

    public boolean handleWaitingLobbyItemUse(Player player, ItemStack item) {
        if (player == null || item == null) {
            return false;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (!isPreGameArena(arena) || arena.getSpectators().contains(player.getUniqueId())) {
            return false;
        }

        if (item.getType() == Material.BED) {
            player.closeInventory();
            leaveArena(player, true);
            return true;
        }

        if (item.getType() == Material.WOOL || item.getType() == Material.SKULL_ITEM) {
            if (!hasTeamSelectorAccess(player)) {
                sendTeamSelectorNoPermission(player);
                return true;
            }

            plugin.getMenuManager().openTeamSelectorMenu(player, arena);
            return true;
        }

        return false;
    }

    public void joinArena(Player player, Arena arena) {
        if (arena == null) {
            plugin.getMessageManager().send(player, "game.no-arena-available");
            return;
        }

        if (plugin.getArenaManager().getArenaByPlayer(player.getUniqueId()) != null) {
            plugin.getMessageManager().send(player, "general.already-in-arena");
            return;
        }

        if (!arena.isRuntimeInstance()) {
            if (!arena.isReady()) {
                plugin.getMessageManager().send(player, "game.arena-not-ready");
                return;
            }

            arena = plugin.getArenaManager().resolveJoinableArena(arena, getArenaCapacity());
            if (arena == null) {
                plugin.getMessageManager().send(player, "game.no-arena-available");
                return;
            }
        }

        joinRuntimeArena(player, arena);
    }

    public void quickJoin(Player player) {
        Arena runtimeArena = findBestRuntimeArena();
        if (runtimeArena != null) {
            joinRuntimeArena(player, runtimeArena);
            return;
        }

        Arena template = findBestTemplateArena();
        if (template == null) {
            plugin.getMessageManager().send(player, "game.no-arena-available");
            return;
        }

        joinArena(player, template);
    }

    public List<Arena> getJoinableArenas() {
        List<Arena> arenas = new ArrayList<Arena>();
        for (Arena arena : plugin.getArenaManager().getConfiguredArenas()) {
            if (arena.isReady()) {
                arenas.add(arena);
            }
        }

        Collections.sort(arenas, new Comparator<Arena>() {
            @Override
            public int compare(Arena first, Arena second) {
                int firstPlayers = plugin.getArenaManager().countPlayersForTemplate(first.getTemplateName());
                int secondPlayers = plugin.getArenaManager().countPlayersForTemplate(second.getTemplateName());
                if (firstPlayers != secondPlayers) {
                    return Integer.compare(secondPlayers, firstPlayers);
                }
                return first.getDisplayName().compareToIgnoreCase(second.getDisplayName());
            }
        });
        return arenas;
    }

    public void leaveArena(Player player, boolean restorePlayer) {
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (arena == null) {
            plugin.getMessageManager().send(player, "general.not-in-arena");
            return;
        }

        boolean wasIngame = arena.getState() == ArenaState.INGAME;
        clearPendingRespawn(player.getUniqueId());
        arena.removePlayer(player.getUniqueId());
        plugin.getTeamManager().unassign(arena, player.getUniqueId());
        plugin.getArenaManager().clearPlayerArena(player.getUniqueId());
        plugin.getScoreboardManager().clear(player);

        if (restorePlayer) {
            restorePlayer(player, plugin.getLobbyManager().getMainWorldSpawn());
        }

        plugin.getMessageManager().send(player, "game.left");
        broadcast(arena, "game.leave-broadcast", placeholders(
            "player", player.getName(),
            "players", String.valueOf(arena.getPlayerCount()),
            "max_players", String.valueOf(getArenaCapacity())
        ));

        if (wasIngame) {
            checkWin(arena);
        } else if (arena.getPlayerCount() < getRequiredPlayersToStart()) {
            arena.setState(ArenaState.WAITING);
            arena.setCountdown(0);
        }

        if (!wasIngame && arena.getPlayerCount() == 0) {
            cleanupEmptyArena(arena);
        }
    }

    public void handleDeath(Player player, PlayerDeathEvent event) {
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (arena == null || arena.getState() != ArenaState.INGAME) {
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setDeathMessage(null);

        ArenaTeam team = plugin.getTeamManager().getTeam(arena, player.getUniqueId());
        if (team == null) {
            return;
        }

        Location deathLocation = player.getLocation().clone();
        if (team.isBedDestroyed()) {
            arena.getSpectators().add(player.getUniqueId());
            plugin.getTeamManager().updateEliminationState(arena);
            plugin.getMessageManager().send(player, "game.death-final");
            pendingRespawns.put(player.getUniqueId(), new PendingRespawn(arena.getSpectatorSpawn(), arena.getSpectatorSpawn(), true));
        } else {
            plugin.getMessageManager().send(player, "game.death-respawn");
            pendingRespawns.put(player.getUniqueId(), new PendingRespawn(deathLocation, resolveTeamRespawn(arena, team), false));
        }

        forceInstantRespawn(player);
    }

    public void handleRespawn(final Player player, PlayerRespawnEvent event) {
        final Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (arena == null || arena.getState() != ArenaState.INGAME) {
            return;
        }

        final PendingRespawn pendingRespawn = pendingRespawns.get(player.getUniqueId());
        if (pendingRespawn != null) {
            Location spectatorLocation = pendingRespawn.getSpectatorLocation();
            if (spectatorLocation != null) {
                event.setRespawnLocation(spectatorLocation);
            }

            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        return;
                    }

                    if (pendingRespawn.isFinalDeath()) {
                        makeSpectator(player, arena);
                        if (pendingRespawn.getSpectatorLocation() != null) {
                            teleportSafely(player, pendingRespawn.getSpectatorLocation());
                        }
                        clearPendingRespawn(player.getUniqueId());
                        checkWin(arena);
                        return;
                    }

                    prepareRespawningPlayer(player);
                    if (pendingRespawn.getSpectatorLocation() != null) {
                        teleportSafely(player, pendingRespawn.getSpectatorLocation());
                    }
                    startRespawnCountdown(player, arena, pendingRespawn.getRespawnLocation());
                }
            });
            return;
        }

        final ArenaTeam team = plugin.getTeamManager().getTeam(arena, player.getUniqueId());
        if (team != null && !team.isBedDestroyed()) {
            final Location respawnLocation = resolveTeamRespawn(arena, team);
            if (respawnLocation != null) {
                event.setRespawnLocation(respawnLocation);
            }
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    preparePlayer(player);
                    plugin.getShopManager().applyRespawnLoadout(player, team);
                    if (respawnLocation != null) {
                        teleportSafely(player, respawnLocation);
                    }
                }
            });
            return;
        }

        final Location spectatorLocation = arena.getSpectatorSpawn();
        if (spectatorLocation != null) {
            event.setRespawnLocation(spectatorLocation);
        }
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                makeSpectator(player, arena);
                if (spectatorLocation != null) {
                    teleportSafely(player, spectatorLocation);
                }
                checkWin(arena);
            }
        });
    }

    public void markBedDestroyed(Arena arena, ArenaTeam team, Player breaker) {
        if (team == null || team.isBedDestroyed()) {
            return;
        }

        team.setBedDestroyed(true);
        broadcast(arena, "game.bed-destroyed", placeholders(
            "team", team.getColor().getColoredName(),
            "player", breaker.getName()
        ));

        for (UUID uniqueId : arena.getPlayers()) {
            Player member = Bukkit.getPlayer(uniqueId);
            if (member == null || !member.isOnline()) {
                continue;
            }

            TeamColor color = plugin.getTeamManager().getColor(arena, uniqueId);
            if (color == team.getColor()) {
                plugin.getMessageManager().send(member, "game.your-bed-destroyed");
            }
        }
    }

    public void endGame(Arena arena, TeamColor winner) {
        if (arena.getState() == ArenaState.ENDING || arena.getState() == ArenaState.RESETTING) {
            return;
        }

        arena.setState(ArenaState.ENDING);
        arena.setEndCountdown(plugin.getConfig().getInt("settings.ending-seconds", 8));

        if (winner != null) {
            broadcast(arena, "game.winner", placeholders("team", winner.getColoredName(), "arena", arena.getDisplayName()));
        } else {
            broadcast(arena, "game.draw", Collections.<String, String>emptyMap());
        }
    }

    public void resetArena(Arena arena) {
        arena.setState(ArenaState.RESETTING);
        if (arena.hasActiveWorld()) {
            arena.clearSnapshots();
        } else {
            arena.restoreSnapshots();
        }

        for (UUID uniqueId : new ArrayList<UUID>(arena.getPlayers())) {
            clearPendingRespawn(uniqueId);
            Player player = Bukkit.getPlayer(uniqueId);
            if (player != null && player.isOnline()) {
                restorePlayer(player, plugin.getLobbyManager().getMainWorldSpawn());
                plugin.getScoreboardManager().clear(player);
            } else {
                snapshots.remove(uniqueId);
            }
            plugin.getArenaManager().clearPlayerArena(uniqueId);
        }

        arena.getPlayers().clear();
        arena.getSpectators().clear();
        arena.getPlayerTeams().clear();
        arena.clearArmorTiers();
        arena.setCountdown(0);
        arena.setElapsedTime(0);
        arena.setEndCountdown(0);
        arena.setState(ArenaState.WAITING);
        arena.clearPlacedBlocks();
        plugin.getNpcManager().clearArenaShopNpcs(arena);
        plugin.getHologramManager().clearArenaChestHolograms(arena);
        plugin.getWorldCloneManager().destroyClone(arena);

        for (ArenaTeam team : arena.getTeams().values()) {
            team.resetRuntime();
        }

        if (arena.isRuntimeInstance()) {
            plugin.getArenaManager().removeRuntimeArena(arena);
        } else {
            plugin.getNpcManager().refreshArenaShopNpcs(arena);
        }
    }

    private void tickArenas() {
        for (Arena arena : new ArrayList<Arena>(plugin.getArenaManager().getRuntimeArenas())) {
            if (arena.getState() == ArenaState.WAITING) {
                if (arena.getPlayerCount() >= getRequiredPlayersToStart()) {
                    arena.setState(ArenaState.STARTING);
                    arena.setCountdown(getStartingCountdown());
                }
            } else if (arena.getState() == ArenaState.STARTING) {
                handleStartingTick(arena);
            } else if (arena.getState() == ArenaState.INGAME) {
                arena.setElapsedTime(arena.getElapsedTime() + 1);
                plugin.getShopManager().tickArenaEffects(arena);
                checkWin(arena);
            } else if (arena.getState() == ArenaState.ENDING) {
                arena.setEndCountdown(arena.getEndCountdown() - 1);
                if (arena.getEndCountdown() <= 0) {
                    resetArena(arena);
                }
            }
        }
    }

    private void handleStartingTick(Arena arena) {
        if (arena.getPlayerCount() < getRequiredPlayersToStart()) {
            arena.setState(ArenaState.WAITING);
            arena.setCountdown(0);
            broadcast(arena, "game.countdown-cancelled", Collections.<String, String>emptyMap());
            if (arena.getPlayerCount() == 0) {
                cleanupEmptyArena(arena);
            }
            return;
        }

        int seconds = arena.getCountdown();
        if (seconds == 10 || seconds == 5 || seconds <= 3) {
            broadcast(arena, "game.countdown", Collections.singletonMap("seconds", String.valueOf(seconds)));
        }

        if (seconds <= 0) {
            startGame(arena);
            return;
        }

        arena.setCountdown(seconds - 1);
    }

    private void joinRuntimeArena(Player player, Arena arena) {
        if (!arena.isReady()) {
            plugin.getMessageManager().send(player, "game.arena-not-ready");
            return;
        }

        if (arena.getState() != ArenaState.WAITING && arena.getState() != ArenaState.STARTING) {
            plugin.getMessageManager().send(player, "game.arena-running");
            return;
        }

        int maxPlayers = getArenaCapacity();
        if (arena.isFull(maxPlayers)) {
            plugin.getMessageManager().send(player, "game.arena-full");
            return;
        }

        if (!plugin.getWorldCloneManager().ensureClone(arena)) {
            player.sendMessage(plugin.getMessageManager().get("prefix") + ChatUtil.color("&cNao foi possivel preparar o clone do mapa desta arena."));
            return;
        }

        snapshots.put(player.getUniqueId(), PlayerSnapshot.capture(player));
        preparePlayer(player);

        arena.addPlayer(player.getUniqueId());
        plugin.getArenaManager().setPlayerArena(player.getUniqueId(), arena);

        Location joinLocation = resolveLobbySpawn(arena);
        if (joinLocation != null) {
            teleportSafely(player, joinLocation);
        }

        sendJoinWaitingMessage(player, arena);
        giveWaitingLobbyItems(player);
        broadcast(arena, "game.join-broadcast", placeholders(
            "player", player.getName(),
            "players", String.valueOf(arena.getPlayerCount()),
            "max_players", String.valueOf(maxPlayers)
        ));

        if (arena.getState() == ArenaState.WAITING && arena.getPlayerCount() >= getRequiredPlayersToStart()) {
            arena.setState(ArenaState.STARTING);
            arena.setCountdown(getStartingCountdown());
        }
    }

    private Arena findBestRuntimeArena() {
        List<Arena> arenas = new ArrayList<Arena>();
        int maxPlayers = getArenaCapacity();

        for (Arena arena : plugin.getArenaManager().getRuntimeArenas()) {
            if (arena.getState() != ArenaState.WAITING && arena.getState() != ArenaState.STARTING) {
                continue;
            }
            if (arena.isFull(maxPlayers)) {
                continue;
            }
            arenas.add(arena);
        }

        Collections.sort(arenas, new Comparator<Arena>() {
            @Override
            public int compare(Arena first, Arena second) {
                return Integer.compare(second.getPlayerCount(), first.getPlayerCount());
            }
        });
        return arenas.isEmpty() ? null : arenas.get(0);
    }

    private Arena findBestTemplateArena() {
        List<Arena> arenas = getJoinableArenas();
        return arenas.isEmpty() ? null : arenas.get(0);
    }

    private void startGame(Arena arena) {
        arena.setState(ArenaState.INGAME);
        arena.setElapsedTime(0);
        arena.clearSnapshots();
        assignRandomTeams(arena);

        for (ArenaTeam team : arena.getTeams().values()) {
            team.resetRuntime();
        }

        CuboidRegion waitingRegion = arena.getMatchRegion(arena.getWaitingRegion());
        if (waitingRegion != null) {
            for (org.bukkit.block.Block block : waitingRegion.getBlocks()) {
                arena.registerSnapshot(block.getState());
                if (block.getType() != Material.AIR) {
                    block.setType(Material.AIR);
                }
            }
        }

        plugin.getSetupManager().clearArenaSetupVisuals(arena);
        plugin.getNpcManager().refreshArenaShopNpcs(arena);
        plugin.getHologramManager().clearArenaChestHolograms(arena);

        for (UUID uniqueId : new ArrayList<UUID>(arena.getPlayers())) {
            Player player = Bukkit.getPlayer(uniqueId);
            if (player == null || !player.isOnline()) {
                continue;
            }

            preparePlayer(player);
            ArenaTeam team = plugin.getTeamManager().getTeam(arena, uniqueId);
            if (team != null) {
                plugin.getShopManager().applyRespawnLoadout(player, team);
                plugin.getMessageManager().send(player, "game.team-assigned", Collections.singletonMap("team", team.getColor().getColoredName()));
            }
            Location startLocation = team == null ? resolveLobbySpawn(arena) : resolveTeamRespawn(arena, team);
            if (startLocation != null) {
                teleportSafely(player, startLocation);
            }
        }

        broadcast(arena, "game.game-started", Collections.<String, String>emptyMap());
    }

    private void checkWin(Arena arena) {
        if (arena.getState() != ArenaState.INGAME) {
            return;
        }

        List<ArenaTeam> aliveTeams = plugin.getTeamManager().getAliveTeams(arena);
        if (aliveTeams.size() == 1) {
            endGame(arena, aliveTeams.get(0).getColor());
        } else if (aliveTeams.isEmpty()) {
            endGame(arena, null);
        }
    }

    private void cleanupEmptyArena(Arena arena) {
        arena.clearSnapshots();
        arena.clearPlacedBlocks();
        plugin.getNpcManager().clearArenaShopNpcs(arena);
        plugin.getHologramManager().clearArenaChestHolograms(arena);
        plugin.getWorldCloneManager().destroyClone(arena);
        arena.resetGameRuntime();

        if (arena.isRuntimeInstance()) {
            plugin.getArenaManager().removeRuntimeArena(arena);
        } else {
            plugin.getNpcManager().refreshArenaShopNpcs(arena);
        }
    }

    private void preparePlayer(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(10.0F);
        player.setExp(0.0F);
        player.setLevel(0);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setCanPickupItems(true);
        player.setGameMode(GameMode.SURVIVAL);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.updateInventory();
    }

    private void restorePlayer(Player player) {
        restorePlayer(player, null);
    }

    private void restorePlayer(Player player, Location overrideLocation) {
        PlayerSnapshot snapshot = snapshots.remove(player.getUniqueId());
        if (snapshot != null) {
            snapshot.restore(player, overrideLocation);
        }
    }

    private void makeSpectator(Player player, Arena arena) {
        arena.getSpectators().add(player.getUniqueId());
        try {
            player.setGameMode(GameMode.SPECTATOR);
        } catch (IllegalArgumentException exception) {
            player.setGameMode(GameMode.CREATIVE);
            player.setAllowFlight(plugin.getConfig().getBoolean("settings.spectators-can-fly", true));
            player.setFlying(plugin.getConfig().getBoolean("settings.spectators-can-fly", true));
        }
        player.setCanPickupItems(false);
        plugin.getMessageManager().send(player, "game.spectator");
    }

    private void prepareRespawningPlayer(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(10.0F);
        player.setExp(0.0F);
        player.setLevel(5);
        player.setCanPickupItems(false);

        try {
            player.setGameMode(GameMode.SPECTATOR);
        } catch (IllegalArgumentException exception) {
            player.setGameMode(GameMode.CREATIVE);
            player.setAllowFlight(true);
            player.setFlying(true);
        }
    }

    private void startRespawnCountdown(final Player player, final Arena arena, final Location respawnLocation) {
        clearRespawnTask(player.getUniqueId());

        final int[] seconds = new int[] {5};
        int task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    clearPendingRespawn(player.getUniqueId());
                    return;
                }

                if (plugin.getArenaManager().getArenaByPlayer(player.getUniqueId()) != arena) {
                    clearPendingRespawn(player.getUniqueId());
                    return;
                }

                if (seconds[0] <= 0) {
                    ArenaTeam team = plugin.getTeamManager().getTeam(arena, player.getUniqueId());
                    preparePlayer(player);
                    if (team != null) {
                        plugin.getShopManager().applyRespawnLoadout(player, team);
                    }
                    if (respawnLocation != null) {
                        teleportSafely(player, respawnLocation);
                    }
                    clearPendingRespawn(player.getUniqueId());
                    return;
                }

                player.setLevel(seconds[0]);
                player.sendMessage(plugin.getMessageManager().get("prefix") + ChatUtil.color("&aRespawn em &f" + seconds[0] + "s&a."));
                seconds[0]--;
            }
        }, 0L, 20L);

        respawnTasks.put(player.getUniqueId(), Integer.valueOf(task));
    }

    private void clearPendingRespawn(UUID uniqueId) {
        pendingRespawns.remove(uniqueId);
        clearRespawnTask(uniqueId);
    }

    private void clearRespawnTask(UUID uniqueId) {
        Integer task = respawnTasks.remove(uniqueId);
        if (task != null) {
            Bukkit.getScheduler().cancelTask(task.intValue());
        }
    }

    private void broadcast(Arena arena, String path, Map<String, String> placeholders) {
        for (UUID uniqueId : arena.getPlayers()) {
            Player player = Bukkit.getPlayer(uniqueId);
            if (player == null || !player.isOnline()) {
                continue;
            }

            plugin.getMessageManager().send(player, path, placeholders);
            if (plugin.getConfig().getBoolean("settings.sounds", true)) {
                player.playSound(player.getLocation(), Sound.NOTE_PLING, 1.0F, 1.2F);
            }
        }
    }

    private int getRequiredPlayersToStart() {
        return getArenaCapacity();
    }

    private boolean isPreGameArena(Arena arena) {
        return arena != null && (arena.getState() == ArenaState.WAITING || arena.getState() == ArenaState.STARTING);
    }

    private int resolveHotbarSlot(String path, int fallback) {
        int configured = plugin.getConfig().getInt(path, fallback);
        if (configured < 0 || configured > 8) {
            return fallback;
        }
        return configured;
    }

    private ItemStack createLeaveArenaItem() {
        return new ItemBuilder(Material.BED)
            .name(plugin.getConfig().getString("pre-game-items.leave-arena.name", "&cVoltar ao Lobby"))
            .lore(plugin.getConfig().getStringList("pre-game-items.leave-arena.lore"))
            .build();
    }

    private boolean hasTeamSelectorAccess(Player player) {
        if (player == null) {
            return false;
        }

        String permission = plugin.getConfig().getString("pre-game-items.team-selector.permission", "newbedwars.teamselect");
        return permission == null || permission.trim().isEmpty() || player.hasPermission(permission) || player.isOp();
    }

    private ItemStack createTeamSelectorItem(Arena arena, UUID uniqueId) {
        TeamColor color = plugin.getTeamManager().getColor(arena, uniqueId);
        List<String> lore = new ArrayList<String>();
        for (String line : plugin.getConfig().getStringList("pre-game-items.team-selector.lore")) {
            lore.add(line.replace("%team%", color == null ? "&7Aleatorio" : color.getColoredName()));
        }

        return new ItemBuilder(Material.SKULL_ITEM, 1, (short) 3)
            .skullOwner(resolveTeamSelectorHead(color))
            .name(plugin.getConfig().getString("pre-game-items.team-selector.name", "&bEscolher Time"))
            .lore(lore)
            .build();
    }

    private void sendTeamSelectorNoPermission(Player player) {
        if (player == null) {
            return;
        }

        String prefix = plugin.getMessageManager().getConfiguration().getString("prefix", "");
        String raw = plugin.getConfig().getString("pre-game-items.team-selector.no-permission-message",
            "%prefix%&cVoce nao tem permissao para escolher o time.");
        player.sendMessage(ChatUtil.color(raw.replace("%prefix%", ChatUtil.color(prefix))));
    }

    private void sendJoinWaitingMessage(Player player, Arena arena) {
        if (player == null || arena == null) {
            return;
        }

        String prefix = plugin.getMessageManager().getConfiguration().getString("prefix", "");
        String raw = plugin.getMessageManager().getConfiguration().getString("game.joined-waiting",
            "%prefix%&aVoce entrou na arena &f%arena%&a.");
        raw = raw.replace("%prefix%", ChatUtil.color(prefix));
        raw = raw.replace("%arena%", arena.getDisplayName());
        player.sendMessage(ChatUtil.color(raw));
    }

    private String resolveTeamSelectorHead(TeamColor color) {
        if (color == TeamColor.RED) {
            return plugin.getConfig().getString("pre-game-items.team-selector.head-owner-red", "MHF_Red");
        }
        if (color == TeamColor.BLUE) {
            return plugin.getConfig().getString("pre-game-items.team-selector.head-owner-blue", "MHF_Blue");
        }
        return plugin.getConfig().getString("pre-game-items.team-selector.head-owner-default", "MHF_Question");
    }

    private void assignRandomTeams(Arena arena) {
        if (arena == null) {
            return;
        }

        Map<UUID, TeamColor> preferences = new HashMap<UUID, TeamColor>(arena.getPlayerTeams());
        arena.getPlayerTeams().clear();

        List<UUID> players = new ArrayList<UUID>(arena.getPlayers());
        Collections.shuffle(players);
        List<TeamColor> colors = new ArrayList<TeamColor>(plugin.getTeamManager().getActiveColors());
        Collections.shuffle(colors);

        List<UUID> remainingPlayers = new ArrayList<UUID>();
        for (UUID uniqueId : players) {
            TeamColor preferred = preferences.get(uniqueId);
            if (preferred != null && colors.contains(preferred)) {
                arena.getPlayerTeams().put(uniqueId, preferred);
                colors.remove(preferred);
            } else {
                remainingPlayers.add(uniqueId);
            }
        }

        int limit = Math.min(remainingPlayers.size(), colors.size());
        for (int i = 0; i < limit; i++) {
            arena.getPlayerTeams().put(remainingPlayers.get(i), colors.get(i));
        }
    }

    private int getStartingCountdown() {
        int configured = plugin.getConfig().getInt("settings.countdown-seconds", 10);
        return Math.max(3, Math.min(configured, 10));
    }

    private Location resolveLobbySpawn(Arena arena) {
        if (arena.getWaitingSpawn() != null) {
            return arena.getMatchLocation(arena.getWaitingSpawn());
        }

        if (arena.getMatchWorld() != null) {
            return arena.getMatchWorld().getSpawnLocation();
        }

        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    private Location resolveTeamRespawn(Arena arena, ArenaTeam team) {
        if (team != null && team.getSpawnLocation() != null) {
            return arena.getMatchLocation(team.getSpawnLocation());
        }

        return resolveLobbySpawn(arena);
    }

    private Map<String, String> placeholders(String... values) {
        Map<String, String> map = new HashMap<String, String>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(values[i], values[i + 1]);
        }
        return map;
    }

    private void teleportSafely(final Player player, final Location target) {
        if (player == null || target == null || target.getWorld() == null) {
            return;
        }

        target.getChunk().load();
        player.teleport(target);
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }

                target.getChunk().load();
                player.teleport(target);
            }
        }, 2L);
    }

    private void forceInstantRespawn(final Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (player == null || !player.isOnline()) {
                    return;
                }

                try {
                    Object spigot = player.getClass().getMethod("spigot").invoke(player);
                    spigot.getClass().getMethod("respawn").invoke(spigot);
                    return;
                } catch (Exception ignored) {
                }

                try {
                    String packageName = Bukkit.getServer().getClass().getPackage().getName();
                    String version = packageName.substring(packageName.lastIndexOf('.') + 1);
                    Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
                    Object handle = craftPlayerClass.getMethod("getHandle").invoke(player);
                    Object connection = handle.getClass().getField("playerConnection").get(handle);
                    Class<?> packetClass = Class.forName("net.minecraft.server." + version + ".PacketPlayInClientCommand");
                    Class<?> enumClass = Class.forName("net.minecraft.server." + version + ".PacketPlayInClientCommand$EnumClientCommand");
                    @SuppressWarnings("unchecked")
                    Object performRespawn = Enum.valueOf((Class<Enum>) enumClass.asSubclass(Enum.class), "PERFORM_RESPAWN");
                    Object packet = packetClass.getConstructor(enumClass).newInstance(performRespawn);
                    connection.getClass().getMethod("a", packetClass).invoke(connection, packet);
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
        }, 1L);
    }
}

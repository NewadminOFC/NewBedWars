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
import n.plugins.newbedwars.arena.BedWarsMode;
import n.plugins.newbedwars.arena.TeamColor;
import n.plugins.newbedwars.model.PlayerSnapshot;
import n.plugins.newbedwars.util.ItemBuilder;
import n.plugins.newbedwars.util.ChatUtil;
import n.plugins.newbedwars.util.CuboidRegion;
import n.plugins.newbedwars.util.SoundUtil;
import n.plugins.newbedwars.util.TitleUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
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

    public int getArenaCapacity(Arena arena) {
        return plugin.getTeamManager().getArenaCapacity(arena);
    }

    public int getRequiredPlayersToStart() {
        return getRequiredPlayersToStart(null);
    }

    public int getRequiredPlayersToStart(Arena arena) {
        BedWarsMode mode = arena == null ? BedWarsMode.ONE_VS_ONE : arena.getMode();
        int capacity = arena == null ? getArenaCapacity() : getArenaCapacity(arena);
        int legacyFallback = plugin.getConfig().getInt("settings.min-players", capacity);
        int configured = plugin.getConfig().getInt("settings.mode-min-players." + mode.getId(), legacyFallback);
        return Math.max(1, Math.min(configured, capacity));
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

    public void giveEndingLobbyItems(Arena arena) {
        if (arena == null) {
            return;
        }

        for (UUID uniqueId : new ArrayList<UUID>(arena.getPlayers())) {
            Player player = Bukkit.getPlayer(uniqueId);
            if (player == null || !player.isOnline()) {
                continue;
            }

            clearPendingRespawn(uniqueId);
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getEnderChest().clear();
            player.setItemOnCursor(null);
            player.setCanPickupItems(false);
            for (PotionEffect effect : new ArrayList<PotionEffect>(player.getActivePotionEffects())) {
                player.removePotionEffect(effect.getType());
            }
            int leaveSlot = resolveHotbarSlot("pre-game-items.leave-arena.slot", 8);
            player.getInventory().setItem(leaveSlot, createLeaveArenaItem());
            player.setLevel(0);
            player.setExp(0.0F);
            player.updateInventory();
        }
    }

    public boolean handleWaitingLobbyItemUse(Player player, ItemStack item) {
        if (player == null || item == null) {
            return false;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (arena == null) {
            return false;
        }

        if (item.getType() == Material.BED) {
            if (!isPreGameArena(arena) && arena.getState() != ArenaState.ENDING) {
                return false;
            }
            player.closeInventory();
            leaveArena(player, true);
            return true;
        }

        if (!isPreGameArena(arena) || arena.getSpectators().contains(player.getUniqueId())) {
            return false;
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
            sendNoArenaAvailable(player, BedWarsMode.ONE_VS_ONE);
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

            arena = plugin.getArenaManager().resolveJoinableArena(arena, getArenaCapacity(arena));
            if (arena == null) {
                sendNoArenaAvailable(player, arena.getMode());
                return;
            }
        }

        joinRuntimeArena(player, arena);
    }

    public void quickJoin(Player player) {
        quickJoin(player, BedWarsMode.ONE_VS_ONE);
    }

    public void quickJoin(Player player, BedWarsMode mode) {
        BedWarsMode queueMode = mode == null ? BedWarsMode.ONE_VS_ONE : mode;
        Arena runtimeArena = findBestRuntimeArena(queueMode);
        if (runtimeArena != null) {
            joinRuntimeArena(player, runtimeArena);
            return;
        }

        Arena template = findBestTemplateArena(queueMode);
        if (template == null) {
            sendNoArenaAvailable(player, queueMode);
            return;
        }

        joinArena(player, template);
    }

    public List<Arena> getJoinableArenas() {
        return getJoinableArenas(BedWarsMode.ONE_VS_ONE);
    }

    public List<Arena> getJoinableArenas(BedWarsMode mode) {
        List<Arena> arenas = new ArrayList<Arena>();
        for (Arena arena : plugin.getArenaManager().getConfiguredArenas()) {
            if (arena.isReady() && (mode == null || arena.getMode() == mode)) {
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

        ArenaState previousState = arena.getState();
        boolean wasIngame = previousState == ArenaState.INGAME;
        boolean wasPreGame = previousState == ArenaState.WAITING || previousState == ArenaState.STARTING;
        ArenaTeam leavingTeam = plugin.getTeamManager().getTeam(arena, player.getUniqueId());
        clearPendingRespawn(player.getUniqueId());
        arena.removePlayer(player.getUniqueId());
        plugin.getTeamManager().unassign(arena, player.getUniqueId());
        plugin.getArenaManager().clearPlayerArena(player.getUniqueId());
        plugin.getScoreboardManager().clear(player);

        if (restorePlayer) {
            restorePlayer(player, plugin.getLobbyManager().getMainWorldSpawn());
        }

        plugin.getMessageManager().send(player, "game.left");
        if (wasPreGame) {
            broadcast(arena, "game.leave-broadcast", placeholders(
                "player", player.getName(),
                "players", String.valueOf(arena.getPlayerCount()),
                "max_players", String.valueOf(getArenaCapacity(arena))
            ));
        } else if (wasIngame) {
            destroyTeamBedOnLeave(arena, leavingTeam, player.getName());
            broadcast(arena, "game.leave-ingame-broadcast", placeholders(
                "player", player.getName(),
                "players", String.valueOf(arena.getPlayerCount()),
                "max_players", String.valueOf(getArenaCapacity(arena))
            ));
        }

        if (wasIngame) {
            checkWin(arena);
        } else if (wasPreGame && arena.getPlayerCount() < getRequiredPlayersToStart(arena)) {
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

        Location spectatorLocation = resolveDeathSpectatorLocation(arena);
        if (team.isBedDestroyed()) {
            arena.getSpectators().add(player.getUniqueId());
            pendingRespawns.put(player.getUniqueId(), new PendingRespawn(spectatorLocation, spectatorLocation, true));
            plugin.getMessageManager().send(player, "game.death-final");
            SoundUtil.playConfigured(plugin, player, "sound-effects.final-death", "WITHER_DEATH", 1.0F, 1.0F);
        } else {
            Location respawnLocation = resolveTeamRespawn(arena, team);
            pendingRespawns.put(player.getUniqueId(), new PendingRespawn(spectatorLocation, respawnLocation, false));
            plugin.getMessageManager().send(player, "game.death-respawn");
        }

        plugin.getTeamManager().updateEliminationState(arena);
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
                        sendConfiguredTitle(player, "titles.game.final-death", Collections.<String, String>emptyMap(), 5, 50, 10);
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
                SoundUtil.playConfigured(plugin, member, "sound-effects.own-bed-destroyed", "WITHER_DEATH", 1.0F, 1.0F);
                sendConfiguredTitle(member, "titles.game.bed-destroyed", placeholders(
                    "team", team.getColor().getColoredName(),
                    "player", breaker.getName()
                ), 5, 60, 10);
            } else {
                SoundUtil.playConfigured(plugin, member, "sound-effects.bed-destroyed", "ENDERDRAGON_GROWL", 0.8F, 1.1F);
            }
        }
    }

    public void endGame(Arena arena, TeamColor winner) {
        if (arena.getState() == ArenaState.ENDING || arena.getState() == ArenaState.RESETTING) {
            return;
        }

        arena.setState(ArenaState.ENDING);
        arena.setEndCountdown(Math.max(1, plugin.getConfig().getInt("settings.ending-seconds", 15)));

        if (winner != null) {
            broadcast(arena, "game.winner", placeholders("team", winner.getColoredName(), "arena", arena.getDisplayName()));
        } else {
            broadcast(arena, "game.draw", Collections.<String, String>emptyMap());
        }

        giveEndingLobbyItems(arena);
        playEndGameSounds(arena, winner);
        sendEndGameTitles(arena, winner);
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
            maintainArenaWorld(arena);

            if (arena.getState() == ArenaState.WAITING) {
                if (arena.getPlayerCount() >= getRequiredPlayersToStart(arena)) {
                    arena.setState(ArenaState.STARTING);
                    arena.setCountdown(getStartingCountdown(arena));
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
        if (arena.getPlayerCount() < getRequiredPlayersToStart(arena)) {
            arena.setState(ArenaState.WAITING);
            arena.setCountdown(0);
            broadcast(arena, "game.countdown-cancelled", Collections.<String, String>emptyMap());
            if (arena.getPlayerCount() == 0) {
                cleanupEmptyArena(arena);
            }
            return;
        }

        int seconds = arena.getCountdown();
        if (seconds <= 0) {
            startGame(arena);
            return;
        }

        int targetCountdown = getStartingCountdown(arena);
        if (targetCountdown > 0 && seconds > targetCountdown) {
            arena.setCountdown(targetCountdown);
            seconds = targetCountdown;
        }

        if (seconds > 0 && shouldAnnounceCountdown(seconds) && plugin.getConfig().getBoolean("settings.start-countdown-chat", true)) {
            broadcast(arena, "game.countdown", Collections.singletonMap("seconds", String.valueOf(seconds)));
        }

        if (seconds > 0 && shouldAnnounceCountdown(seconds)) {
            playCountdownSounds(arena);
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

        int maxPlayers = getArenaCapacity(arena);
        if (arena.isFull(maxPlayers)) {
            plugin.getMessageManager().send(player, "game.arena-full");
            return;
        }
        if (arena.hasStartedMatch()) {
            plugin.getMessageManager().send(player, "game.arena-running");
            return;
        }

        if (!plugin.getWorldCloneManager().ensureClone(arena)) {
            player.sendMessage(plugin.getMessageManager().get("prefix") + ChatUtil.color("&cNao foi possivel preparar o clone do mapa desta arena."));
            return;
        }

        snapshots.put(player.getUniqueId(), PlayerSnapshot.capture(player));
        preparePlayer(player);
        player.getEnderChest().clear();

        arena.addPlayer(player.getUniqueId());
        plugin.getArenaManager().setPlayerArena(player.getUniqueId(), arena);

        Location joinLocation = resolveLobbySpawn(arena);
        if (joinLocation != null) {
            teleportSafely(player, joinLocation);
        }

        giveWaitingLobbyItems(player);
        broadcast(arena, "game.join-broadcast", placeholders(
            "player", player.getName(),
            "players", String.valueOf(arena.getPlayerCount()),
            "max_players", String.valueOf(maxPlayers)
        ));

        if (arena.getState() == ArenaState.WAITING && arena.getPlayerCount() >= getRequiredPlayersToStart(arena)) {
            arena.setState(ArenaState.STARTING);
            arena.setCountdown(getStartingCountdown(arena));
        }
    }

    private Arena findBestRuntimeArena(BedWarsMode mode) {
        List<Arena> arenas = new ArrayList<Arena>();

        for (Arena arena : plugin.getArenaManager().getRuntimeArenas()) {
            if (mode != null && arena.getMode() != mode) {
                continue;
            }
            if (arena.hasStartedMatch()) {
                continue;
            }
            if (arena.getState() != ArenaState.WAITING && arena.getState() != ArenaState.STARTING) {
                continue;
            }
            if (arena.isFull(getArenaCapacity(arena))) {
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

    private Arena findBestTemplateArena(BedWarsMode mode) {
        List<Arena> arenas = getJoinableArenas(mode);
        return arenas.isEmpty() ? null : arenas.get(0);
    }

    private void startGame(Arena arena) {
        arena.setState(ArenaState.INGAME);
        arena.markMatchStarted();
        arena.setElapsedTime(0);
        arena.clearSnapshots();
        assignRandomTeams(arena);

        for (ArenaTeam team : arena.getTeams().values()) {
            team.resetRuntime();
        }
        destroyUnusedTeamBeds(arena);
        plugin.getTeamManager().updateEliminationState(arena);

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
        plugin.getHologramManager().refreshArenaChestHolograms(arena);
        broadcast(arena, "game.game-started", Collections.<String, String>emptyMap());

        for (UUID uniqueId : new ArrayList<UUID>(arena.getPlayers())) {
            Player player = Bukkit.getPlayer(uniqueId);
            if (player == null || !player.isOnline()) {
                continue;
            }

            preparePlayer(player);
            ArenaTeam team = plugin.getTeamManager().getTeam(arena, uniqueId);
            if (team != null) {
                plugin.getShopManager().applyRespawnLoadout(player, team);
            }
            Location startLocation = team == null ? resolveLobbySpawn(arena) : resolveTeamRespawn(arena, team);
            if (startLocation != null) {
                teleportSafely(player, startLocation);
            }
            SoundUtil.playConfigured(plugin, player, "sound-effects.game-start", "ENDERDRAGON_GROWL", 0.7F, 1.2F);
        }
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
        applySpectatorState(player);
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
        applySpectatorState(player);
    }

    private void applySpectatorState(Player player) {
        boolean canFly = plugin.getConfig().getBoolean("settings.spectators-can-fly", true);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(canFly);
        player.setFlying(canFly);
        player.setCanPickupItems(false);
        player.setFallDistance(0.0F);
        player.updateInventory();
    }

    private void startRespawnCountdown(final Player player, final Arena arena, final Location respawnLocation) {
        clearRespawnTask(player.getUniqueId());

        final int[] seconds = new int[] {Math.max(1, plugin.getConfig().getInt("settings.respawn-seconds", 5))};
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
                    arena.getSpectators().remove(player.getUniqueId());
                    preparePlayer(player);
                    if (team != null) {
                        plugin.getShopManager().applyRespawnLoadout(player, team);
                    }
                    if (respawnLocation != null) {
                        teleportSafely(player, respawnLocation);
                    }
                    SoundUtil.playConfigured(plugin, player, "sound-effects.respawn", "LEVEL_UP", 1.0F, 1.1F);
                    sendConfiguredTitle(player, "titles.game.respawned", placeholders(
                        "team", team == null ? "&7Sem time" : team.getColor().getColoredName()
                    ), 5, 30, 10);
                    clearPendingRespawn(player.getUniqueId());
                    plugin.getTeamManager().updateEliminationState(arena);
                    return;
                }

                player.setLevel(seconds[0]);
                if (plugin.getConfig().getBoolean("settings.respawn-countdown-chat", false)) {
                    player.sendMessage(plugin.getMessageManager().get("prefix") + ChatUtil.color("&aRespawn em &f" + seconds[0] + "s&a."));
                }
                sendConfiguredTitle(player, "titles.game.respawn-countdown", Collections.singletonMap("seconds", String.valueOf(seconds[0])), 0, 25, 0);
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

        if (color != null && color != TeamColor.RED && color != TeamColor.BLUE) {
            return new ItemBuilder(Material.WOOL, 1, color.getWoolData())
                .name(plugin.getConfig().getString("pre-game-items.team-selector.name", "&bEscolher Time"))
                .lore(lore)
                .build();
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

    private TeamColor pickSmallestTeam(Arena arena) {
        List<TeamColor> colors = new ArrayList<TeamColor>(plugin.getTeamManager().getActiveColors(arena));
        Collections.shuffle(colors);
        TeamColor best = null;
        int bestSize = Integer.MAX_VALUE;
        int teamSize = plugin.getTeamManager().getTeamSize(arena);

        for (TeamColor color : colors) {
            int currentSize = plugin.getTeamManager().getTeamMemberCount(arena, color);
            if (currentSize >= teamSize) {
                continue;
            }
            if (best == null || currentSize < bestSize) {
                best = color;
                bestSize = currentSize;
            }
        }

        return best;
    }

    private void sendNoArenaAvailable(Player player, BedWarsMode mode) {
        plugin.getMessageManager().send(player, "game.no-arena-available", Collections.singletonMap(
            "mode", mode == null ? BedWarsMode.ONE_VS_ONE.getDisplayName() : mode.getDisplayName()
        ));
    }

    private void assignRandomTeams(Arena arena) {
        if (arena == null) {
            return;
        }

        Map<UUID, TeamColor> preferences = new HashMap<UUID, TeamColor>(arena.getPlayerTeams());
        arena.getPlayerTeams().clear();

        List<UUID> players = new ArrayList<UUID>(arena.getPlayers());
        Collections.shuffle(players);
        for (UUID uniqueId : players) {
            TeamColor preferred = preferences.get(uniqueId);
            if (preferred != null && plugin.getTeamManager().isTeamAvailable(arena, uniqueId, preferred)) {
                arena.getPlayerTeams().put(uniqueId, preferred);
                continue;
            }

            TeamColor next = pickSmallestTeam(arena);
            if (next != null) {
                arena.getPlayerTeams().put(uniqueId, next);
            }
        }
    }

    public boolean speedUpStart(Arena arena) {
        if (arena == null) {
            return false;
        }
        if (arena.getState() != ArenaState.WAITING && arena.getState() != ArenaState.STARTING) {
            return false;
        }
        if (arena.getPlayerCount() < getRequiredPlayersToStart(arena)) {
            return false;
        }

        int forceCountdown = Math.max(1, plugin.getConfig().getInt("settings.force-start-countdown", 5));
        int currentCountdown = arena.getCountdown();
        arena.setState(ArenaState.STARTING);
        if (currentCountdown <= 0) {
            arena.setCountdown(forceCountdown);
        } else {
            arena.setCountdown(Math.min(currentCountdown, forceCountdown));
        }
        return true;
    }

    private int getStartingCountdown(Arena arena) {
        int configured = Math.max(1, plugin.getConfig().getInt("settings.countdown-seconds", 10));
        if (!plugin.getConfig().getBoolean("settings.dynamic-start-countdown", true) || arena == null) {
            return configured;
        }

        int players = arena.getPlayerCount();
        int required = getRequiredPlayersToStart(arena);
        int capacity = Math.max(required, getArenaCapacity(arena));

        if (players >= capacity || players >= Math.max(required + 3, (int) Math.ceil(capacity * 0.75D))) {
            return 10;
        }
        if (players >= Math.max(required + 2, (int) Math.ceil(capacity * 0.60D))) {
            return 20;
        }
        if (players >= Math.max(required + 1, (int) Math.ceil(capacity * 0.45D))) {
            return 30;
        }
        return 40;
    }

    private boolean shouldAnnounceCountdown(int seconds) {
        return seconds == 40 || seconds == 30 || seconds == 20 || seconds == 10 || seconds <= 5;
    }

    private void destroyUnusedTeamBeds(Arena arena) {
        if (arena == null) {
            return;
        }

        for (TeamColor color : plugin.getTeamManager().getActiveColors(arena)) {
            ArenaTeam team = arena.getTeam(color);
            if (team == null || team.isBedDestroyed()) {
                continue;
            }
            if (!plugin.getTeamManager().getPlayersInTeam(arena, color).isEmpty()) {
                continue;
            }

            team.setBedDestroyed(true);
            team.setEliminated(true);
            removeTeamBedBlocks(arena, team);
        }
    }

    private void destroyTeamBedOnLeave(Arena arena, ArenaTeam team, String playerName) {
        if (arena == null || team == null || team.isBedDestroyed()) {
            return;
        }

        team.setBedDestroyed(true);
        removeTeamBedBlocks(arena, team);

        for (UUID uniqueId : arena.getPlayers()) {
            Player member = Bukkit.getPlayer(uniqueId);
            if (member == null || !member.isOnline()) {
                continue;
            }

            TeamColor color = plugin.getTeamManager().getColor(arena, uniqueId);
            if (color == team.getColor()) {
                plugin.getMessageManager().send(member, "game.your-bed-destroyed");
                SoundUtil.playConfigured(plugin, member, "sound-effects.own-bed-destroyed", "WITHER_DEATH", 1.0F, 1.0F);
                sendConfiguredTitle(member, "titles.game.bed-destroyed", placeholders(
                    "team", team.getColor().getColoredName(),
                    "player", playerName
                ), 5, 60, 10);
            } else {
                SoundUtil.playConfigured(plugin, member, "sound-effects.bed-destroyed", "ENDERDRAGON_GROWL", 0.8F, 1.1F);
            }
        }
    }

    private void removeTeamBedBlocks(Arena arena, ArenaTeam team) {
        if (arena == null || team == null || team.getBedData() == null) {
            return;
        }

        removeBedBlock(arena, team.getBedData().getHead());
        removeBedBlock(arena, team.getBedData().getFoot());
    }

    private void removeBedBlock(Arena arena, Location sourceLocation) {
        if (arena == null || sourceLocation == null) {
            return;
        }

        Location matchLocation = arena.getMatchLocation(sourceLocation);
        if (matchLocation == null) {
            return;
        }

        Block block = matchLocation.getBlock();
        BlockState state = block.getState();
        arena.registerSnapshot(state);
        if (block.getType() != Material.AIR) {
            block.setType(Material.AIR);
        }
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

    private Location resolveDeathSpectatorLocation(Arena arena) {
        if (arena == null) {
            return null;
        }

        Location waitingSpawn = resolveLobbySpawn(arena);
        if (waitingSpawn != null) {
            return waitingSpawn;
        }

        return arena.getSpectatorSpawn();
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

    private void maintainArenaWorld(Arena arena) {
        if (arena == null || arena.getMatchWorld() == null) {
            return;
        }

        if (plugin.getConfig().getBoolean("settings.arena-always-day", true)) {
            arena.getMatchWorld().setGameRuleValue("doDaylightCycle", "false");
            arena.getMatchWorld().setTime(1000L);
        }

        if (plugin.getConfig().getBoolean("settings.arena-clear-weather", true)) {
            arena.getMatchWorld().setStorm(false);
            arena.getMatchWorld().setThundering(false);
            arena.getMatchWorld().setWeatherDuration(Integer.MAX_VALUE);
            arena.getMatchWorld().setThunderDuration(Integer.MAX_VALUE);
        }
    }

    private void playCountdownSounds(Arena arena) {
        if (arena == null) {
            return;
        }

        for (UUID uniqueId : arena.getPlayers()) {
            Player player = Bukkit.getPlayer(uniqueId);
            if (player == null || !player.isOnline()) {
                continue;
            }

            SoundUtil.playConfigured(plugin, player, "sound-effects.countdown", "NOTE_PLING", 1.0F, 1.5F);
        }
    }

    private void sendEndGameTitles(Arena arena, TeamColor winner) {
        if (arena == null) {
            return;
        }

        for (UUID uniqueId : arena.getPlayers()) {
            Player player = Bukkit.getPlayer(uniqueId);
            if (player == null || !player.isOnline()) {
                continue;
            }

            if (winner == null) {
                sendConfiguredTitle(player, "titles.game.draw", Collections.<String, String>emptyMap(), 10, 60, 10);
                continue;
            }

            TeamColor playerTeam = plugin.getTeamManager().getColor(arena, uniqueId);
            if (playerTeam == winner) {
                sendConfiguredTitle(player, "titles.game.win", Collections.singletonMap("team", winner.getColoredName()), 10, 60, 10);
            } else {
                sendConfiguredTitle(player, "titles.game.lose", Collections.singletonMap("team", winner.getColoredName()), 10, 60, 10);
            }
        }
    }

    private void playEndGameSounds(Arena arena, TeamColor winner) {
        if (arena == null) {
            return;
        }

        for (UUID uniqueId : arena.getPlayers()) {
            Player player = Bukkit.getPlayer(uniqueId);
            if (player == null || !player.isOnline()) {
                continue;
            }

            if (winner == null) {
                SoundUtil.playConfigured(plugin, player, "sound-effects.draw", "NOTE_PLING", 1.0F, 1.0F);
                continue;
            }

            TeamColor playerTeam = plugin.getTeamManager().getColor(arena, uniqueId);
            if (playerTeam == winner) {
                SoundUtil.playConfigured(plugin, player, "sound-effects.victory", "LEVEL_UP", 1.0F, 1.1F);
            } else {
                SoundUtil.playConfigured(plugin, player, "sound-effects.defeat", "WITHER_DEATH", 0.7F, 1.0F);
            }
        }
    }

    private void sendConfiguredTitle(Player player, String basePath, Map<String, String> placeholders, int fadeIn, int stay, int fadeOut) {
        if (player == null || !player.isOnline() || !plugin.getConfig().getBoolean("settings.titles", true)) {
            return;
        }

        String title = getOptionalMessage(basePath + ".title", placeholders);
        String subtitle = getOptionalMessage(basePath + ".subtitle", placeholders);
        if ((title == null || title.trim().isEmpty()) && (subtitle == null || subtitle.trim().isEmpty())) {
            return;
        }

        TitleUtil.sendTitle(player, title, subtitle, fadeIn, stay, fadeOut);
    }

    private String getOptionalMessage(String path, Map<String, String> placeholders) {
        if (!plugin.getMessageManager().getConfiguration().contains(path)) {
            return "";
        }
        return plugin.getMessageManager().get(path, placeholders);
    }
}

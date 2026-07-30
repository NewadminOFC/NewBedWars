package n.plugins.newbedwars.manager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
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
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

public class GameManager {

    private final NewBedWars plugin;
    private final Map<UUID, PlayerSnapshot> snapshots;
    private final Map<UUID, PendingRespawn> pendingRespawns;
    private final Map<UUID, Integer> respawnTasks;
    private final Map<UUID, DamageTag> recentDamagers;
    private final Map<UUID, Arena> pendingArenaJoins;
    private final Map<String, Integer> requiredPlayersCache;
    private int taskId = -1;
    private int combatCleanupCounter = 0;
    private long requiredPlayersCacheTime;

    private static final long COMBAT_TAG_MILLIS = 10000L;
    private static final Vector ZERO_VECTOR = new Vector(0.0D, 0.0D, 0.0D);
    private static final long CONFIG_CACHE_TTL = 5000L;

    private boolean cachedStartCountdownChat = true;
    private int cachedCountdownSeconds = 10;
    private boolean cachedDynamicStartCountdown = true;
    private boolean cachedSoundsEnabled = true;
    private boolean cachedTitlesEnabled = true;
    private boolean cachedRespawnCountdownChat = false;
    private int cachedEndingSeconds = 15;
    private boolean cachedArenaAlwaysDay = true;
    private boolean cachedArenaClearWeather = true;
    private long lastConfigCacheTime = 0;

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

    private static class DamageTag {

        private final UUID attackerUniqueId;
        private final long expireAt;

        private DamageTag(UUID attackerUniqueId, long expireAt) {
            this.attackerUniqueId = attackerUniqueId;
            this.expireAt = expireAt;
        }
    }

    public GameManager(NewBedWars plugin) {
        this.plugin = plugin;
        this.snapshots = new HashMap<UUID, PlayerSnapshot>();
        this.pendingRespawns = new HashMap<UUID, PendingRespawn>();
        this.respawnTasks = new HashMap<UUID, Integer>();
        this.recentDamagers = new HashMap<UUID, DamageTag>();
        this.pendingArenaJoins = new HashMap<UUID, Arena>();
        this.requiredPlayersCache = new HashMap<String, Integer>();
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

        pendingArenaJoins.clear();
        for (Arena arena : new ArrayList<Arena>(plugin.getArenaManager().getRuntimeArenas())) {
            resetArena(arena);
        }
    }

    public void invalidateConfigurationCache() {
        lastConfigCacheTime = 0L;
        cachedSpectatorsCanFly = null;
        requiredPlayersCache.clear();
        requiredPlayersCacheTime = 0L;
    }

    public int getArenaCapacity() {
        return plugin.getTeamManager().getArenaCapacity();
    }

    private void refreshConfigCache() {
        long now = System.currentTimeMillis();
        if (now - lastConfigCacheTime < CONFIG_CACHE_TTL) {
            return;
        }
        lastConfigCacheTime = now;
        cachedStartCountdownChat = plugin.getConfig().getBoolean("settings.start-countdown-chat", true);
        cachedCountdownSeconds = Math.max(1, plugin.getConfig().getInt("settings.countdown-seconds", 10));
        cachedDynamicStartCountdown = plugin.getConfig().getBoolean("settings.dynamic-start-countdown", true);
        cachedSoundsEnabled = plugin.getConfig().getBoolean("settings.sounds", true);
        cachedTitlesEnabled = plugin.getConfig().getBoolean("settings.titles", true);
        cachedRespawnCountdownChat = plugin.getConfig().getBoolean("settings.respawn-countdown-chat", false);
        cachedEndingSeconds = Math.max(1, plugin.getConfig().getInt("settings.ending-seconds", 15));
        cachedArenaAlwaysDay = plugin.getConfig().getBoolean("settings.arena-always-day", true);
        cachedArenaClearWeather = plugin.getConfig().getBoolean("settings.arena-clear-weather", true);
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

    public void tagCombat(Player attacker, Player victim) {
        if (attacker == null || victim == null) {
            return;
        }

        if (attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        Arena attackerArena = plugin.getArenaManager().getArenaByPlayer(attacker.getUniqueId());
        Arena victimArena = plugin.getArenaManager().getArenaByPlayer(victim.getUniqueId());
        if (attackerArena == null || victimArena == null || attackerArena != victimArena || attackerArena.getState() != ArenaState.INGAME) {
            return;
        }

        if (attackerArena.getSpectators().contains(attacker.getUniqueId())
            || attackerArena.getSpectators().contains(victim.getUniqueId())
            || isRespawning(attacker.getUniqueId())
            || isRespawning(victim.getUniqueId())) {
            return;
        }

        TeamColor attackerColor = plugin.getTeamManager().getColor(attackerArena, attacker.getUniqueId());
        TeamColor victimColor = plugin.getTeamManager().getColor(attackerArena, victim.getUniqueId());
        if (attackerColor != null && attackerColor == victimColor) {
            return;
        }

        recentDamagers.put(victim.getUniqueId(), new DamageTag(attacker.getUniqueId(), System.currentTimeMillis() + COMBAT_TAG_MILLIS));
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
            BedWarsMode requestedMode = arena.getMode();
            if (!arena.isReady()) {
                plugin.getMessageManager().send(player, "game.arena-not-ready");
                return;
            }

            arena = plugin.getArenaManager().resolveJoinableArena(arena, getArenaCapacity(arena));
            if (arena == null) {
                sendNoArenaAvailable(player, requestedMode);
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

        final Map<String, Integer> templateCounts = new HashMap<String, Integer>(arenas.size() * 2);
        for (Arena arena : arenas) {
            String key = arena.getTemplateName();
            if (!templateCounts.containsKey(key)) {
                templateCounts.put(key, plugin.getArenaManager().countPlayersForTemplate(key));
            }
        }

        Collections.sort(arenas, new Comparator<Arena>() {
            @Override
            public int compare(Arena first, Arena second) {
                int firstPlayers = templateCounts.get(first.getTemplateName());
                int secondPlayers = templateCounts.get(second.getTemplateName());
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
            if (cancelPendingJoin(player.getUniqueId())) {
                plugin.getMessageManager().send(player, "game.clone-join-cancelled");
                return;
            }
            plugin.getMessageManager().send(player, "general.not-in-arena");
            return;
        }

        ArenaState previousState = arena.getState();
        boolean wasIngame = previousState == ArenaState.INGAME;
        boolean wasPreGame = previousState == ArenaState.WAITING || previousState == ArenaState.STARTING;
        ArenaTeam leavingTeam = plugin.getTeamManager().getTeam(arena, player.getUniqueId());
        clearPendingRespawn(player.getUniqueId());
        clearCombatState(player.getUniqueId());
        arena.removePlayer(player.getUniqueId());
        plugin.getTeamManager().unassign(arena, player.getUniqueId());
        boolean teamEliminatedOnLeave = leavingTeam != null && leavingTeam.isEliminated();
        plugin.getArenaManager().clearPlayerArena(player.getUniqueId());
        plugin.getScoreboardManager().clear(player);

        if (restorePlayer) {
            restorePlayer(player, plugin.getLobbyManager().getMainWorldSpawn());
            plugin.getMessageManager().send(player, "lobby.teleported");
        }
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
            if (teamEliminatedOnLeave) {
                broadcastSilently(arena, "game.team-eliminated", placeholders(
                    "team", leavingTeam.getColor().getColoredName()
                ));
            }
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

    public boolean cancelPendingJoin(UUID uniqueId) {
        return uniqueId != null && pendingArenaJoins.remove(uniqueId) != null;
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

        boolean finalDeath = team.isBedDestroyed();
        UUID killerUniqueId = resolveKillerUniqueId(player);
        announceDeath(arena, player, killerUniqueId, finalDeath);
        registerKill(arena, killerUniqueId, finalDeath);
        clearCombatState(player.getUniqueId());

        Location spectatorLocation = resolveDeathSpectatorLocation(arena);
        if (finalDeath) {
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
                        ArenaTeam finalTeam = plugin.getTeamManager().getTeam(arena, player.getUniqueId());
                        boolean wasEliminated = finalTeam != null && finalTeam.isEliminated();
                        makeSpectator(player, arena);
                        if (pendingRespawn.getSpectatorLocation() != null) {
                            teleportSafely(player, pendingRespawn.getSpectatorLocation());
                        }
                        sendConfiguredTitle(player, "titles.game.final-death", Collections.<String, String>emptyMap(), 5, 50, 10);
                        clearPendingRespawn(player.getUniqueId());
                        plugin.getTeamManager().updateEliminationState(arena);
                        if (finalTeam != null && finalTeam.isEliminated() && !wasEliminated) {
                            broadcastSilently(arena, "game.team-eliminated", placeholders(
                                "team", finalTeam.getColor().getColoredName()
                            ));
                        }
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
        String breakerName = resolveColoredPlayerName(arena, breaker.getUniqueId());
        String coloredTeamName = team.getColor().getColoredName();
        broadcast(arena, "game.bed-destroyed", placeholders("team", coloredTeamName, "player", breakerName));

        Map<String, String> titlePlaceholders = placeholders("team", coloredTeamName, "player", breakerName);
        TeamColor teamColor = team.getColor();

        for (UUID uniqueId : arena.getPlayers()) {
            Player member = Bukkit.getPlayer(uniqueId);
            if (member == null || !member.isOnline()) {
                continue;
            }

            TeamColor color = plugin.getTeamManager().getColor(arena, uniqueId);
            if (color == teamColor) {
                plugin.getMessageManager().send(member, "game.your-bed-destroyed");
                SoundUtil.playConfigured(plugin, member, "sound-effects.own-bed-destroyed", "WITHER_DEATH", 1.0F, 1.0F);
                sendConfiguredTitle(member, "titles.game.bed-destroyed", titlePlaceholders, 5, 60, 10);
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
        arena.setEndCountdown(cachedEndingSeconds);

        if (winner != null) {
            broadcastSilently(arena, "game.winner", placeholders("team", winner.getColoredName(), "arena", arena.getDisplayName()));
        } else {
            broadcastSilently(arena, "game.draw", Collections.<String, String>emptyMap());
        }

        giveEndingLobbyItems(arena);
        sendVictorySummary(arena, winner);
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

        for (UUID uniqueId : arena.getPlayers()) {
            clearPendingRespawn(uniqueId);
            clearCombatState(uniqueId);
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
        arena.clearKillStats();
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
        plugin.getTeamManager().invalidateAliveTeamsCache(arena);

        if (arena.isRuntimeInstance()) {
            plugin.getArenaManager().removeRuntimeArena(arena);
        } else {
            plugin.getNpcManager().refreshArenaShopNpcs(arena);
        }
    }

    private int getCachedRequiredPlayers(Arena arena) {
        long now = System.currentTimeMillis();
        if (now - requiredPlayersCacheTime > CONFIG_CACHE_TTL) {
            requiredPlayersCache.clear();
            requiredPlayersCacheTime = now;
        }

        BedWarsMode mode = arena == null ? BedWarsMode.ONE_VS_ONE : arena.getMode();
        String cacheKey = mode.getId() + ":" + (arena == null ? getArenaCapacity() : getArenaCapacity(arena));
        Integer cached = requiredPlayersCache.get(cacheKey);
        if (cached == null) {
            cached = Integer.valueOf(getRequiredPlayersToStart(arena));
            requiredPlayersCache.put(cacheKey, cached);
        }
        return cached.intValue();
    }

    private void tickArenas() {
        refreshConfigCache();
        combatCleanupCounter++;
        if (combatCleanupCounter >= 5) {
            combatCleanupCounter = 0;
            purgeExpiredCombatTags();
        }

        for (Arena arena : new ArrayList<Arena>(plugin.getArenaManager().getRuntimeArenasView())) {
            if (arena.getState() == ArenaState.WAITING) {
                if (arena.getPlayerCount() >= getCachedRequiredPlayers(arena)) {
                    arena.setState(ArenaState.STARTING);
                    arena.setCountdown(getStartingCountdown(arena));
                }
            } else if (arena.getState() == ArenaState.STARTING) {
                handleStartingTick(arena);
            } else if (arena.getState() == ArenaState.INGAME) {
                int elapsed = arena.getElapsedTime() + 1;
                arena.setElapsedTime(elapsed);
                if (elapsed % 60 == 0) {
                    maintainArenaWorld(arena);
                }
                plugin.getShopManager().tickArenaEffects(arena);
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

        if (seconds > 0 && shouldAnnounceCountdown(seconds) && cachedStartCountdownChat) {
            broadcast(arena, "game.countdown", Collections.singletonMap("seconds", String.valueOf(seconds)));
        }

        if (seconds > 0 && shouldAnnounceCountdown(seconds)) {
            playCountdownSounds(arena);
        }

        arena.setCountdown(seconds - 1);
    }

    private void joinRuntimeArena(Player player, Arena arena) {
        if (player == null || arena == null) {
            return;
        }
        if (!arena.isReady()) {
            plugin.getMessageManager().send(player, "game.arena-not-ready");
            return;
        }

        if (arena.getState() != ArenaState.WAITING && arena.getState() != ArenaState.STARTING) {
            plugin.getMessageManager().send(player, "game.arena-running");
            return;
        }

        int maxPlayers = getArenaCapacity(arena);
        if (arena.getPlayerCount() + countPendingJoins(arena) >= maxPlayers) {
            plugin.getMessageManager().send(player, "game.arena-full");
            return;
        }
        if (arena.hasStartedMatch()) {
            plugin.getMessageManager().send(player, "game.arena-running");
            return;
        }

        if (pendingArenaJoins.containsKey(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "game.clone-preparing");
            return;
        }

        final Player joiningPlayer = player;
        final Arena joiningArena = arena;
        pendingArenaJoins.put(player.getUniqueId(), arena);
        plugin.getMessageManager().send(player, "game.clone-preparing");
        plugin.getWorldCloneManager().ensureClone(arena, new WorldCloneManager.CloneCallback() {
            @Override
            public void complete(boolean success) {
                Arena reservedArena = pendingArenaJoins.remove(joiningPlayer.getUniqueId());
                if (reservedArena != joiningArena) {
                    cleanupUnusedPreparedArena(joiningArena);
                    return;
                }
                if (!joiningPlayer.isOnline()) {
                    cleanupUnusedPreparedArena(joiningArena);
                    return;
                }
                if (!success) {
                    plugin.getMessageManager().send(joiningPlayer, "game.clone-prepare-failed");
                    cleanupUnusedPreparedArena(joiningArena);
                    return;
                }
                if (plugin.getArenaManager().getArenaByPlayer(joiningPlayer.getUniqueId()) != null) {
                    return;
                }
                if (plugin.getArenaManager().getArena(joiningArena.getName()) != joiningArena
                    || joiningArena.hasStartedMatch()
                    || (joiningArena.getState() != ArenaState.WAITING && joiningArena.getState() != ArenaState.STARTING)) {
                    plugin.getMessageManager().send(joiningPlayer, "game.arena-running");
                    return;
                }
                if (joiningArena.getPlayerCount() + countPendingJoins(joiningArena) >= getArenaCapacity(joiningArena)) {
                    plugin.getMessageManager().send(joiningPlayer, "game.arena-full");
                    return;
                }
                completeRuntimeJoin(joiningPlayer, joiningArena);
            }
        });
    }

    private void cleanupUnusedPreparedArena(Arena arena) {
        if (arena == null
            || arena.getPlayerCount() > 0
            || countPendingJoins(arena) > 0
            || plugin.getArenaManager().getArena(arena.getName()) != arena) {
            return;
        }
        cleanupEmptyArena(arena);
    }

    private void completeRuntimeJoin(Player player, Arena arena) {
        int maxPlayers = getArenaCapacity(arena);
        snapshots.put(player.getUniqueId(), PlayerSnapshot.capture(player));
        clearCombatState(player.getUniqueId());
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

    private int countPendingJoins(Arena arena) {
        if (arena == null || pendingArenaJoins.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Arena pendingArena : pendingArenaJoins.values()) {
            if (pendingArena == arena) {
                count++;
            }
        }
        return count;
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
            if (arena.getPlayerCount() + countPendingJoins(arena) >= getArenaCapacity(arena)) {
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
        arena.clearKillStats();
        arena.clearSnapshots();
        clearArenaCombatTags(arena);
        assignRandomTeams(arena);

        for (ArenaTeam team : arena.getTeams().values()) {
            team.resetRuntime();
        }
        destroyUnusedTeamBeds(arena);
        plugin.getTeamManager().updateEliminationState(arena);

        final CuboidRegion waitingRegion = arena.getMatchRegion(arena.getWaitingRegion());
        if (waitingRegion != null) {
            clearRegionBatched(arena, waitingRegion);
        }

        plugin.getSetupManager().clearArenaSetupVisuals(arena);
        plugin.getHologramManager().refreshArenaChestHolograms(arena);
        broadcast(arena, "game.game-started", Collections.<String, String>emptyMap());

        for (UUID uniqueId : arena.getPlayers()) {
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

        // Os teletransportes carregam os chunks das bases primeiro. Depois disso,
        // os NPCs são criados gradualmente para não concentrar Citizens + chunks
        // no mesmo tick de início da partida.
        plugin.getNpcManager().refreshArenaShopNpcs(arena);
    }

    private void clearRegionBatched(final Arena arena, CuboidRegion region) {
        final Iterator<Block> blocks = region.blockIterator();
        if (!blocks.hasNext()) {
            return;
        }

        final int batchSize = Math.max(25, Math.min(2000,
            plugin.getConfig().getInt("performance.region-blocks-per-tick", 250)));
        long warningSize = Math.max(1000L,
            plugin.getConfig().getLong("performance.region-warning-blocks", 100000L));
        if (region.getVolume() > warningSize) {
            plugin.getLogger().warning("A regiao de espera da arena " + arena.getName()
                + " possui " + region.getVolume() + " blocos; a limpeza sera dividida entre varios ticks.");
        }

        final int[] scheduledTask = new int[] {-1};
        scheduledTask[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (arena.getState() != ArenaState.INGAME || !blocks.hasNext()) {
                    Bukkit.getScheduler().cancelTask(scheduledTask[0]);
                    return;
                }

                int processed = 0;
                while (processed < batchSize && blocks.hasNext()) {
                    Block block = blocks.next();
                    if (block.getType() != Material.AIR) {
                        arena.registerSnapshot(block.getState());
                        block.setType(Material.AIR, false);
                    }
                    processed++;
                }

                if (!blocks.hasNext()) {
                    Bukkit.getScheduler().cancelTask(scheduledTask[0]);
                }
            }
        }, 1L, 1L);
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
        plugin.getTeamManager().invalidateAliveTeamsCache(arena);

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
        player.setFallDistance(0.0F);

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
        giveSpectatorLeaveItem(player, arena);
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

    private Boolean cachedSpectatorsCanFly;
    private long spectatorFlyCacheTime;

    private void applySpectatorState(final Player player) {
        long now = System.currentTimeMillis();
        if (cachedSpectatorsCanFly == null || now - spectatorFlyCacheTime >= 5000L) {
            cachedSpectatorsCanFly = plugin.getConfig().getBoolean("settings.spectators-can-fly", true);
            spectatorFlyCacheTime = now;
        }
        boolean canFly = cachedSpectatorsCanFly;
        player.setGameMode(GameMode.ADVENTURE);
        synchronizeSpectatorFlight(player, canFly);
        player.setCanPickupItems(false);
        player.setFallDistance(0.0F);
        player.updateInventory();

        if (canFly) {
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        return;
                    }
                    Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
                    if (arena != null
                        && (arena.getSpectators().contains(player.getUniqueId())
                            || isRespawning(player.getUniqueId()))) {
                        synchronizeSpectatorFlight(player, true);
                    }
                }
            }, 2L);
        }
    }

    private void synchronizeSpectatorFlight(Player player, boolean canFly) {
        if (player == null) {
            return;
        }
        if (player.getAllowFlight() != canFly) {
            player.setAllowFlight(canFly);
        }
        if (canFly && !player.isFlying()) {
            player.setFlying(true);
        } else if (!canFly && player.isFlying()) {
            player.setFlying(false);
        }
        player.setFallDistance(0.0F);
    }

    private void giveSpectatorLeaveItem(Player player, Arena arena) {
        if (player == null || arena == null || arena.getState() != ArenaState.INGAME) {
            return;
        }

        int leaveSlot = resolveHotbarSlot("pre-game-items.leave-arena.slot", 8);
        player.getInventory().setItem(leaveSlot, createLeaveArenaItem());
        player.updateInventory();
    }

    private void startRespawnCountdown(final Player player, final Arena arena, final Location respawnLocation) {
        clearRespawnTask(player.getUniqueId());

        final int[] seconds = new int[] {Math.max(1, plugin.getConfig().getInt("settings.respawn-seconds", 5))};
        final UUID playerId = player.getUniqueId();
        int task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    clearPendingRespawn(playerId);
                    return;
                }

                if (plugin.getArenaManager().getArenaByPlayer(playerId) != arena) {
                    clearPendingRespawn(playerId);
                    return;
                }

                if (Boolean.TRUE.equals(cachedSpectatorsCanFly)
                    && (!player.getAllowFlight() || !player.isFlying())) {
                    synchronizeSpectatorFlight(player, true);
                }

                if (seconds[0] <= 0) {
                    ArenaTeam team = plugin.getTeamManager().getTeam(arena, playerId);
                    arena.getSpectators().remove(playerId);
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
                    clearPendingRespawn(playerId);
                    plugin.getTeamManager().updateEliminationState(arena);
                    return;
                }

                player.setLevel(seconds[0]);
                if (cachedRespawnCountdownChat) {
                    plugin.getMessageManager().send(player, "game.respawn-countdown-chat",
                        Collections.singletonMap("seconds", String.valueOf(seconds[0])));
                }
                sendConfiguredTitle(player, "titles.game.respawn-countdown", Collections.singletonMap("seconds", String.valueOf(seconds[0])), 0, 25, 0);
                seconds[0]--;
            }
        }, 0L, 20L);

        respawnTasks.put(playerId, Integer.valueOf(task));
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

    private UUID resolveKillerUniqueId(Player victim) {
        if (victim == null) {
            return null;
        }

        UUID direct = resolveDirectAttackerUniqueId(victim.getLastDamageCause());
        if (direct != null && !direct.equals(victim.getUniqueId())) {
            return direct;
        }

        UUID tagged = resolveTaggedKillerUniqueId(victim);
        if (tagged != null && !tagged.equals(victim.getUniqueId())) {
            return tagged;
        }

        return null;
    }

    private UUID resolveDirectAttackerUniqueId(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent)) {
            return null;
        }

        return resolveAttackerUniqueId(((EntityDamageByEntityEvent) event).getDamager());
    }

    private UUID resolveAttackerUniqueId(Entity damager) {
        if (damager instanceof Player) {
            return ((Player) damager).getUniqueId();
        }

        if (damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Player) {
                return ((Player) shooter).getUniqueId();
            }
        }

        return null;
    }

    private UUID resolveTaggedKillerUniqueId(Player victim) {
        if (victim == null) {
            return null;
        }

        DamageTag damageTag = recentDamagers.get(victim.getUniqueId());
        if (damageTag == null) {
            return null;
        }

        if (damageTag.expireAt <= System.currentTimeMillis()) {
            recentDamagers.remove(victim.getUniqueId());
            return null;
        }

        Arena victimArena = plugin.getArenaManager().getArenaByPlayer(victim.getUniqueId());
        Arena attackerArena = plugin.getArenaManager().getArenaByPlayer(damageTag.attackerUniqueId);
        if (victimArena == null || attackerArena == null || victimArena != attackerArena) {
            recentDamagers.remove(victim.getUniqueId());
            return null;
        }

        return damageTag.attackerUniqueId;
    }

    private void registerKill(Arena arena, UUID killerUniqueId, boolean finalKill) {
        if (arena == null
            || killerUniqueId == null
            || !arena.getPlayers().contains(killerUniqueId)
            || arena.getSpectators().contains(killerUniqueId)
            || isRespawning(killerUniqueId)) {
            return;
        }

        arena.addKill(killerUniqueId);
        if (finalKill) {
            arena.addFinalKill(killerUniqueId);
        }
    }

    private void announceDeath(Arena arena, Player victim, UUID killerUniqueId, boolean finalDeath) {
        if (arena == null || victim == null) {
            return;
        }

        Map<String, String> values = new HashMap<String, String>(8);
        values.put("victim", victim.getName());
        values.put("victim_colored", resolveColoredPlayerName(arena, victim.getUniqueId()));
        values.put("victim_team", resolveTeamPrefix(arena, victim.getUniqueId()));

        if (killerUniqueId != null) {
            values.put("killer", resolvePlayerName(killerUniqueId));
            values.put("killer_colored", resolveColoredPlayerName(arena, killerUniqueId));
            values.put("killer_team", resolveTeamPrefix(arena, killerUniqueId));
            broadcastSilently(arena, finalDeath ? "game.final-kill" : "game.kill", values);
            return;
        }

        EntityDamageEvent cause = victim.getLastDamageCause();
        if (cause != null && cause.getCause() == EntityDamageEvent.DamageCause.VOID) {
            broadcastSilently(arena, finalDeath ? "game.final-kill-void" : "game.kill-void", values);
            return;
        }

        broadcastSilently(arena, finalDeath ? "game.final-kill-generic" : "game.kill-generic", values);
    }

    private void clearCombatState(UUID uniqueId) {
        if (uniqueId == null) {
            return;
        }

        recentDamagers.remove(uniqueId);

        Iterator<Map.Entry<UUID, DamageTag>> iterator = recentDamagers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, DamageTag> entry = iterator.next();
            DamageTag tag = entry.getValue();
            if (tag == null || uniqueId.equals(tag.attackerUniqueId)) {
                iterator.remove();
            }
        }
    }

    private void purgeExpiredCombatTags() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, DamageTag>> iterator = recentDamagers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, DamageTag> entry = iterator.next();
            DamageTag tag = entry.getValue();
            if (tag == null || tag.expireAt <= now) {
                iterator.remove();
            }
        }
    }

    private void clearArenaCombatTags(Arena arena) {
        if (arena == null) {
            return;
        }

        for (UUID uniqueId : arena.getPlayers()) {
            recentDamagers.remove(uniqueId);
        }
    }

    private void sendVictorySummary(Arena arena, TeamColor winner) {
        if (arena == null) {
            return;
        }

        List<String> lines = new ArrayList<String>();
        lines.add(plugin.getMessageManager().get("game.victory-summary-header"));
        lines.add(plugin.getMessageManager().get("game.victory-summary-title"));
        lines.add(plugin.getMessageManager().get("game.victory-summary-winner", placeholders(
            "winner", resolveWinnerDisplay(arena, winner)
        )));
        lines.add("");

        List<Map.Entry<UUID, Integer>> topFinalKills = getTopFinalKills(arena);
        if (topFinalKills.isEmpty()) {
            lines.add(plugin.getMessageManager().get("game.victory-summary-empty"));
        } else {
            int limit = Math.min(3, topFinalKills.size());
            for (int i = 0; i < limit; i++) {
                Map.Entry<UUID, Integer> entry = topFinalKills.get(i);
                lines.add(plugin.getMessageManager().get("game.victory-summary-entry", placeholders(
                    "position", formatVictoryPosition(i + 1),
                    "player", resolveColoredPlayerName(arena, entry.getKey()),
                    "kills", String.valueOf(entry.getValue())
                )));
            }
        }

        lines.add(plugin.getMessageManager().get("game.victory-summary-footer"));

        for (UUID uniqueId : arena.getPlayers()) {
            Player player = Bukkit.getPlayer(uniqueId);
            if (player == null || !player.isOnline()) {
                continue;
            }

            for (String line : lines) {
                player.sendMessage(line == null ? "" : line);
            }
        }
    }

    private String resolveWinnerDisplay(Arena arena, TeamColor winner) {
        if (winner == null) {
            return "&7Ninguem";
        }

        if (arena == null || arena.getMode().getTeamSize() > 1) {
            return winner.getColoredName();
        }

        for (UUID uniqueId : plugin.getTeamManager().getPlayersInTeam(arena, winner)) {
            if (!arena.getSpectators().contains(uniqueId)) {
                return resolvePlayerName(uniqueId);
            }
        }

        List<UUID> members = plugin.getTeamManager().getPlayersInTeam(arena, winner);
        if (!members.isEmpty()) {
            return resolvePlayerName(members.get(0));
        }

        return winner.getColoredName();
    }

    private List<Map.Entry<UUID, Integer>> getTopFinalKills(Arena arena) {
        List<Map.Entry<UUID, Integer>> top = new ArrayList<Map.Entry<UUID, Integer>>();
        if (arena == null) {
            return top;
        }

        for (Map.Entry<UUID, Integer> entry : arena.getPlayerFinalKills().entrySet()) {
            if (entry.getValue() == null || entry.getValue().intValue() <= 0) {
                continue;
            }
            top.add(entry);
        }

        Collections.sort(top, new Comparator<Map.Entry<UUID, Integer>>() {
            @Override
            public int compare(Map.Entry<UUID, Integer> first, Map.Entry<UUID, Integer> second) {
                int killCompare = Integer.compare(second.getValue().intValue(), first.getValue().intValue());
                if (killCompare != 0) {
                    return killCompare;
                }
                return resolvePlayerName(first.getKey()).compareToIgnoreCase(resolvePlayerName(second.getKey()));
            }
        });
        return top;
    }

    private String formatVictoryPosition(int position) {
        return position + "o";
    }

    private String resolvePlayerName(UUID uniqueId) {
        if (uniqueId == null) {
            return "Desconhecido";
        }

        Player online = Bukkit.getPlayer(uniqueId);
        if (online != null) {
            return online.getName();
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(uniqueId);
        if (offline != null && offline.getName() != null && !offline.getName().trim().isEmpty()) {
            return offline.getName();
        }

        return uniqueId.toString().substring(0, 8);
    }

    private String resolveColoredPlayerName(Arena arena, UUID uniqueId) {
        return resolveTeamPrefix(arena, uniqueId) + resolvePlayerName(uniqueId);
    }

    private String resolveTeamPrefix(Arena arena, UUID uniqueId) {
        if (arena == null || uniqueId == null) {
            return "&7";
        }

        TeamColor color = plugin.getTeamManager().getColor(arena, uniqueId);
        return color == null ? "&7" : color.getChatColor().toString();
    }

    private void broadcastSilently(Arena arena, String path, Map<String, String> placeholders) {
        if (arena == null) {
            return;
        }

        for (UUID uniqueId : arena.getPlayers()) {
            Player player = Bukkit.getPlayer(uniqueId);
            if (player != null && player.isOnline()) {
                plugin.getMessageManager().send(player, path, placeholders);
            }
        }
    }

    private void broadcast(Arena arena, String path, Map<String, String> placeholders) {
        for (UUID uniqueId : arena.getPlayers()) {
            Player player = Bukkit.getPlayer(uniqueId);
            if (player == null || !player.isOnline()) {
                continue;
            }

            plugin.getMessageManager().send(player, path, placeholders);
            if (cachedSoundsEnabled) {
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
        List<String> configLore = plugin.getConfig().getStringList("pre-game-items.team-selector.lore");
        List<String> lore = new ArrayList<String>(configLore.size());
        String teamDisplay = color == null ? "&7Aleatorio" : color.getColoredName();
        for (String line : configLore) {
            lore.add(line.replace("%team%", teamDisplay));
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
        int configured = cachedCountdownSeconds;
        if (!cachedDynamicStartCountdown || arena == null) {
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

        TeamColor teamColor = team.getColor();
        String coloredTeamName = teamColor.getColoredName();
        Map<String, String> titlePlaceholders = placeholders("team", coloredTeamName, "player", playerName);

        for (UUID uniqueId : arena.getPlayers()) {
            Player member = Bukkit.getPlayer(uniqueId);
            if (member == null || !member.isOnline()) {
                continue;
            }

            TeamColor color = plugin.getTeamManager().getColor(arena, uniqueId);
            if (color == teamColor) {
                plugin.getMessageManager().send(member, "game.your-bed-destroyed");
                SoundUtil.playConfigured(plugin, member, "sound-effects.own-bed-destroyed", "WITHER_DEATH", 1.0F, 1.0F);
                sendConfiguredTitle(member, "titles.game.bed-destroyed", titlePlaceholders, 5, 60, 10);
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
        Map<String, String> map = new HashMap<String, String>((values.length / 2) * 2);
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(values[i], values[i + 1]);
        }
        return map;
    }

    private void teleportSafely(final Player player, final Location target) {
        if (player == null || target == null || target.getWorld() == null) {
            return;
        }

        if (target.getWorld().isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4)) {
            player.teleport(target);
        } else {
            target.getChunk().load();
            player.teleport(target);
        }
    }

    private boolean respawnReflectionInitialized;
    private boolean respawnReflectionFailed;
    private Method respawnSpigotMethod;
    private Method respawnSpigotRespawnMethod;
    private Class<?> respawnCraftPlayerClass;
    private Method respawnGetHandleMethod;
    private Field respawnPlayerConnectionField;
    private Class<?> respawnPacketClass;
    private Constructor<?> respawnPacketConstructor;
    private Method respawnConnectionMethod;

    private void initRespawnReflection() {
        if (respawnReflectionInitialized) {
            return;
        }
        respawnReflectionInitialized = true;

        try {
            Method m = Player.class.getMethod("spigot");
            Method r = m.getReturnType().getMethod("respawn");
            respawnSpigotMethod = m;
            respawnSpigotRespawnMethod = r;
            return;
        } catch (Exception ignored) {
        }

        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            String version = packageName.substring(packageName.lastIndexOf('.') + 1);
            respawnCraftPlayerClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
            respawnGetHandleMethod = respawnCraftPlayerClass.getMethod("getHandle");
            Class<?> handleClass = Class.forName("net.minecraft.server." + version + ".EntityPlayer");
            respawnPlayerConnectionField = handleClass.getField("playerConnection");
            respawnPacketClass = Class.forName("net.minecraft.server." + version + ".PacketPlayInClientCommand");
            Class<?> enumClass = Class.forName("net.minecraft.server." + version + ".PacketPlayInClientCommand$EnumClientCommand");
            respawnPacketConstructor = respawnPacketClass.getConstructor(enumClass);
            Class<?> connectionClass = Class.forName("net.minecraft.server." + version + ".PlayerConnection");
            respawnConnectionMethod = connectionClass.getMethod("a", respawnPacketClass);
        } catch (Exception exception) {
            respawnReflectionFailed = true;
        }
    }

    private void forceInstantRespawn(final Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (player == null || !player.isOnline()) {
                    return;
                }

                initRespawnReflection();

                if (respawnSpigotMethod != null) {
                    try {
                        Object spigot = respawnSpigotMethod.invoke(player);
                        respawnSpigotRespawnMethod.invoke(spigot);
                        return;
                    } catch (Exception ignored) {
                    }
                }

                if (respawnReflectionFailed) {
                    return;
                }

                try {
                    Object handle = respawnGetHandleMethod.invoke(player);
                    Object connection = respawnPlayerConnectionField.get(handle);
                    @SuppressWarnings("unchecked")
                    Object performRespawn = Enum.valueOf((Class<Enum>) respawnPacketConstructor.getParameterTypes()[0].asSubclass(Enum.class), "PERFORM_RESPAWN");
                    Object packet = respawnPacketConstructor.newInstance(performRespawn);
                    respawnConnectionMethod.invoke(connection, packet);
                } catch (Exception exception) {
                    respawnReflectionFailed = true;
                    plugin.getLogger().warning("Respawn instantaneo indisponivel nesta versao do servidor; o fallback foi desativado.");
                }
            }
        }, 1L);
    }

    private void maintainArenaWorld(Arena arena) {
        if (arena == null) {
            return;
        }

        if (arena.getElapsedTime() % 60 != 0) {
            return;
        }

        org.bukkit.World matchWorld = arena.getMatchWorld();
        if (matchWorld == null) {
            return;
        }

        if (cachedArenaAlwaysDay) {
            matchWorld.setGameRuleValue("doDaylightCycle", "false");
            matchWorld.setTime(1000L);
        }

        if (cachedArenaClearWeather) {
            matchWorld.setStorm(false);
            matchWorld.setThundering(false);
            matchWorld.setWeatherDuration(Integer.MAX_VALUE);
            matchWorld.setThunderDuration(Integer.MAX_VALUE);
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

        Map<String, String> winnerPlaceholder = winner == null ? null : Collections.singletonMap("team", winner.getColoredName());

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
                sendConfiguredTitle(player, "titles.game.win", winnerPlaceholder, 10, 60, 10);
            } else {
                sendConfiguredTitle(player, "titles.game.lose", winnerPlaceholder, 10, 60, 10);
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
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!cachedTitlesEnabled) {
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

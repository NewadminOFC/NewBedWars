package n.plugins.newbedwars.manager;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaState;
import n.plugins.newbedwars.arena.ArenaTeam;
import n.plugins.newbedwars.arena.BedWarsMode;
import n.plugins.newbedwars.arena.TeamColor;
import n.plugins.newbedwars.npc.BedWarsNpcType;
import n.plugins.newbedwars.npc.NpcHologram;
import n.plugins.newbedwars.util.ChatUtil;
import n.plugins.newbedwars.util.LocationUtil;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.NameTagVisibility;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class NpcManager {

    private static final String DATA_TYPE = "newbedwars:type";
    private static final String DATA_SKIN = "newbedwars:skin";
    private static final String DATA_ARENA = "newbedwars:arena";
    private static final String DATA_TEAM = "newbedwars:team";
    private static final String DATA_MODE = "newbedwars:mode";
    private final NewBedWars plugin;
    private final Map<Integer, NpcHologram> holograms;
    private final Map<String, Integer> runtimeShopNpcIds;
    private final Queue<ShopSpawnRequest> shopSpawnQueue;
    private final java.util.Set<Integer> trackedBedwarsNpcIds = new java.util.HashSet<Integer>();
    private int taskId = -1;
    private int shopSpawnTaskId = -1;

    private static final class ShopSpawnRequest {
        private final Arena arena;
        private final ArenaTeam team;
        private final BedWarsNpcType type;
        private final Location location;
        private int attempts;

        private ShopSpawnRequest(Arena arena, ArenaTeam team, BedWarsNpcType type, Location location) {
            this.arena = arena;
            this.team = team;
            this.type = type;
            this.location = location == null ? null : location.clone();
        }
    }

    private String cachedItemShopTopText;
    private String cachedItemShopBottomText;
    private double cachedItemShopTopHeight;
    private double cachedItemShopBottomHeight;
    private String cachedUpgradeShopTopText;
    private String cachedUpgradeShopBottomText;
    private double cachedUpgradeShopTopHeight;
    private double cachedUpgradeShopBottomHeight;
    private String cachedSoloTopText;
    private String cachedSoloBottomText;
    private double cachedSoloTopHeight;
    private double cachedSoloBottomHeight;
    private long hologramConfigCacheTime;
    private static final long HOLOGRAM_CONFIG_TTL = 5000L;

    public NpcManager(NewBedWars plugin) {
        this.plugin = plugin;
        this.holograms = new HashMap<Integer, NpcHologram>();
        this.runtimeShopNpcIds = new HashMap<String, Integer>();
        this.shopSpawnQueue = new ArrayDeque<ShopSpawnRequest>();
        refreshHologramConfigCache();
    }

    public void start() {
        if (taskId != -1) {
            return;
        }

        destroyOrphanedShopNpcs();

        long refreshTicks = Math.max(40L, plugin.getConfig().getLong("npc.refresh-ticks", 100L));
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                refreshVisuals();
            }
        }, refreshTicks, refreshTicks);
    }

    private void destroyOrphanedShopNpcs() {
        int count = 0;
        java.util.Iterator<NPC> iterator = CitizensAPI.getNPCRegistry().iterator();
        while (iterator.hasNext()) {
            NPC npc = iterator.next();
            if (isBedWarsNpc(npc) && (isItemShopNpc(npc) || isUpgradeShopNpc(npc))) {
                removeHologram(npc.getId());
                npc.destroy();
                count++;
            }
        }
        runtimeShopNpcIds.clear();
        plugin.getLogger().info("[BedWars] Deleted " + count + " orphaned shop NPCs on startup.");
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        for (NpcHologram hologram : holograms.values()) {
            hologram.clear();
        }
        holograms.clear();

        destroyRuntimeShopNpcs();
    }

    public void restartScheduler() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        if (shopSpawnTaskId != -1) {
            Bukkit.getScheduler().cancelTask(shopSpawnTaskId);
            shopSpawnTaskId = -1;
        }
        shopSpawnQueue.clear();
        start();
    }

    public NPC createSoloNpc(Player creator, String skinName) {
        return createQueueNpc(creator, BedWarsMode.ONE_VS_ONE, skinName);
    }

    public NPC createQueueNpc(Player creator, BedWarsMode mode, String skinName) {
        BedWarsMode npcMode = mode == null ? BedWarsMode.ONE_VS_ONE : mode;
        String fallbackName = npcMode == BedWarsMode.ONE_VS_ONE
            ? plugin.getConfig().getString("npc.solo.internal-name", "BedWars1v1")
            : npcMode.getNpcDefaultName();
        String npcName = fallbackName;
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, npcName);
        npc.setProtected(true);
        npc.data().setPersistent(DATA_TYPE, BedWarsNpcType.SOLO.name());
        npc.data().setPersistent(DATA_SKIN, skinName);
        npc.data().setPersistent(DATA_MODE, npcMode.getId());
        setMetadataIfPresent(npc, "NAMEPLATE_VISIBLE", false);
        setMetadataIfPresent(npc, "REMOVE_FROM_TABLIST", true);

        applyLookClose(npc);
        normalizeSoloNpcName(npc);
        applyStoredNpcSkin(npc);
        npc.spawn(creator.getLocation());
        normalizeSoloNpcName(npc);
        hideNameplate(npc);
        refreshNpcSkin(npc);
        updateHologram(npc);
        trackNpc(npc);
        return npc;
    }

    public boolean setSkin(int npcId, String skinName) {
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc == null || !isBedWarsNpc(npc)) {
            return false;
        }

        npc.data().setPersistent(DATA_SKIN, skinName);
        SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        skinTrait.clearTexture();
        skinTrait.setShouldUpdateSkins(true);
        skinTrait.setSkinName(skinName, true);
        refreshNpcSkin(npc);
        updateHologram(npc);
        return true;
    }

    public boolean removeNpc(int npcId) {
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc == null || !isBedWarsNpc(npc)) {
            return false;
        }

        removeHologram(npcId);
        removeRuntimeKey(npc);
        untrackNpcById(npcId);
        npc.destroy();
        return true;
    }

    public boolean isBedWarsNpc(NPC npc) {
        return npc != null && npc.data().has(DATA_TYPE);
    }

    private void trackNpc(NPC npc) {
        if (npc != null) {
            trackedBedwarsNpcIds.add(Integer.valueOf(npc.getId()));
        }
    }

    private void untrackNpc(NPC npc) {
        if (npc != null) {
            trackedBedwarsNpcIds.remove(Integer.valueOf(npc.getId()));
        }
    }

    private void untrackNpcById(int npcId) {
        trackedBedwarsNpcIds.remove(Integer.valueOf(npcId));
    }

    public boolean isSoloNpc(NPC npc) {
        return hasType(npc, BedWarsNpcType.SOLO);
    }

    public boolean isItemShopNpc(NPC npc) {
        return hasType(npc, BedWarsNpcType.ITEM_SHOP);
    }

    public boolean isUpgradeShopNpc(NPC npc) {
        return hasType(npc, BedWarsNpcType.UPGRADE_SHOP);
    }

    public BedWarsNpcType getNpcType(NPC npc) {
        if (!isBedWarsNpc(npc)) {
            return null;
        }

        Object rawType = npc.data().get(DATA_TYPE);
        if (rawType == null) {
            return null;
        }

        try {
            return BedWarsNpcType.valueOf(rawType.toString());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public String getArenaName(NPC npc) {
        if (npc == null) {
            return null;
        }

        Object rawArena = npc.data().get(DATA_ARENA);
        return rawArena == null ? null : rawArena.toString();
    }

    public TeamColor getTeamColor(NPC npc) {
        if (npc == null) {
            return null;
        }

        Object rawTeam = npc.data().get(DATA_TEAM);
        if (rawTeam == null) {
            return null;
        }

        try {
            return TeamColor.valueOf(rawTeam.toString());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public BedWarsMode getMode(NPC npc) {
        if (npc == null) {
            return BedWarsMode.ONE_VS_ONE;
        }

        Object rawMode = npc.data().get(DATA_MODE);
        BedWarsMode mode = BedWarsMode.fromInput(rawMode == null ? null : rawMode.toString());
        return mode == null ? BedWarsMode.ONE_VS_ONE : mode;
    }

    private final Map<Integer, String> lastTopText = new HashMap<Integer, String>();
    private final Map<Integer, String> lastBottomText = new HashMap<Integer, String>();
    private long lastFullRefresh;

    public void refreshVisuals() {
        refreshHologramConfigCache();
        long now = System.currentTimeMillis();
        boolean fullRefresh = (now - lastFullRefresh >= 5000L);
        if (fullRefresh) {
            lastFullRefresh = now;
        }

        java.util.Iterator<Integer> iterator = trackedBedwarsNpcIds.iterator();
        while (iterator.hasNext()) {
            int npcId = iterator.next().intValue();
            NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
            if (npc == null) {
                iterator.remove();
                removeHologram(npcId);
                lastTopText.remove(Integer.valueOf(npcId));
                lastBottomText.remove(Integer.valueOf(npcId));
                continue;
            }

            if (!npc.isSpawned()) {
                removeHologram(npcId);
                lastTopText.remove(Integer.valueOf(npcId));
                lastBottomText.remove(Integer.valueOf(npcId));
                continue;
            }

            if (usesHologram(npc)) {
                if (fullRefresh || isSoloNpc(npc)) {
                    updateHologramIfChanged(npc);
                }
            } else {
                removeHologram(npcId);
                if (fullRefresh && (isItemShopNpc(npc) || isUpgradeShopNpc(npc))) {
                    applyShopNpcAppearance(npc);
                }
            }
        }
    }

    private void updateHologramIfChanged(NPC npc) {
        if (npc == null || !npc.isSpawned() || !usesHologram(npc)) {
            return;
        }

        String top = getNpcTopText(npc);
        String bottom = getNpcBottomText(npc);
        int npcId = npc.getId();

        String cachedTop = lastTopText.get(npcId);
        String cachedBottom = lastBottomText.get(npcId);

        if (top.equals(cachedTop) && bottom.equals(cachedBottom)) {
            return;
        }

        lastTopText.put(npcId, top);
        lastBottomText.put(npcId, bottom);
        updateHologram(npc);
    }

    public void refreshArenaShopNpcs(Arena arena) {
        if (arena == null || !arena.hasActiveWorld()) {
            return;
        }

        clearPendingShopSpawns(arena);
        for (TeamColor color : plugin.getTeamManager().getActiveColors(arena)) {
            ArenaTeam team = arena.getTeam(color);
            if (team == null) {
                continue;
            }

            shopSpawnQueue.offer(new ShopSpawnRequest(
                arena,
                team,
                BedWarsNpcType.ITEM_SHOP,
                arena.getMatchLocation(team.getItemShopLocation())
            ));
            shopSpawnQueue.offer(new ShopSpawnRequest(
                arena,
                team,
                BedWarsNpcType.UPGRADE_SHOP,
                arena.getMatchLocation(team.getUpgradeShopLocation())
            ));
        }
        startShopSpawnQueue();
    }

    public void clearArenaShopNpcs(Arena arena) {
        if (arena == null) {
            return;
        }

        clearPendingShopSpawns(arena);
        for (TeamColor color : plugin.getTeamManager().getActiveColors(arena)) {
            removeRuntimeNpc(buildRuntimeKey(arena.getName(), color, BedWarsNpcType.ITEM_SHOP));
            removeRuntimeNpc(buildRuntimeKey(arena.getName(), color, BedWarsNpcType.UPGRADE_SHOP));
        }
    }

    public void updateHologram(NPC npc) {
        if (npc == null || !npc.isSpawned() || npc.getEntity() == null || !usesHologram(npc)) {
            return;
        }
        refreshHologramConfigCache();

        NpcHologram hologram = holograms.get(npc.getId());
        if (hologram == null) {
            hologram = createHologram(npc);
            holograms.put(npc.getId(), hologram);
        }

        String top = getNpcTopText(npc);
        String bottom = getNpcBottomText(npc);
        Location npcLocation = npc.getEntity().getLocation();

        if (hologram.size() < 2) {
            hologram.clear();
            hologram = createHologram(npc);
            holograms.put(npc.getId(), hologram);
        }

        hologram.setLineName(0, top);
        hologram.setLineName(1, bottom);
        hologram.teleportLine(0, npcLocation.clone().add(0.0D, getTopLineHeight(npc), 0.0D));
        hologram.teleportLine(1, npcLocation.clone().add(0.0D, getBottomLineHeight(npc), 0.0D));
    }

    public void removeHologram(int npcId) {
        NpcHologram hologram = holograms.remove(npcId);
        if (hologram != null) {
            hologram.clear();
        }
        lastTopText.remove(npcId);
        lastBottomText.remove(npcId);
    }

    private final List<String> cachedHiddenNpcNames = new ArrayList<String>();
    private long hiddenNamesCacheTime;

    public void applyHiddenNameTeam(Scoreboard scoreboard) {
        if (scoreboard == null) {
            return;
        }

        Team team = scoreboard.getTeam("bw_npc_hidden");
        if (team == null) {
            team = scoreboard.registerNewTeam("bw_npc_hidden");
            team.setNameTagVisibility(NameTagVisibility.NEVER);
        }

        long now = System.currentTimeMillis();
        if (now - hiddenNamesCacheTime > 5000L || cachedHiddenNpcNames.isEmpty()) {
            cachedHiddenNpcNames.clear();
            for (Integer npcId : trackedBedwarsNpcIds) {
                NPC npc = CitizensAPI.getNPCRegistry().getById(npcId.intValue());
                if (npc == null || !isSoloNpc(npc)) {
                    continue;
                }
                cachedHiddenNpcNames.add(npc.getName());
                if (npc.isSpawned() && npc.getEntity() != null) {
                    cachedHiddenNpcNames.add(npc.getEntity().getName());
                }
            }
            hiddenNamesCacheTime = now;
        }

        for (String name : cachedHiddenNpcNames) {
            addHiddenEntry(team, name);
        }
    }

    private void addHiddenEntry(Team team, String entry) {
        if (team == null || entry == null || entry.isEmpty()) {
            return;
        }

        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
    }

    public String getSkinName(NPC npc) {
        if (npc == null) {
            return plugin.getConfig().getString("npc.default-skin", "Steve");
        }

        Object rawSkin = npc.data().get(DATA_SKIN, plugin.getConfig().getString("npc.default-skin", "Steve"));
        return rawSkin == null ? plugin.getConfig().getString("npc.default-skin", "Steve") : rawSkin.toString();
    }

    public String formatNpcText(String text) {
        return formatNpcText(text, BedWarsMode.ONE_VS_ONE);
    }

    private int cachedPlayingCount = -1;
    private int cachedOnlineCount = -1;
    private long playingCountCacheTime;

    public String formatNpcText(String text, BedWarsMode mode) {
        if (text == null) {
            return "";
        }

        long now = System.currentTimeMillis();
        if (now - playingCountCacheTime >= 5000L || cachedPlayingCount < 0) {
            playingCountCacheTime = now;
            cachedPlayingCount = 0;
            for (Arena arena : plugin.getArenaManager().getArenas()) {
                cachedPlayingCount += arena.getPlayerCount();
            }
            cachedOnlineCount = Bukkit.getOnlinePlayers().size();
        }

        return text
            .replace("%mode%", mode == null ? BedWarsMode.ONE_VS_ONE.getDisplayName() : mode.getDisplayName())
            .replace("%playing%", String.valueOf(cachedPlayingCount))
            .replace("%online%", String.valueOf(cachedOnlineCount));
    }

    private boolean hasType(NPC npc, BedWarsNpcType type) {
        if (!isBedWarsNpc(npc) || type == null) {
            return false;
        }

        Object rawType = npc.data().get(DATA_TYPE);
        return rawType != null && type.name().equalsIgnoreCase(rawType.toString());
    }

    private boolean spawnOrReplaceShopNpc(Arena arena, ArenaTeam team, BedWarsNpcType type, Location rawLocation) {
        String key = buildRuntimeKey(arena.getName(), team.getColor(), type);
        Integer oldId = runtimeShopNpcIds.remove(key);
        if (oldId != null) {
            NPC oldNpc = CitizensAPI.getNPCRegistry().getById(oldId.intValue());
            if (oldNpc != null) {
                removeHologram(oldNpc.getId());
                untrackNpc(oldNpc);
                oldNpc.destroy();
            }
        }

        if (rawLocation == null || rawLocation.getWorld() == null) {
            return false;
        }

        Location spawnLocation = LocationUtil.npcSpawnLocation(rawLocation);
        Location teamSpawn = arena.getMatchLocation(team.getSpawnLocation());
        orientLocationToward(spawnLocation, teamSpawn);
        int chunkX = spawnLocation.getBlockX() >> 4;
        int chunkZ = spawnLocation.getBlockZ() >> 4;
        if (!spawnLocation.getWorld().isChunkLoaded(chunkX, chunkZ)) {
            spawnLocation.getWorld().loadChunk(chunkX, chunkZ);
        }
        if (!spawnLocation.getWorld().isChunkLoaded(chunkX, chunkZ)) {
            return false;
        }

        NPC npc = null;
        try {
            String displayName = getShopNpcDisplayName(type);
            npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, displayName);
            npc.setProtected(true);
            npc.data().setPersistent(DATA_TYPE, type.name());
            npc.data().setPersistent(DATA_ARENA, arena.getName());
            npc.data().setPersistent(DATA_TEAM, team.getColor().name());
            npc.data().setPersistent(DATA_SKIN, getShopNpcSkinName(type));
            setMetadataIfPresent(npc, "NAMEPLATE_VISIBLE",
                plugin.getConfig().getBoolean("npc.shop-nameplate-visible", true));
            setMetadataIfPresent(npc, "REMOVE_FROM_TABLIST", true);
            applyLookClose(npc);
            applyStoredNpcSkin(npc);
            if (!npc.spawn(spawnLocation) || npc.getEntity() == null) {
                npc.destroy();
                return false;
            }
            trackNpc(npc);
        } catch (RuntimeException exception) {
            if (npc != null) {
                npc.destroy();
            }
            return false;
        }

        applyShopNpcAppearance(npc);
        scheduleShopNpcStabilization(npc);
        runtimeShopNpcIds.put(key, npc.getId());
        return true;
    }

    private String getShopNpcDisplayName(BedWarsNpcType type) {
        String path = type == BedWarsNpcType.UPGRADE_SHOP
            ? "npc.upgrade-shop.name"
            : "npc.item-shop.name";
        String fallback = type == BedWarsNpcType.UPGRADE_SHOP
            ? "&a&lMELHORIAS"
            : "&b&lLOJA";
        return ChatUtil.color(plugin.getConfig().getString(path, fallback));
    }

    private String getShopNpcSkinName(BedWarsNpcType type) {
        String path = type == BedWarsNpcType.UPGRADE_SHOP
            ? "npc.upgrade-shop.skin"
            : "npc.item-shop.skin";
        String fallback = type == BedWarsNpcType.UPGRADE_SHOP ? "smhliv" : "_marlee1";
        String configured = plugin.getConfig().getString(path, fallback);
        return configured == null || configured.trim().isEmpty() ? fallback : configured.trim();
    }

    private void applyShopNpcAppearance(NPC npc) {
        if (npc == null || !npc.isSpawned() || npc.getEntity() == null
            || (!isItemShopNpc(npc) && !isUpgradeShopNpc(npc))) {
            return;
        }

        BedWarsNpcType type = getNpcType(npc);
        String displayName = getShopNpcDisplayName(type);
        boolean nameplateVisible = plugin.getConfig().getBoolean("npc.shop-nameplate-visible", true);
        if (!displayName.equals(npc.getName())) {
            npc.setName(displayName);
        }
        setMetadataIfPresent(npc, "NAMEPLATE_VISIBLE", nameplateVisible);

        faceShopNpcTowardTeamSpawn(npc);
    }

    private void faceShopNpcTowardTeamSpawn(NPC npc) {
        if (npc == null || !npc.isSpawned() || npc.getEntity() == null
            || !plugin.getConfig().getBoolean("npc.face-team-spawn", true)) {
            return;
        }

        Arena arena = plugin.getArenaManager().getArena(getArenaName(npc));
        TeamColor color = getTeamColor(npc);
        ArenaTeam team = arena == null || color == null ? null : arena.getTeam(color);
        Location target = team == null ? null : arena.getMatchLocation(team.getSpawnLocation());
        if (target == null || target.getWorld() == null
            || !target.getWorld().equals(npc.getEntity().getWorld())) {
            return;
        }

        Location horizontalTarget = target.clone();
        double eyeHeight = npc.getEntity() instanceof LivingEntity
            ? ((LivingEntity) npc.getEntity()).getEyeHeight()
            : 0.0D;
        horizontalTarget.setY(npc.getEntity().getLocation().getY() + eyeHeight);
        Location current = npc.getEntity().getLocation();
        Location desired = current.clone();
        orientLocationToward(desired, horizontalTarget);
        if (angularDistance(current.getYaw(), desired.getYaw()) > 2.0F
            || Math.abs(current.getPitch()) > 2.0F) {
            npc.faceLocation(horizontalTarget);
        }
    }

    private void orientLocationToward(Location source, Location target) {
        if (source == null || target == null || source.getWorld() == null || target.getWorld() == null
            || !source.getWorld().equals(target.getWorld())) {
            return;
        }

        double deltaX = target.getX() - source.getX();
        double deltaZ = target.getZ() - source.getZ();
        if ((deltaX * deltaX) + (deltaZ * deltaZ) < 0.0001D) {
            return;
        }

        source.setYaw((float) Math.toDegrees(Math.atan2(-deltaX, deltaZ)));
        source.setPitch(0.0F);
    }

    private float angularDistance(float first, float second) {
        float difference = Math.abs(first - second) % 360.0F;
        return difference > 180.0F ? 360.0F - difference : difference;
    }

    private void scheduleShopNpcStabilization(final NPC npc) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                applyShopNpcAppearance(npc);
            }
        }, 2L);
    }

    private void startShopSpawnQueue() {
        if (shopSpawnTaskId != -1 || shopSpawnQueue.isEmpty()) {
            return;
        }

        long initialDelay = Math.max(1L, plugin.getConfig().getLong("npc.shop-spawn-delay-ticks", 10L));
        shopSpawnTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                processShopSpawnQueue();
            }
        }, initialDelay, 1L);
    }

    private void processShopSpawnQueue() {
        int perTick = Math.max(1, Math.min(4, plugin.getConfig().getInt("npc.shop-spawns-per-tick", 1)));
        int processed = 0;
        while (processed < perTick && !shopSpawnQueue.isEmpty()) {
            ShopSpawnRequest request = shopSpawnQueue.poll();
            processShopSpawnRequest(request);
            processed++;
        }

        if (shopSpawnQueue.isEmpty() && shopSpawnTaskId != -1) {
            Bukkit.getScheduler().cancelTask(shopSpawnTaskId);
            shopSpawnTaskId = -1;
        }
    }

    private void processShopSpawnRequest(final ShopSpawnRequest request) {
        if (request == null
            || request.arena == null
            || request.team == null
            || plugin.getArenaManager().getArena(request.arena.getName()) != request.arena
            || request.arena.getState() != ArenaState.INGAME
            || !request.arena.hasActiveWorld()) {
            return;
        }

        if (spawnOrReplaceShopNpc(request.arena, request.team, request.type, request.location)) {
            return;
        }

        request.attempts++;
        int maxAttempts = Math.max(1, Math.min(5, plugin.getConfig().getInt("npc.shop-spawn-attempts", 3)));
        if (request.attempts >= maxAttempts) {
            plugin.getLogger().warning("Nao foi possivel criar o NPC " + request.type.name()
                + " do time " + request.team.getColor().name() + " na arena " + request.arena.getName() + ".");
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (plugin.getArenaManager().getArena(request.arena.getName()) == request.arena
                    && request.arena.getState() == ArenaState.INGAME
                    && request.arena.hasActiveWorld()) {
                    shopSpawnQueue.offer(request);
                    startShopSpawnQueue();
                }
            }
        }, 20L);
    }

    private void clearPendingShopSpawns(Arena arena) {
        if (arena == null || shopSpawnQueue.isEmpty()) {
            return;
        }

        java.util.Iterator<ShopSpawnRequest> iterator = shopSpawnQueue.iterator();
        while (iterator.hasNext()) {
            ShopSpawnRequest request = iterator.next();
            if (request != null && request.arena == arena) {
                iterator.remove();
            }
        }
    }

    private void cleanupRuntimeShopNpcs() {
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (isItemShopNpc(npc) || isUpgradeShopNpc(npc)) {
                removeHologram(npc.getId());
                untrackNpc(npc);
                npc.destroy();
            }
        }
        runtimeShopNpcIds.clear();
    }

    private void destroyRuntimeShopNpcs() {
        shopSpawnQueue.clear();
        for (Integer id : runtimeShopNpcIds.values()) {
            NPC npc = CitizensAPI.getNPCRegistry().getById(id.intValue());
            if (npc != null) {
                removeHologram(npc.getId());
                untrackNpc(npc);
                npc.destroy();
            }
        }
        runtimeShopNpcIds.clear();
    }

    private void removeRuntimeKey(NPC npc) {
        if (npc == null) {
            return;
        }

        String arenaName = npc.data().get(DATA_ARENA, "");
        String teamName = npc.data().get(DATA_TEAM, "");
        Object rawType = npc.data().get(DATA_TYPE);
        if (arenaName == null || teamName == null || rawType == null) {
            return;
        }

        try {
            TeamColor color = TeamColor.valueOf(teamName);
            BedWarsNpcType type = BedWarsNpcType.valueOf(rawType.toString());
            runtimeShopNpcIds.remove(buildRuntimeKey(arenaName, color, type));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void removeRuntimeNpc(String key) {
        Integer id = runtimeShopNpcIds.remove(key);
        if (id == null) {
            return;
        }

        NPC npc = CitizensAPI.getNPCRegistry().getById(id.intValue());
        if (npc != null) {
            removeHologram(npc.getId());
            untrackNpc(npc);
            npc.destroy();
        }
    }

    private String buildRuntimeKey(String arenaName, TeamColor color, BedWarsNpcType type) {
        return arenaName.toLowerCase() + ":" + color.name() + ":" + type.name();
    }

    private NpcHologram createHologram(NPC npc) {
        refreshHologramConfigCache();
        NpcHologram hologram = new NpcHologram();
        Location base = npc.getEntity().getLocation();
        hologram.addLine(spawnLine(base.clone().add(0.0D, getTopLineHeight(npc), 0.0D), getNpcTopText(npc)));
        hologram.addLine(spawnLine(base.clone().add(0.0D, getBottomLineHeight(npc), 0.0D), getNpcBottomText(npc)));
        return hologram;
    }

    private ArmorStand spawnLine(Location location, String text) {
        World world = location.getWorld();
        ArmorStand stand = (ArmorStand) world.spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setCustomNameVisible(true);
        stand.setCustomName(text);
        stand.setBasePlate(false);
        stand.setArms(false);
        return stand;
    }

    private void setMetadataIfPresent(NPC npc, String fieldName, Object value) {
        try {
            Object metadataKey = NPC.Metadata.class.getField(fieldName).get(null);
            if (metadataKey instanceof String) {
                npc.data().setPersistent((String) metadataKey, value);
            }
        } catch (Exception ignored) {
        }
    }

    private void applyLookClose(NPC npc) {
        boolean shopNpc = isItemShopNpc(npc) || isUpgradeShopNpc(npc);
        boolean enabled = shopNpc
            ? plugin.getConfig().getBoolean("npc.shop-look-close", false)
            : plugin.getConfig().getBoolean("npc.look-close", true);
        if (!enabled) {
            return;
        }
        LookClose lookClose = npc.getOrAddTrait(LookClose.class);
        lookClose.lookClose(true);
        lookClose.setRange(plugin.getConfig().getDouble("npc.look-close-range", 8.0D));
    }

    public void invalidateConfigurationCache() {
        hologramConfigCacheTime = 0L;
        lastFullRefresh = 0L;
        playingCountCacheTime = 0L;
        hiddenNamesCacheTime = 0L;
        cachedPlayingCount = -1;
        lastTopText.clear();
        lastBottomText.clear();
    }

    private void refreshNpcSkin(final NPC npc) {
        if (npc == null) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                respawnNpcVisuals(npc);
            }
        }, 20L);

        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                respawnNpcVisuals(npc);
            }
        }, 60L);
    }

    private void hideNameplate(NPC npc) {
        normalizeSoloNpcName(npc);
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        applyHiddenNameTeam(scoreboard);
    }

    private boolean usesHologram(NPC npc) {
        return isSoloNpc(npc);
    }

    private void refreshHologramConfigCache() {
        long now = System.currentTimeMillis();
        if (now - hologramConfigCacheTime < HOLOGRAM_CONFIG_TTL) {
            return;
        }
        hologramConfigCacheTime = now;
        cachedItemShopTopText = ChatUtil.color(plugin.getConfig().getString("npc.item-shop.hologram-top", "&b&lLOJA"));
        cachedItemShopBottomText = ChatUtil.color(plugin.getConfig().getString("npc.item-shop.hologram-bottom", "&eClique para abrir"));
        cachedItemShopTopHeight = plugin.getConfig().getDouble("npc.item-shop.hologram-top-height", 2.18D);
        cachedItemShopBottomHeight = plugin.getConfig().getDouble("npc.item-shop.hologram-bottom-height", 1.90D);
        cachedUpgradeShopTopText = ChatUtil.color(plugin.getConfig().getString("npc.upgrade-shop.hologram-top", "&b&lMELHORIAS"));
        cachedUpgradeShopBottomText = ChatUtil.color(plugin.getConfig().getString("npc.upgrade-shop.hologram-bottom", "&eClique para abrir"));
        cachedUpgradeShopTopHeight = plugin.getConfig().getDouble("npc.upgrade-shop.hologram-top-height", 2.18D);
        cachedUpgradeShopBottomHeight = plugin.getConfig().getDouble("npc.upgrade-shop.hologram-bottom-height", 1.90D);
        cachedSoloTopText = plugin.getConfig().getString("npc.solo.hologram-top", "&b&lBedWars - %mode%");
        cachedSoloBottomText = plugin.getConfig().getString("npc.solo.hologram-bottom", "&e%playing% jogando!");
        cachedSoloTopHeight = plugin.getConfig().getDouble("npc.solo.hologram-top-height", 2.00D);
        cachedSoloBottomHeight = plugin.getConfig().getDouble("npc.solo.hologram-bottom-height", 1.74D);
    }

    private String getNpcTopText(NPC npc) {
        refreshHologramConfigCache();
        if (isItemShopNpc(npc)) {
            return fallbackText(cachedItemShopTopText, "&b&lLOJA");
        }
        if (isUpgradeShopNpc(npc)) {
            return fallbackText(cachedUpgradeShopTopText, "&b&lMELHORIAS");
        }
        return ChatUtil.color(formatNpcText(
            fallbackText(cachedSoloTopText, "&b&lBedWars - %mode%"),
            getMode(npc)
        ));
    }

    private String getNpcBottomText(NPC npc) {
        refreshHologramConfigCache();
        if (isItemShopNpc(npc)) {
            return fallbackText(cachedItemShopBottomText, "&eClique para abrir");
        }
        if (isUpgradeShopNpc(npc)) {
            return fallbackText(cachedUpgradeShopBottomText, "&eClique para abrir");
        }
        return ChatUtil.color(formatNpcText(
            fallbackText(cachedSoloBottomText, "&e%playing% jogando!"),
            getMode(npc)
        ));
    }

    private String fallbackText(String text, String fallback) {
        return text == null ? ChatUtil.color(fallback) : text;
    }

    private double getTopLineHeight(NPC npc) {
        if (isItemShopNpc(npc)) {
            return cachedItemShopTopHeight;
        }
        if (isUpgradeShopNpc(npc)) {
            return cachedUpgradeShopTopHeight;
        }
        return cachedSoloTopHeight;
    }

    private double getBottomLineHeight(NPC npc) {
        if (isItemShopNpc(npc)) {
            return cachedItemShopBottomHeight;
        }
        if (isUpgradeShopNpc(npc)) {
            return cachedUpgradeShopBottomHeight;
        }
        return cachedSoloBottomHeight;
    }

    private void respawnNpcVisuals(NPC npc) {
        if (npc == null || !npc.isSpawned()) {
            return;
        }

        Location location = npc.getEntity().getLocation();
        npc.despawn();
        normalizeSoloNpcName(npc);
        applyStoredNpcSkin(npc);
        npc.spawn(location);
        if (isSoloNpc(npc)) {
            normalizeSoloNpcName(npc);
            applyStoredNpcSkin(npc);
            hideNameplate(npc);
        } else if (isItemShopNpc(npc) || isUpgradeShopNpc(npc)) {
            applyShopNpcAppearance(npc);
        }
        if (usesHologram(npc)) {
            updateHologram(npc);
        }
    }

    private void applyStoredNpcSkin(NPC npc) {
        if (npc == null || !isBedWarsNpc(npc)) {
            return;
        }

        String skinName = getSkinName(npc);
        if (skinName == null || skinName.trim().isEmpty()) {
            return;
        }

        SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        skinTrait.clearTexture();
        skinTrait.setShouldUpdateSkins(true);
        skinTrait.setSkinName(skinName, true);
    }

    private void normalizeSoloNpcName(NPC npc) {
        if (npc == null || !isSoloNpc(npc)) {
            return;
        }

        String hiddenName = ChatUtil.color("&r");
        if (!hiddenName.equals(npc.getName())) {
            npc.setName(hiddenName);
        }

        if (npc.isSpawned() && npc.getEntity() instanceof Player) {
            Player entity = (Player) npc.getEntity();
            entity.setCustomName(null);
            entity.setCustomNameVisible(false);
        }
    }
}

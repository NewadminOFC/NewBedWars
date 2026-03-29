package n.plugins.newbedwars.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
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
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
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
    private int taskId = -1;

    public NpcManager(NewBedWars plugin) {
        this.plugin = plugin;
        this.holograms = new HashMap<Integer, NpcHologram>();
        this.runtimeShopNpcIds = new HashMap<String, Integer>();
    }

    public void start() {
        if (taskId != -1) {
            return;
        }

        cleanupRuntimeShopNpcs();
        respawnAllArenaShopNpcs();

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                refreshVisuals();
            }
        }, 20L, 20L);
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

        SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        skinTrait.clearTexture();
        skinTrait.setShouldUpdateSkins(true);
        skinTrait.setSkinName(skinName, true);

        normalizeSoloNpcName(npc);
        npc.spawn(creator.getLocation());
        normalizeSoloNpcName(npc);
        hideNameplate(npc);
        refreshNpcSkin(npc);
        updateHologram(npc);
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
        npc.destroy();
        return true;
    }

    public boolean isBedWarsNpc(NPC npc) {
        return npc != null && npc.data().has(DATA_TYPE);
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

    public void refreshVisuals() {
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (!isBedWarsNpc(npc)) {
                continue;
            }

            if (!npc.isSpawned()) {
                removeHologram(npc.getId());
                continue;
            }

            if (usesHologram(npc)) {
                normalizeSoloNpcName(npc);
                updateHologram(npc);
            } else {
                removeHologram(npc.getId());
            }
        }
    }

    public void refreshArenaShopNpcs(Arena arena) {
        if (arena == null) {
            return;
        }

        for (TeamColor color : plugin.getTeamManager().getActiveColors(arena)) {
            ArenaTeam team = arena.getTeam(color);
            if (team == null) {
                continue;
            }

            spawnOrReplaceShopNpc(arena, team, BedWarsNpcType.ITEM_SHOP, arena.getMatchLocation(team.getItemShopLocation()));
            spawnOrReplaceShopNpc(arena, team, BedWarsNpcType.UPGRADE_SHOP, arena.getMatchLocation(team.getUpgradeShopLocation()));
        }
    }

    public void clearArenaShopNpcs(Arena arena) {
        if (arena == null) {
            return;
        }

        for (TeamColor color : plugin.getTeamManager().getActiveColors(arena)) {
            removeRuntimeNpc(buildRuntimeKey(arena.getName(), color, BedWarsNpcType.ITEM_SHOP));
            removeRuntimeNpc(buildRuntimeKey(arena.getName(), color, BedWarsNpcType.UPGRADE_SHOP));
        }
    }

    public void updateHologram(NPC npc) {
        if (npc == null || !npc.isSpawned() || !usesHologram(npc)) {
            return;
        }

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
    }

    public void applyHiddenNameTeam(Scoreboard scoreboard) {
        if (scoreboard == null) {
            return;
        }

        Team team = scoreboard.getTeam("bw_npc_hidden");
        if (team == null) {
            team = scoreboard.registerNewTeam("bw_npc_hidden");
            team.setNameTagVisibility(NameTagVisibility.NEVER);
        }

        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (!isSoloNpc(npc)) {
                continue;
            }

            if (npc.getName() == null || npc.getName().trim().isEmpty()) {
                continue;
            }

            if (!team.hasEntry(npc.getName())) {
                team.addEntry(npc.getName());
            }
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

    public String formatNpcText(String text, BedWarsMode mode) {
        int playing = 0;
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            if (mode != null && arena.getMode() != mode) {
                continue;
            }
            playing += arena.getPlayerCount();
        }

        return text
            .replace("%mode%", mode == null ? BedWarsMode.ONE_VS_ONE.getDisplayName() : mode.getDisplayName())
            .replace("%playing%", String.valueOf(playing))
            .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
    }

    private boolean hasType(NPC npc, BedWarsNpcType type) {
        if (!isBedWarsNpc(npc) || type == null) {
            return false;
        }

        Object rawType = npc.data().get(DATA_TYPE);
        return rawType != null && type.name().equalsIgnoreCase(rawType.toString());
    }

    private void respawnAllArenaShopNpcs() {
        for (Arena arena : plugin.getArenaManager().getConfiguredArenas()) {
            refreshArenaShopNpcs(arena);
        }
    }

    private void spawnOrReplaceShopNpc(Arena arena, ArenaTeam team, BedWarsNpcType type, Location rawLocation) {
        String key = buildRuntimeKey(arena.getName(), team.getColor(), type);
        Integer oldId = runtimeShopNpcIds.remove(key);
        if (oldId != null) {
            NPC oldNpc = CitizensAPI.getNPCRegistry().getById(oldId.intValue());
            if (oldNpc != null) {
                removeHologram(oldNpc.getId());
                oldNpc.destroy();
            }
        }

        if (rawLocation == null || rawLocation.getWorld() == null) {
            return;
        }

        Location spawnLocation = LocationUtil.npcSpawnLocation(rawLocation);
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER, " ");
        npc.setProtected(true);
        npc.data().setPersistent(DATA_TYPE, type.name());
        npc.data().setPersistent(DATA_ARENA, arena.getName());
        npc.data().setPersistent(DATA_TEAM, team.getColor().name());
        setMetadataIfPresent(npc, "NAMEPLATE_VISIBLE", false);
        applyLookClose(npc);
        npc.spawn(spawnLocation);

        if (npc.getEntity() instanceof Villager) {
            Villager villager = (Villager) npc.getEntity();
            villager.setProfession(type == BedWarsNpcType.ITEM_SHOP ? Villager.Profession.BLACKSMITH : Villager.Profession.LIBRARIAN);
            villager.setAdult();
            villager.setCustomName(null);
            villager.setCustomNameVisible(false);
            silenceVillager(villager);
            try {
                villager.getClass().getMethod("setRecipes", java.util.List.class).invoke(villager, new ArrayList());
            } catch (Exception ignored) {
            }
        }

        updateHologram(npc);
        runtimeShopNpcIds.put(key, npc.getId());
    }

    private void cleanupRuntimeShopNpcs() {
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (isItemShopNpc(npc) || isUpgradeShopNpc(npc)) {
                removeHologram(npc.getId());
                npc.destroy();
            }
        }
        runtimeShopNpcIds.clear();
    }

    private void destroyRuntimeShopNpcs() {
        for (Integer id : runtimeShopNpcIds.values()) {
            NPC npc = CitizensAPI.getNPCRegistry().getById(id.intValue());
            if (npc != null) {
                removeHologram(npc.getId());
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
            npc.destroy();
        }
    }

    private String buildRuntimeKey(String arenaName, TeamColor color, BedWarsNpcType type) {
        return arenaName.toLowerCase() + ":" + color.name() + ":" + type.name();
    }

    private NpcHologram createHologram(NPC npc) {
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
        LookClose lookClose = npc.getOrAddTrait(LookClose.class);
        lookClose.lookClose(plugin.getConfig().getBoolean("npc.look-close", true));
        lookClose.setRange(plugin.getConfig().getDouble("npc.look-close-range", 8.0D));
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyHiddenNameTeam(player.getScoreboard());
        }
    }

    private boolean usesHologram(NPC npc) {
        return isSoloNpc(npc) || isItemShopNpc(npc) || isUpgradeShopNpc(npc);
    }

    private String getNpcTopText(NPC npc) {
        if (isItemShopNpc(npc)) {
            return ChatUtil.color(plugin.getConfig().getString("npc.item-shop.hologram-top", "&b&lLOJA"));
        }
        if (isUpgradeShopNpc(npc)) {
            return ChatUtil.color(plugin.getConfig().getString("npc.upgrade-shop.hologram-top", "&b&lMELHORIAS"));
        }
        return ChatUtil.color(formatNpcText(plugin.getConfig().getString("npc.solo.hologram-top", "&b&lBedWars - %mode%"), getMode(npc)));
    }

    private String getNpcBottomText(NPC npc) {
        if (isItemShopNpc(npc)) {
            return ChatUtil.color(plugin.getConfig().getString("npc.item-shop.hologram-bottom", "&eClique para abrir"));
        }
        if (isUpgradeShopNpc(npc)) {
            return ChatUtil.color(plugin.getConfig().getString("npc.upgrade-shop.hologram-bottom", "&eClique para abrir"));
        }
        return ChatUtil.color(formatNpcText(plugin.getConfig().getString("npc.solo.hologram-bottom", "&e%playing% jogando!"), getMode(npc)));
    }

    private double getTopLineHeight(NPC npc) {
        if (isItemShopNpc(npc)) {
            return plugin.getConfig().getDouble("npc.item-shop.hologram-top-height", 2.18D);
        }
        if (isUpgradeShopNpc(npc)) {
            return plugin.getConfig().getDouble("npc.upgrade-shop.hologram-top-height", 2.18D);
        }
        return plugin.getConfig().getDouble("npc.solo.hologram-top-height", 2.00D);
    }

    private double getBottomLineHeight(NPC npc) {
        if (isItemShopNpc(npc)) {
            return plugin.getConfig().getDouble("npc.item-shop.hologram-bottom-height", 1.90D);
        }
        if (isUpgradeShopNpc(npc)) {
            return plugin.getConfig().getDouble("npc.upgrade-shop.hologram-bottom-height", 1.90D);
        }
        return plugin.getConfig().getDouble("npc.solo.hologram-bottom-height", 1.74D);
    }

    private void respawnNpcVisuals(NPC npc) {
        if (npc == null || !npc.isSpawned()) {
            return;
        }

        Location location = npc.getEntity().getLocation();
        npc.despawn();
        normalizeSoloNpcName(npc);
        npc.spawn(location);
        if (isSoloNpc(npc)) {
            normalizeSoloNpcName(npc);
            hideNameplate(npc);
        } else if (npc.getEntity() instanceof Villager) {
            Villager villager = (Villager) npc.getEntity();
            villager.setCustomName(null);
            villager.setCustomNameVisible(false);
            silenceVillager(villager);
        }
        if (usesHologram(npc)) {
            updateHologram(npc);
        }
    }

    private void silenceVillager(Villager villager) {
        if (villager == null) {
            return;
        }

        try {
            villager.getClass().getMethod("setSilent", boolean.class).invoke(villager, true);
            return;
        } catch (Exception ignored) {
        }

        try {
            Object handle = villager.getClass().getMethod("getHandle").invoke(villager);
            handle.getClass().getMethod("setSilent", boolean.class).invoke(handle, true);
            return;
        } catch (Exception ignored) {
        }

        try {
            Class<?> nbtTagCompoundClass = getNmsClass("NBTTagCompound");
            Object compound = nbtTagCompoundClass.newInstance();
            Object handle = villager.getClass().getMethod("getHandle").invoke(villager);
            handle.getClass().getMethod("c", nbtTagCompoundClass).invoke(handle, compound);
            nbtTagCompoundClass.getMethod("setBoolean", String.class, boolean.class).invoke(compound, "Silent", true);
            handle.getClass().getMethod("f", nbtTagCompoundClass).invoke(handle, compound);
        } catch (Exception ignored) {
        }
    }

    private Class<?> getNmsClass(String name) throws ClassNotFoundException {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        String version = packageName.substring(packageName.lastIndexOf('.') + 1);
        return Class.forName("net.minecraft.server." + version + "." + name);
    }

    private void normalizeSoloNpcName(NPC npc) {
        if (npc == null || !isSoloNpc(npc)) {
            return;
        }

        if (!" ".equals(npc.getName())) {
            npc.setName(" ");
        }

        if (npc.isSpawned() && npc.getEntity() instanceof Player) {
            Player entity = (Player) npc.getEntity();
            entity.setCustomName(null);
            entity.setCustomNameVisible(false);
        }
    }
}

package n.plugins.newbedwars.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaTeam;
import n.plugins.newbedwars.arena.BedData;
import n.plugins.newbedwars.arena.GeneratorPoint;
import n.plugins.newbedwars.arena.GeneratorType;
import n.plugins.newbedwars.arena.TeamColor;
import n.plugins.newbedwars.npc.BedWarsNpcType;
import n.plugins.newbedwars.npc.NpcHologram;
import n.plugins.newbedwars.setup.SetupPointAction;
import n.plugins.newbedwars.setup.SetupRegionAction;
import n.plugins.newbedwars.setup.SetupSession;
import n.plugins.newbedwars.util.BedUtil;
import n.plugins.newbedwars.util.ChatUtil;
import n.plugins.newbedwars.util.CuboidRegion;
import n.plugins.newbedwars.util.ItemBuilder;
import n.plugins.newbedwars.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class SetupManager {

    private static final String WAITING_SPAWN_ITEM = "\u00A7bSalvar spawn de espera";
    private static final String POSITION_ONE_ITEM = "\u00A7a/pos1";
    private static final String POSITION_TWO_ITEM = "\u00A7c/pos2";
    private static final String MENU_ITEM = "\u00A7aAbrir menu da arena";

    private final NewBedWars plugin;
    private final Map<UUID, SetupSession> sessions;
    private final Map<String, Map<String, NpcHologram>> arenaHolograms;

    public SetupManager(NewBedWars plugin) {
        this.plugin = plugin;
        this.sessions = new HashMap<UUID, SetupSession>();
        this.arenaHolograms = new HashMap<String, Map<String, NpcHologram>>();
    }

    public boolean isInSetup(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public SetupSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public void shutdown() {
        for (String arenaName : new ArrayList<String>(arenaHolograms.keySet())) {
            clearArenaHolograms(arenaName);
        }
    }

    public void startSession(Player player, Arena arena) {
        SetupSession existing = getSession(player);
        if (existing != null && existing.getArenaName().equalsIgnoreCase(arena.getName())) {
            teleportToArena(player, arena);
            giveItems(player, existing, arena);
            refreshArenaSetupVisuals(arena);
            if (existing.isUnlockedMainMenu()) {
                plugin.getMenuManager().openSetupMainMenu(player, arena);
            }
            plugin.getMessageManager().send(player, "setup.already-setting-up");
            return;
        }

        stopSession(player, false);
        SetupSession session = new SetupSession(
            player.getUniqueId(),
            arena.getName(),
            player.getInventory().getContents().clone(),
            player.getInventory().getArmorContents().clone()
        );
        session.setUnlockedMainMenu(hasWaitingSetup(arena));
        sessions.put(player.getUniqueId(), session);

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        teleportToArena(player, arena);
        giveItems(player, session, arena);
        refreshArenaSetupVisuals(arena);

        plugin.getMessageManager().send(player, "setup.session-started", Collections.singletonMap("arena", arena.getName()));
        plugin.getMessageManager().send(player, "setup.open-menu");
        if (session.isUnlockedMainMenu()) {
            plugin.getMenuManager().openSetupMainMenu(player, arena);
        }
    }

    public void stopSession(Player player, boolean notify) {
        SetupSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }

        player.closeInventory();
        player.getInventory().setContents(session.getOriginalContents());
        player.getInventory().setArmorContents(session.getOriginalArmor());
        player.updateInventory();

        Arena arena = plugin.getArenaManager().getConfiguredArena(session.getArenaName());
        if (arena != null) {
            refreshArenaSetupVisuals(arena);
        } else {
            clearArenaHolograms(session.getArenaName());
        }

        if (notify) {
            plugin.getMessageManager().send(player, "setup.session-ended");
        }
    }

    public void finishSession(Player player) {
        stopSession(player, true);
        if (!plugin.getLobbyManager().teleportToLobby(player)) {
            plugin.getMessageManager().send(player, "lobby.not-set");
        }
    }

    public void beginPointSetup(Player player, Arena arena, TeamColor color, SetupPointAction action) {
        SetupSession session = getSession(player);
        if (session == null || arena == null || color == null || action == null) {
            return;
        }

        session.setSelectedTeam(color);
        session.setPendingPointAction(action);
        session.setPendingRegionAction(null);
        session.setPendingRegionTeam(null);
        session.clearSelection();
        if (!action.isBlockRequired()) {
            handlePendingPoint(player, null);
            return;
        }
        player.closeInventory();
        plugin.getMessageManager().send(player, action.isBlockRequired() ? "setup.select-block" : "setup.select-location",
            Collections.singletonMap("action", action.getDisplayName()));
    }

    public void beginArenaPointSetup(Player player, Arena arena, SetupPointAction action) {
        SetupSession session = getSession(player);
        if (session == null || arena == null || action == null) {
            return;
        }

        session.setSelectedTeam(null);
        session.setPendingPointAction(action);
        session.setPendingRegionAction(null);
        session.setPendingRegionTeam(null);
        session.clearSelection();
        if (!action.isBlockRequired()) {
            handlePendingPoint(player, null);
            return;
        }
        player.closeInventory();
        plugin.getMessageManager().send(player, action.isBlockRequired() ? "setup.select-block" : "setup.select-location",
            Collections.singletonMap("action", action.getDisplayName()));
    }

    public void beginRegionSetup(Player player, Arena arena, TeamColor color, SetupRegionAction action) {
        SetupSession session = getSession(player);
        if (session == null || arena == null || color == null || action == null) {
            return;
        }

        session.setSelectedTeam(color);
        session.setPendingPointAction(null);
        session.setPendingRegionAction(action);
        session.setPendingRegionTeam(color);
        session.clearSelection();
        player.closeInventory();
        plugin.getMessageManager().send(player, "setup.region-selection-started",
            Collections.singletonMap("action", action.getDisplayName()));
    }

    public void beginArenaRegionSetup(Player player, Arena arena, SetupRegionAction action) {
        SetupSession session = getSession(player);
        if (session == null || arena == null || action == null) {
            return;
        }

        session.setSelectedTeam(null);
        session.setPendingPointAction(null);
        session.setPendingRegionAction(action);
        session.setPendingRegionTeam(null);
        session.clearSelection();
        player.closeInventory();
        plugin.getMessageManager().send(player, "setup.region-selection-started",
            Collections.singletonMap("action", action.getDisplayName()));
    }

    public boolean handleWaitingSpawnItem(Player player) {
        SetupSession session = getSession(player);
        if (session == null) {
            return false;
        }

        Arena arena = plugin.getArenaManager().getConfiguredArena(session.getArenaName());
        if (arena == null) {
            return false;
        }

        arena.setWaitingSpawn(player.getLocation());
        plugin.getArenaManager().saveArena(arena);
        refreshArenaSetupVisuals(arena);
        plugin.getMessageManager().send(player, "setup.waiting-spawn-saved");
        unlockMenuIfReady(player, session, arena);
        return true;
    }

    public boolean handleSelection(Player player, boolean firstPosition) {
        SetupSession session = getSession(player);
        if (session == null) {
            return false;
        }

        Arena arena = plugin.getArenaManager().getConfiguredArena(session.getArenaName());
        if (arena == null) {
            return false;
        }

        SetupRegionAction action = resolveRegionAction(session);
        if (action == null) {
            plugin.getMessageManager().send(player, "setup.region-not-selected");
            return true;
        }

        Location footLocation = getSelectionLocation(player);
        if (firstPosition) {
            session.setSelectionPos1(footLocation);
            plugin.getMessageManager().send(player, action == SetupRegionAction.WAITING_AREA ? "setup.waiting-pos1-set" : "setup.selection-pos1",
                Collections.singletonMap("action", action.getDisplayName()));
        } else {
            session.setSelectionPos2(footLocation);
            plugin.getMessageManager().send(player, action == SetupRegionAction.WAITING_AREA ? "setup.waiting-pos2-set" : "setup.selection-pos2",
                Collections.singletonMap("action", action.getDisplayName()));
        }

        if (session.getSelectionPos1() == null || session.getSelectionPos2() == null) {
            return true;
        }

        CuboidRegion region = new CuboidRegion(session.getSelectionPos1(), session.getSelectionPos2());
        if (action == SetupRegionAction.WAITING_AREA) {
            arena.setWaitingRegion(region);
            plugin.getArenaManager().saveArena(arena);
            refreshArenaSetupVisuals(arena);

            boolean editingFromMenu = session.getPendingRegionAction() == SetupRegionAction.WAITING_AREA;
            session.clearPendingActions();
            if (editingFromMenu) {
                plugin.getMessageManager().send(player, "setup.selection-complete", Collections.singletonMap("action", action.getDisplayName()));
                unlockMenuIfReady(player, session, arena);
                plugin.getMenuManager().openSetupMainMenu(player, arena);
            } else {
                plugin.getMessageManager().send(player, "setup.waiting-area-complete");
                unlockMenuIfReady(player, session, arena);
            }
            return true;
        }

        ArenaTeam team = arena.getTeam(session.getPendingRegionTeam());
        if (team == null) {
            return false;
        }

        boolean wasConfirmed = team.isConfirmed();
        if (action == SetupRegionAction.TEAM_ISLAND) {
            team.setIslandRegion(region);
        } else if (action == SetupRegionAction.TEAM_PROTECTION) {
            team.setProtectionRegion(region);
        }

        arena.setReady(false);
        plugin.getArenaManager().saveArena(arena);
        refreshArenaSetupVisuals(arena);
        session.clearPendingActions();
        plugin.getMessageManager().send(player, "setup.selection-complete", Collections.singletonMap("action", action.getDisplayName()));
        sendTeamChangedMessage(player, team, wasConfirmed);
        plugin.getMenuManager().openTeamSetupMenu(player, arena, team.getColor());
        return true;
    }

    public boolean handlePendingPoint(Player player, Block clickedBlock) {
        SetupSession session = getSession(player);
        if (session == null || session.getPendingPointAction() == null) {
            return false;
        }

        Arena arena = plugin.getArenaManager().getConfiguredArena(session.getArenaName());
        if (arena == null) {
            return false;
        }

        SetupPointAction action = session.getPendingPointAction();
        if (action.isBlockRequired() && clickedBlock == null) {
            return true;
        }

        if (action == SetupPointAction.ARENA_WAITING_SPAWN) {
            arena.setWaitingSpawn(player.getLocation());
            plugin.getArenaManager().saveArena(arena);
            refreshArenaSetupVisuals(arena);
            session.clearPendingActions();
            plugin.getMessageManager().send(player, "setup.point-saved", Collections.singletonMap("action", action.getDisplayName()));
            unlockMenuIfReady(player, session, arena);
            if (session.isUnlockedMainMenu()) {
                plugin.getMenuManager().openSetupMainMenu(player, arena);
            }
            return true;
        }

        if (action == SetupPointAction.ARENA_DIAMOND_GENERATOR) {
            arena.addGlobalGenerator(GeneratorType.DIAMOND, LocationUtil.generatorDropLocation(clickedBlock.getLocation()));
            plugin.getArenaManager().saveArena(arena);
            refreshArenaSetupVisuals(arena);
            session.clearPendingActions();
            plugin.getMessageManager().send(player, "setup.point-saved", Collections.singletonMap("action", action.getDisplayName()));
            plugin.getMenuManager().openSetupMainMenu(player, arena);
            return true;
        }

        if (action == SetupPointAction.ARENA_EMERALD_GENERATOR) {
            arena.addGlobalGenerator(GeneratorType.EMERALD, LocationUtil.generatorDropLocation(clickedBlock.getLocation()));
            plugin.getArenaManager().saveArena(arena);
            refreshArenaSetupVisuals(arena);
            session.clearPendingActions();
            plugin.getMessageManager().send(player, "setup.point-saved", Collections.singletonMap("action", action.getDisplayName()));
            plugin.getMenuManager().openSetupMainMenu(player, arena);
            return true;
        }

        if (session.getSelectedTeam() == null) {
            return false;
        }

        ArenaTeam team = arena.getTeam(session.getSelectedTeam());
        if (team == null) {
            return false;
        }

        boolean wasConfirmed = team.isConfirmed();
        if (action == SetupPointAction.TEAM_SPAWN) {
            team.setSpawnLocation(player.getLocation());
        } else if (action == SetupPointAction.TEAM_BED) {
            BedData bedData = clickedBlock == null ? null : BedUtil.resolveBedData(clickedBlock);
            if (bedData == null) {
                plugin.getMessageManager().send(player, "setup.invalid-bed");
                return true;
            }
            team.setBedData(bedData);
        } else if (action == SetupPointAction.TEAM_CHEST) {
            if (!isTeamChestBlock(clickedBlock)) {
                plugin.getMessageManager().send(player, "setup.invalid-team-chest");
                return true;
            }
            team.setTeamChestLocation(LocationUtil.centerBlock(clickedBlock.getLocation()));
        } else if (action == SetupPointAction.TEAM_ENDER_CHEST) {
            if (clickedBlock == null || clickedBlock.getType() != Material.ENDER_CHEST) {
                plugin.getMessageManager().send(player, "setup.invalid-ender-chest");
                return true;
            }
            team.setEnderChestLocation(LocationUtil.centerBlock(clickedBlock.getLocation()));
        } else if (action == SetupPointAction.TEAM_IRON_GENERATOR) {
            team.setSingleGenerator(GeneratorType.IRON, LocationUtil.generatorDropLocation(clickedBlock.getLocation()));
        } else if (action == SetupPointAction.TEAM_GOLD_GENERATOR) {
            team.setSingleGenerator(GeneratorType.GOLD, LocationUtil.generatorDropLocation(clickedBlock.getLocation()));
        } else if (action == SetupPointAction.TEAM_ITEM_SHOP) {
            team.setItemShopLocation(LocationUtil.centerBlock(clickedBlock.getLocation()));
        } else if (action == SetupPointAction.TEAM_UPGRADE_SHOP) {
            team.setUpgradeShopLocation(LocationUtil.centerBlock(clickedBlock.getLocation()));
        }

        arena.setReady(false);
        plugin.getArenaManager().saveArena(arena);
        refreshArenaSetupVisuals(arena);
        session.clearPendingActions();
        plugin.getMessageManager().send(player, "setup.point-saved", Collections.singletonMap("action", action.getDisplayName()));
        if (action == SetupPointAction.TEAM_ITEM_SHOP || action == SetupPointAction.TEAM_UPGRADE_SHOP) {
            plugin.getNpcManager().refreshArenaShopNpcs(arena);
        }
        sendTeamChangedMessage(player, team, wasConfirmed);
        plugin.getMenuManager().openTeamSetupMenu(player, arena, team.getColor());
        return true;
    }

    public boolean isWaitingSpawnItem(ItemStack itemStack) {
        return hasName(itemStack, WAITING_SPAWN_ITEM);
    }

    public boolean isPositionOneItem(ItemStack itemStack) {
        return hasName(itemStack, POSITION_ONE_ITEM);
    }

    public boolean isPositionTwoItem(ItemStack itemStack) {
        return hasName(itemStack, POSITION_TWO_ITEM);
    }

    public boolean isMenuItem(ItemStack itemStack) {
        return hasName(itemStack, MENU_ITEM);
    }

    public void openMainMenu(Player player) {
        SetupSession session = getSession(player);
        if (session == null) {
            return;
        }

        Arena arena = plugin.getArenaManager().getConfiguredArena(session.getArenaName());
        if (arena != null && session.isUnlockedMainMenu()) {
            plugin.getMenuManager().openSetupMainMenu(player, arena);
        }
    }

    public boolean canEditNpc(Player player, Arena arena) {
        SetupSession session = getSession(player);
        return session != null && arena != null && session.getArenaName().equalsIgnoreCase(arena.getName());
    }

    public void removeTeamNpc(Player player, Arena arena, TeamColor color, BedWarsNpcType type) {
        if (player == null || arena == null || color == null || type == null) {
            return;
        }

        ArenaTeam team = arena.getTeam(color);
        if (team == null) {
            return;
        }

        boolean changed = false;
        boolean wasConfirmed = team.isConfirmed();
        if (type == BedWarsNpcType.ITEM_SHOP && team.getItemShopLocation() != null) {
            team.setItemShopLocation(null);
            changed = true;
        } else if (type == BedWarsNpcType.UPGRADE_SHOP && team.getUpgradeShopLocation() != null) {
            team.setUpgradeShopLocation(null);
            changed = true;
        }

        if (!changed) {
            plugin.getMenuManager().openTeamSetupMenu(player, arena, color);
            return;
        }

        arena.setReady(false);
        plugin.getArenaManager().saveArena(arena);
        plugin.getNpcManager().refreshArenaShopNpcs(arena);
        refreshArenaSetupVisuals(arena);
        player.sendMessage(plugin.getMessageManager().get("prefix") + ChatUtil.color("&eNPC de &f" + type.getDisplayName() + " &eremovido do time " + color.getColoredName() + "&e."));
        sendTeamChangedMessage(player, team, wasConfirmed);
        plugin.getMenuManager().openTeamSetupMenu(player, arena, color);
    }

    public void clearArenaPoint(Player player, Arena arena, SetupPointAction action) {
        if (player == null || arena == null || action == null) {
            return;
        }

        boolean changed = false;
        if (action == SetupPointAction.ARENA_WAITING_SPAWN && arena.getWaitingSpawn() != null) {
            arena.setWaitingSpawn(null);
            changed = true;
        } else if (action == SetupPointAction.ARENA_DIAMOND_GENERATOR && !arena.getGlobalGenerators(GeneratorType.DIAMOND).isEmpty()) {
            arena.clearGlobalGenerators(GeneratorType.DIAMOND);
            changed = true;
        } else if (action == SetupPointAction.ARENA_EMERALD_GENERATOR && !arena.getGlobalGenerators(GeneratorType.EMERALD).isEmpty()) {
            arena.clearGlobalGenerators(GeneratorType.EMERALD);
            changed = true;
        }

        if (!changed) {
            plugin.getMessageManager().send(player, "setup.nothing-to-clear", Collections.singletonMap("action", action.getDisplayName()));
            plugin.getMenuManager().openSetupMainMenu(player, arena);
            return;
        }

        arena.setReady(false);
        plugin.getArenaManager().saveArena(arena);
        refreshArenaSetupVisuals(arena);
        plugin.getMessageManager().send(player, "setup.cleared", Collections.singletonMap("action", action.getDisplayName()));
        plugin.getMenuManager().openSetupMainMenu(player, arena);
    }

    public void clearArenaRegion(Player player, Arena arena, SetupRegionAction action) {
        if (player == null || arena == null || action == null) {
            return;
        }

        boolean changed = false;
        if (action == SetupRegionAction.WAITING_AREA && arena.getWaitingRegion() != null) {
            arena.setWaitingRegion(null);
            changed = true;
        }

        if (!changed) {
            plugin.getMessageManager().send(player, "setup.nothing-to-clear", Collections.singletonMap("action", action.getDisplayName()));
            plugin.getMenuManager().openSetupMainMenu(player, arena);
            return;
        }

        arena.setReady(false);
        plugin.getArenaManager().saveArena(arena);
        refreshArenaSetupVisuals(arena);
        plugin.getMessageManager().send(player, "setup.cleared", Collections.singletonMap("action", action.getDisplayName()));
        plugin.getMenuManager().openSetupMainMenu(player, arena);
    }

    public void clearTeamPoint(Player player, Arena arena, TeamColor color, SetupPointAction action) {
        if (player == null || arena == null || color == null || action == null) {
            return;
        }

        ArenaTeam team = arena.getTeam(color);
        if (team == null) {
            return;
        }

        boolean changed = false;
        boolean wasConfirmed = team.isConfirmed();
        if (action == SetupPointAction.TEAM_SPAWN && team.getSpawnLocation() != null) {
            team.setSpawnLocation(null);
            changed = true;
        } else if (action == SetupPointAction.TEAM_BED && team.getBedData() != null) {
            team.setBedData(null);
            changed = true;
        } else if (action == SetupPointAction.TEAM_CHEST && team.getTeamChestLocation() != null) {
            team.setTeamChestLocation(null);
            changed = true;
        } else if (action == SetupPointAction.TEAM_ENDER_CHEST && team.getEnderChestLocation() != null) {
            team.setEnderChestLocation(null);
            changed = true;
        } else if (action == SetupPointAction.TEAM_IRON_GENERATOR && !team.getGenerators(GeneratorType.IRON).isEmpty()) {
            team.setSingleGenerator(GeneratorType.IRON, null);
            changed = true;
        } else if (action == SetupPointAction.TEAM_GOLD_GENERATOR && !team.getGenerators(GeneratorType.GOLD).isEmpty()) {
            team.setSingleGenerator(GeneratorType.GOLD, null);
            changed = true;
        } else if (action == SetupPointAction.TEAM_ITEM_SHOP && team.getItemShopLocation() != null) {
            team.setItemShopLocation(null);
            changed = true;
        } else if (action == SetupPointAction.TEAM_UPGRADE_SHOP && team.getUpgradeShopLocation() != null) {
            team.setUpgradeShopLocation(null);
            changed = true;
        }

        if (!changed) {
            plugin.getMessageManager().send(player, "setup.nothing-to-clear", Collections.singletonMap("action", action.getDisplayName()));
            plugin.getMenuManager().openTeamSetupMenu(player, arena, color);
            return;
        }

        arena.setReady(false);
        plugin.getArenaManager().saveArena(arena);
        if (action == SetupPointAction.TEAM_ITEM_SHOP || action == SetupPointAction.TEAM_UPGRADE_SHOP) {
            plugin.getNpcManager().refreshArenaShopNpcs(arena);
        }
        refreshArenaSetupVisuals(arena);
        plugin.getMessageManager().send(player, "setup.cleared", Collections.singletonMap("action", action.getDisplayName()));
        sendTeamChangedMessage(player, team, wasConfirmed);
        plugin.getMenuManager().openTeamSetupMenu(player, arena, color);
    }

    public void clearTeamRegion(Player player, Arena arena, TeamColor color, SetupRegionAction action) {
        if (player == null || arena == null || color == null || action == null) {
            return;
        }

        ArenaTeam team = arena.getTeam(color);
        if (team == null) {
            return;
        }

        boolean changed = false;
        boolean wasConfirmed = team.isConfirmed();
        if (action == SetupRegionAction.TEAM_ISLAND && team.getIslandRegion() != null) {
            team.setIslandRegion(null);
            changed = true;
        } else if (action == SetupRegionAction.TEAM_PROTECTION && team.getProtectionRegion() != null) {
            team.setProtectionRegion(null);
            changed = true;
        }

        if (!changed) {
            plugin.getMessageManager().send(player, "setup.nothing-to-clear", Collections.singletonMap("action", action.getDisplayName()));
            plugin.getMenuManager().openTeamSetupMenu(player, arena, color);
            return;
        }

        arena.setReady(false);
        plugin.getArenaManager().saveArena(arena);
        refreshArenaSetupVisuals(arena);
        plugin.getMessageManager().send(player, "setup.cleared", Collections.singletonMap("action", action.getDisplayName()));
        sendTeamChangedMessage(player, team, wasConfirmed);
        plugin.getMenuManager().openTeamSetupMenu(player, arena, color);
    }

    public void refreshArenaSetupVisuals(Arena arena) {
        if (arena == null) {
            return;
        }

        String arenaKey = arena.getName().toLowerCase();
        clearArenaHolograms(arenaKey);
        if (!plugin.getConfig().getBoolean("settings.setup-holograms", true) || !hasActiveViewer(arena.getName())) {
            return;
        }

        Map<String, NpcHologram> markers = new LinkedHashMap<String, NpcHologram>();
        addPointMarker(markers, "waiting-spawn", arena.getWaitingSpawn(), "&bSpawn de espera");
        addRegionMarkers(markers, "waiting-area", arena.getWaitingRegion(), "&eSala de espera");

        for (TeamColor color : plugin.getTeamManager().getActiveColors()) {
            ArenaTeam team = arena.getTeam(color);
            if (team == null) {
                continue;
            }

            String teamName = color.getColoredName();
            addPointMarker(markers, "team-spawn-" + color.name(), team.getSpawnLocation(), "&bSpawn " + teamName);
            addPointMarker(markers, "team-bed-" + color.name(), team.getBedData() == null ? null : team.getBedData().getHead(), "&cCama " + teamName);
            addPointMarker(markers, "team-chest-" + color.name(), team.getTeamChestLocation(), "&6Bau " + teamName);
            addPointMarker(markers, "team-ender-" + color.name(), team.getEnderChestLocation(), "&5Ender chest " + teamName);
            addPointMarker(markers, "team-item-shop-" + color.name(), team.getItemShopLocation(), "&eLoja " + teamName);
            addPointMarker(markers, "team-upgrade-shop-" + color.name(), team.getUpgradeShopLocation(), "&bMelhorias " + teamName);
            addRegionMarkers(markers, "team-island-" + color.name(), team.getIslandRegion(), "&aIlha " + teamName);
            addRegionMarkers(markers, "team-protection-" + color.name(), team.getProtectionRegion(), "&5Protecao " + teamName);
            addGeneratorMarkers(markers, "team-iron-" + color.name(), team.getGenerators(GeneratorType.IRON), "&fFerro " + teamName);
            addGeneratorMarkers(markers, "team-gold-" + color.name(), team.getGenerators(GeneratorType.GOLD), "&6Ouro " + teamName);
        }

        addGeneratorMarkers(markers, "global-diamond", arena.getGlobalGenerators(GeneratorType.DIAMOND), "&bDiamante");
        addGeneratorMarkers(markers, "global-emerald", arena.getGlobalGenerators(GeneratorType.EMERALD), "&aEsmeralda");
        if (!markers.isEmpty()) {
            arenaHolograms.put(arenaKey, markers);
        }
    }

    public void clearArenaSetupVisuals(Arena arena) {
        if (arena == null) {
            return;
        }
        clearArenaHolograms(arena.getName());
        if (!arena.getTemplateName().equalsIgnoreCase(arena.getName())) {
            clearArenaHolograms(arena.getTemplateName());
        }
    }

    private boolean hasName(ItemStack itemStack, String expected) {
        return itemStack != null
            && itemStack.hasItemMeta()
            && itemStack.getItemMeta().hasDisplayName()
            && expected.equals(itemStack.getItemMeta().getDisplayName());
    }

    private void unlockMenuIfReady(Player player, SetupSession session, Arena arena) {
        boolean unlocked = hasWaitingSetup(arena);
        if (!unlocked) {
            return;
        }

        if (!session.isUnlockedMainMenu()) {
            session.setUnlockedMainMenu(true);
            giveMenuItem(player);
            plugin.getMessageManager().send(player, "setup.menu-unlocked");
            plugin.getMenuManager().openSetupMainMenu(player, arena);
        }
    }

    private void giveItems(Player player, SetupSession session, Arena arena) {
        player.getInventory().setItem(0, new ItemBuilder(Material.NETHER_STAR)
            .name("&bSalvar spawn de espera")
            .lore(
                "&7Clique com botao direito",
                "&7para salvar o spawn de espera"
            ).build());
        player.getInventory().setItem(1, new ItemBuilder(Material.SLIME_BALL)
            .name("&a/pos1")
            .lore(
                "&7Va ate o comeco da area",
                "&7e clique com botao direito",
                "&7para marcar a primeira",
                "&7posicao nos seus pes."
            ).build());
        player.getInventory().setItem(2, new ItemBuilder(Material.MAGMA_CREAM)
            .name("&c/pos2")
            .lore(
                "&7Va ate o final da area",
                "&7e clique com botao direito",
                "&7para marcar a segunda",
                "&7posicao nos seus pes."
            ).build());

        if (session.isUnlockedMainMenu() || hasWaitingSetup(arena)) {
            session.setUnlockedMainMenu(true);
            giveMenuItem(player);
        }

        player.updateInventory();
    }

    private void giveMenuItem(Player player) {
        player.getInventory().setItem(8, new ItemBuilder(Material.COMPASS)
            .name("&aAbrir menu da arena")
            .lore("&7Clique com botao direito", "&7para abrir o setup principal.").build());
        player.updateInventory();
    }

    private void teleportToArena(Player player, Arena arena) {
        if (arena == null) {
            return;
        }

        Location target = arena.getWaitingSpawn();
        if (target != null && target.getWorld() != null) {
            teleportSafely(player, target);
            return;
        }

        World world = arena.getWorld();
        if (world != null) {
            teleportSafely(player, world.getSpawnLocation());
        }
    }

    private void teleportSafely(final Player player, final Location target) {
        if (player == null || target == null || target.getWorld() == null) {
            return;
        }

        target.getChunk().load();
        player.teleport(target);
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
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

    private boolean hasWaitingSetup(Arena arena) {
        return arena != null && arena.getWaitingSpawn() != null && arena.getWaitingRegion() != null && arena.getWaitingRegion().isComplete();
    }

    private SetupRegionAction resolveRegionAction(SetupSession session) {
        if (session == null) {
            return null;
        }

        if (session.getPendingRegionAction() != null) {
            return session.getPendingRegionAction();
        }

        return session.isUnlockedMainMenu() ? null : SetupRegionAction.WAITING_AREA;
    }

    private Location getSelectionLocation(Player player) {
        Location location = player.getLocation();
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), 0.0F, 0.0F);
    }

    private void sendTeamChangedMessage(Player player, ArenaTeam team, boolean wasConfirmed) {
        if (player == null || team == null || !wasConfirmed) {
            return;
        }
        plugin.getMessageManager().send(player, "setup.changed-team-setting", Collections.singletonMap("team", team.getColor().getColoredName()));
    }

    private boolean isTeamChestBlock(Block clickedBlock) {
        return clickedBlock != null && (clickedBlock.getType() == Material.CHEST || clickedBlock.getType() == Material.TRAPPED_CHEST);
    }

    private boolean hasActiveViewer(String arenaName) {
        if (arenaName == null) {
            return false;
        }

        for (SetupSession session : sessions.values()) {
            if (arenaName.equalsIgnoreCase(session.getArenaName())) {
                return true;
            }
        }
        return false;
    }

    private void clearArenaHolograms(String arenaName) {
        if (arenaName == null) {
            return;
        }

        Map<String, NpcHologram> markers = arenaHolograms.remove(arenaName.toLowerCase());
        if (markers == null) {
            return;
        }

        for (NpcHologram hologram : markers.values()) {
            hologram.clear();
        }
    }

    private void addGeneratorMarkers(Map<String, NpcHologram> markers, String keyPrefix, List<GeneratorPoint> points, String label) {
        if (points == null) {
            return;
        }

        for (int index = 0; index < points.size(); index++) {
            GeneratorPoint point = points.get(index);
            String text = points.size() > 1 ? label + " &7#" + (index + 1) : label;
            addPointMarker(markers, keyPrefix + "-" + index, point == null ? null : point.getLocation(), text);
        }
    }

    private void addRegionMarkers(Map<String, NpcHologram> markers, String keyPrefix, CuboidRegion region, String label) {
        if (region == null || !region.isComplete()) {
            return;
        }

        addPointMarker(markers, keyPrefix + "-pos1", region.getPos1(), label + " &7(Pos1)");
        addPointMarker(markers, keyPrefix + "-pos2", region.getPos2(), label + " &7(Pos2)");
    }

    private void addPointMarker(Map<String, NpcHologram> markers, String key, Location location, String text) {
        if (markers == null || key == null || location == null || location.getWorld() == null) {
            return;
        }

        NpcHologram hologram = new NpcHologram();
        hologram.addLine(spawnLine(markerLocation(location), ChatUtil.color(text)));
        markers.put(key, hologram);
    }

    private Location markerLocation(Location location) {
        return LocationUtil.topCenter(location).add(0.0D, 0.65D, 0.0D);
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

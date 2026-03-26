package n.plugins.newbedwars.manager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaTeam;
import n.plugins.newbedwars.arena.BedData;
import n.plugins.newbedwars.arena.GeneratorType;
import n.plugins.newbedwars.arena.TeamColor;
import n.plugins.newbedwars.setup.SetupPointAction;
import n.plugins.newbedwars.setup.SetupRegionAction;
import n.plugins.newbedwars.setup.SetupSession;
import n.plugins.newbedwars.util.BedUtil;
import n.plugins.newbedwars.util.CuboidRegion;
import n.plugins.newbedwars.util.ItemBuilder;
import n.plugins.newbedwars.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class SetupManager {

    private static final String WAITING_SPAWN_ITEM = "§bSalvar spawn de espera";
    private static final String SELECTION_WAND_ITEM = "§eSelecionar area";
    private static final String MENU_ITEM = "§aAbrir menu da arena";

    private final NewBedWars plugin;
    private final Map<UUID, SetupSession> sessions;

    public SetupManager(NewBedWars plugin) {
        this.plugin = plugin;
        this.sessions = new HashMap<UUID, SetupSession>();
    }

    public boolean isInSetup(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public SetupSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public void startSession(Player player, Arena arena) {
        SetupSession existing = getSession(player);
        if (existing != null && existing.getArenaName().equalsIgnoreCase(arena.getName())) {
            teleportToArena(player, arena);
            if (existing.isUnlockedMainMenu()) {
                plugin.getMenuManager().openSetupMainMenu(player, arena);
            } else {
                giveItems(player, existing, arena);
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
        session.setUnlockedMainMenu(arena.getWaitingSpawn() != null && arena.getWaitingRegion() != null && arena.getWaitingRegion().isComplete());
        sessions.put(player.getUniqueId(), session);

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        teleportToArena(player, arena);
        giveItems(player, session, arena);

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
        if (session == null) {
            return;
        }

        session.setSelectedTeam(color);
        session.setPendingPointAction(action);
        session.setPendingRegionAction(null);
        session.setPendingRegionTeam(null);
        session.clearSelection();
        player.closeInventory();
        plugin.getMessageManager().send(player, action.isBlockRequired() ? "setup.select-block" : "setup.select-location",
            Collections.singletonMap("action", action.getDisplayName()));
    }

    public void beginArenaPointSetup(Player player, Arena arena, SetupPointAction action) {
        SetupSession session = getSession(player);
        if (session == null) {
            return;
        }

        session.setSelectedTeam(null);
        session.setPendingPointAction(action);
        session.setPendingRegionAction(null);
        session.setPendingRegionTeam(null);
        session.clearSelection();
        player.closeInventory();
        plugin.getMessageManager().send(player, action.isBlockRequired() ? "setup.select-block" : "setup.select-location",
            Collections.singletonMap("action", action.getDisplayName()));
    }

    public void beginRegionSetup(Player player, Arena arena, TeamColor color, SetupRegionAction action) {
        SetupSession session = getSession(player);
        if (session == null) {
            return;
        }

        session.setSelectedTeam(color);
        session.setPendingPointAction(null);
        session.setPendingRegionAction(action);
        session.setPendingRegionTeam(color);
        session.clearSelection();
        player.closeInventory();
        plugin.getMessageManager().send(player, "setup.select-block", Collections.singletonMap("action", action.getDisplayName()));
    }

    public boolean handleWaitingSpawnItem(Player player) {
        SetupSession session = getSession(player);
        if (session == null) {
            return false;
        }

        Arena arena = plugin.getArenaManager().getArena(session.getArenaName());
        if (arena == null) {
            return false;
        }

        arena.setWaitingSpawn(player.getLocation());
        plugin.getArenaManager().saveArena(arena);
        plugin.getMessageManager().send(player, "setup.waiting-spawn-saved");
        unlockMenuIfReady(player, session, arena);
        return true;
    }

    public boolean handleSelection(Player player, Block block, boolean firstPosition) {
        SetupSession session = getSession(player);
        if (session == null || block == null) {
            return false;
        }

        Arena arena = plugin.getArenaManager().getArena(session.getArenaName());
        if (arena == null) {
            return false;
        }

        SetupRegionAction action = session.getPendingRegionAction() == null ? SetupRegionAction.WAITING_AREA : session.getPendingRegionAction();
        if (firstPosition) {
            session.setSelectionPos1(block.getLocation());
            plugin.getMessageManager().send(player, action == SetupRegionAction.WAITING_AREA ? "setup.waiting-pos1-set" : "setup.selection-pos1",
                Collections.singletonMap("action", action.getDisplayName()));
        } else {
            session.setSelectionPos2(block.getLocation());
            plugin.getMessageManager().send(player, action == SetupRegionAction.WAITING_AREA ? "setup.waiting-pos2-set" : "setup.selection-pos2",
                Collections.singletonMap("action", action.getDisplayName()));
        }

        if (session.getSelectionPos1() == null || session.getSelectionPos2() == null) {
            return true;
        }

        // A mesma ferramenta de selecao serve para lobby, ilha e protecao inicial.
        CuboidRegion region = new CuboidRegion(session.getSelectionPos1(), session.getSelectionPos2());
        if (action == SetupRegionAction.WAITING_AREA && session.getPendingRegionAction() == null) {
            arena.setWaitingRegion(region);
            plugin.getArenaManager().saveArena(arena);
            plugin.getMessageManager().send(player, "setup.waiting-area-complete");
            session.clearSelection();
            unlockMenuIfReady(player, session, arena);
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
        session.clearPendingActions();
        plugin.getMessageManager().send(player, "setup.selection-complete", Collections.singletonMap("action", action.getDisplayName()));
        if (wasConfirmed) {
            plugin.getMessageManager().send(player, "setup.changed-team-setting", Collections.singletonMap("team", team.getColor().getColoredName()));
        }
        plugin.getMenuManager().openTeamSetupMenu(player, arena, team.getColor());
        return true;
    }

    public boolean handlePendingPoint(Player player, Block clickedBlock) {
        SetupSession session = getSession(player);
        if (session == null || session.getPendingPointAction() == null) {
            return false;
        }

        Arena arena = plugin.getArenaManager().getArena(session.getArenaName());
        if (arena == null) {
            return false;
        }

        SetupPointAction action = session.getPendingPointAction();
        if (action.isBlockRequired() && clickedBlock == null) {
            return true;
        }

        if (action == SetupPointAction.ARENA_DIAMOND_GENERATOR) {
            arena.addGlobalGenerator(GeneratorType.DIAMOND, LocationUtil.generatorDropLocation(clickedBlock.getLocation()));
            plugin.getArenaManager().saveArena(arena);
            session.clearPendingActions();
            plugin.getMessageManager().send(player, "setup.point-saved", Collections.singletonMap("action", action.getDisplayName()));
            plugin.getMenuManager().openSetupMainMenu(player, arena);
            return true;
        }

        if (action == SetupPointAction.ARENA_EMERALD_GENERATOR) {
            arena.addGlobalGenerator(GeneratorType.EMERALD, LocationUtil.generatorDropLocation(clickedBlock.getLocation()));
            plugin.getArenaManager().saveArena(arena);
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
        session.clearPendingActions();
        plugin.getMessageManager().send(player, "setup.point-saved", Collections.singletonMap("action", action.getDisplayName()));
        if (action == SetupPointAction.TEAM_ITEM_SHOP || action == SetupPointAction.TEAM_UPGRADE_SHOP) {
            plugin.getNpcManager().refreshArenaShopNpcs(arena);
        }
        if (wasConfirmed) {
            plugin.getMessageManager().send(player, "setup.changed-team-setting", Collections.singletonMap("team", team.getColor().getColoredName()));
        }
        plugin.getMenuManager().openTeamSetupMenu(player, arena, team.getColor());
        return true;
    }

    public boolean isWaitingSpawnItem(ItemStack itemStack) {
        return hasName(itemStack, WAITING_SPAWN_ITEM);
    }

    public boolean isSelectionWand(ItemStack itemStack) {
        return hasName(itemStack, SELECTION_WAND_ITEM);
    }

    public boolean isMenuItem(ItemStack itemStack) {
        return hasName(itemStack, MENU_ITEM);
    }

    public void openMainMenu(Player player) {
        SetupSession session = getSession(player);
        if (session == null) {
            return;
        }

        Arena arena = plugin.getArenaManager().getArena(session.getArenaName());
        if (arena != null && session.isUnlockedMainMenu()) {
            plugin.getMenuManager().openSetupMainMenu(player, arena);
        }
    }

    private boolean hasName(ItemStack itemStack, String expected) {
        return itemStack != null
            && itemStack.hasItemMeta()
            && itemStack.getItemMeta().hasDisplayName()
            && expected.equals(itemStack.getItemMeta().getDisplayName());
    }

    private void unlockMenuIfReady(Player player, SetupSession session, Arena arena) {
        boolean unlocked = arena.getWaitingSpawn() != null && arena.getWaitingRegion() != null && arena.getWaitingRegion().isComplete();
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
            .lore("&7Clique com botao direito", "&7para salvar o spawn de espera.").build());
        player.getInventory().setItem(1, new ItemBuilder(Material.WOOD_AXE)
            .name("&eSelecionar area")
            .lore(
                "&7Clique esquerdo: Pos1",
                "&7Clique direito: Pos2",
                "&7Usado para sala de espera,",
                "&7ilha e protecao inicial."
            ).build());

        if (session.isUnlockedMainMenu() || (arena.getWaitingSpawn() != null && arena.getWaitingRegion() != null && arena.getWaitingRegion().isComplete())) {
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
}

package n.plugins.newbedwars.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.TeamColor;
import n.plugins.newbedwars.menu.ArenaSelectorMenu;
import n.plugins.newbedwars.menu.BaseMenu;
import n.plugins.newbedwars.menu.ItemShopMenu;
import n.plugins.newbedwars.menu.SetupNpcMenu;
import n.plugins.newbedwars.menu.SetupConfirmMenu;
import n.plugins.newbedwars.menu.SetupMainMenu;
import n.plugins.newbedwars.menu.SoloQueueMenu;
import n.plugins.newbedwars.menu.TeamSelectorMenu;
import n.plugins.newbedwars.menu.TeamSetupMenu;
import n.plugins.newbedwars.menu.UpgradeShopMenu;
import n.plugins.newbedwars.npc.BedWarsNpcType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class MenuManager {

    private final NewBedWars plugin;
    private final Map<UUID, BaseMenu> openMenus;

    public MenuManager(NewBedWars plugin) {
        this.plugin = plugin;
        this.openMenus = new HashMap<UUID, BaseMenu>();
    }

    public void track(Player player, BaseMenu menu) {
        openMenus.put(player.getUniqueId(), menu);
    }

    public boolean isViewingMenu(Player player) {
        return resolveMenu(player, player.getOpenInventory().getTopInventory()) != null;
    }

    public void openSetupMainMenu(Player player, Arena arena) {
        plugin.getSetupManager().prepareSetupMenuInventory(player);
        new SetupMainMenu(plugin, arena).open(player);
    }

    public void openTeamSetupMenu(Player player, Arena arena, TeamColor color) {
        plugin.getSetupManager().prepareSetupMenuInventory(player);
        new TeamSetupMenu(plugin, arena, color).open(player);
    }

    public void openSetupConfirmMenu(Player player, Arena arena) {
        plugin.getSetupManager().prepareSetupMenuInventory(player);
        new SetupConfirmMenu(plugin, arena).open(player);
    }

    public void openSetupNpcMenu(Player player, Arena arena, TeamColor color, BedWarsNpcType type) {
        plugin.getSetupManager().prepareSetupMenuInventory(player);
        new SetupNpcMenu(plugin, arena, color, type).open(player);
    }

    public void openItemShop(Player player) {
        new ItemShopMenu(plugin).open(player);
    }

    public void openUpgradeShop(Player player) {
        new UpgradeShopMenu(plugin).open(player);
    }

    public void openSoloQueueMenu(Player player) {
        new SoloQueueMenu(plugin).open(player);
    }

    public void openArenaSelectorMenu(Player player) {
        new ArenaSelectorMenu(plugin).open(player);
    }

    public void openTeamSelectorMenu(Player player, Arena arena) {
        new TeamSelectorMenu(plugin, arena).open(player);
    }

    public boolean handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return false;
        }

        Player player = (Player) event.getWhoClicked();
        Inventory topInventory = event.getView().getTopInventory();
        BaseMenu menu = resolveMenu(player, topInventory);
        if (menu == null) {
            return false;
        }

        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        int topSize = topInventory.getSize();
        if (rawSlot < 0 || rawSlot >= topSize || event.getClickedInventory() == null) {
            forceInventorySync(player);
            return true;
        }

        menu.handleClick(player, rawSlot, event.getClick());
        forceInventorySync(player);
        return true;
    }

    public boolean handleDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return false;
        }

        Player player = (Player) event.getWhoClicked();
        Inventory topInventory = event.getView().getTopInventory();
        BaseMenu menu = resolveMenu(player, topInventory);
        if (menu == null) {
            return false;
        }

        Set<Integer> rawSlots = event.getRawSlots();
        int topSize = topInventory.getSize();
        for (Integer rawSlot : rawSlots) {
            if (rawSlot != null && rawSlot.intValue() < topSize) {
                event.setCancelled(true);
                forceInventorySync(player);
                return true;
            }
        }

        event.setCancelled(true);
        forceInventorySync(player);
        return true;
    }

    public void handleClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        BaseMenu current = openMenus.get(player.getUniqueId());
        if (current != null && matchesMenu(current, event.getInventory())) {
            openMenus.remove(player.getUniqueId());
        }
    }

    private boolean matchesMenu(BaseMenu menu, Inventory inventory) {
        if (menu == null || inventory == null) {
            return false;
        }

        return menu.isInventory(inventory) || inventory.getHolder() == menu;
    }

    private BaseMenu resolveMenu(Player player, Inventory topInventory) {
        if (player == null || topInventory == null) {
            return null;
        }

        BaseMenu tracked = openMenus.get(player.getUniqueId());
        if (matchesMenu(tracked, topInventory)) {
            return tracked;
        }

        if (topInventory.getHolder() instanceof BaseMenu) {
            BaseMenu holderMenu = (BaseMenu) topInventory.getHolder();
            openMenus.put(player.getUniqueId(), holderMenu);
            return holderMenu;
        }

        return null;
    }

    private void forceInventorySync(final Player player) {
        if (player == null) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.updateInventory();
                }
            }
        });
    }
}

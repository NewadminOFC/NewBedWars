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
import n.plugins.newbedwars.menu.SetupConfirmMenu;
import n.plugins.newbedwars.menu.SetupMainMenu;
import n.plugins.newbedwars.menu.SoloQueueMenu;
import n.plugins.newbedwars.menu.TeamSetupMenu;
import n.plugins.newbedwars.menu.UpgradeShopMenu;
import org.bukkit.entity.Player;
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
        BaseMenu menu = openMenus.get(player.getUniqueId());
        return menu != null && player.getOpenInventory().getTopInventory().getHolder() == menu;
    }

    public void openSetupMainMenu(Player player, Arena arena) {
        new SetupMainMenu(plugin, arena).open(player);
    }

    public void openTeamSetupMenu(Player player, Arena arena, TeamColor color) {
        new TeamSetupMenu(plugin, arena, color).open(player);
    }

    public void openSetupConfirmMenu(Player player, Arena arena) {
        new SetupConfirmMenu(plugin, arena).open(player);
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

    public boolean handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return false;
        }

        Player player = (Player) event.getWhoClicked();
        BaseMenu menu = openMenus.get(player.getUniqueId());
        if (menu == null) {
            return false;
        }

        if (event.getView().getTopInventory().getHolder() != menu) {
            return false;
        }

        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot < 0 || rawSlot >= topSize || event.getClickedInventory() == null) {
            return true;
        }

        menu.handleClick(player, rawSlot, event.getClick());
        return true;
    }

    public boolean handleDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return false;
        }

        Player player = (Player) event.getWhoClicked();
        BaseMenu menu = openMenus.get(player.getUniqueId());
        if (menu == null) {
            return false;
        }

        if (event.getView().getTopInventory().getHolder() != menu) {
            return false;
        }

        Set<Integer> rawSlots = event.getRawSlots();
        int topSize = event.getView().getTopInventory().getSize();
        for (Integer rawSlot : rawSlots) {
            if (rawSlot != null && rawSlot.intValue() < topSize) {
                event.setCancelled(true);
                return true;
            }
        }

        event.setCancelled(true);
        return true;
    }

    public void handleClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        BaseMenu current = openMenus.get(player.getUniqueId());
        if (current != null && event.getInventory().getHolder() == current) {
            openMenus.remove(player.getUniqueId());
        }
    }
}

package n.plugins.newbedwars.listener;

import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.menu.BaseMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

public class InventoryListener implements Listener {

    private final NewBedWars plugin;

    public InventoryListener(NewBedWars plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (plugin.getMenuManager().handleClick(event)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (event.getView().getTopInventory().getHolder() instanceof BaseMenu) {
            event.setCancelled(true);
        }

        if (isBlockedMerchantView(player, event.getView().getTopInventory().getType())) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        if (plugin.getSetupManager().isInSetup(player) || plugin.getGameManager().isRespawning(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (plugin.getMenuManager().handleDrag(event)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (event.getView().getTopInventory().getHolder() instanceof BaseMenu) {
            event.setCancelled(true);
        }

        if (isBlockedMerchantView(player, event.getView().getTopInventory().getType())) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        if (plugin.getSetupManager().isInSetup(player) || plugin.getGameManager().isRespawning(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        plugin.getMenuManager().handleClose(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        if (isBlockedMerchantView(player, event.getInventory().getType())) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        player.closeInventory();
                    }
                }
            });
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (plugin.getMenuManager().isViewingMenu(player)
            || plugin.getSetupManager().isInSetup(player)
            || plugin.getGameManager().isRespawning(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemPickup(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        if (plugin.getSetupManager().isInSetup(player)) {
            event.setCancelled(true);
            return;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (arena == null) {
            return;
        }

        if (arena.getSpectators().contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (plugin.getGameManager().isRespawning(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        ItemStack itemStack = event.getItem().getItemStack();
        if (itemStack != null && isArmor(itemStack.getType())) {
            event.setCancelled(true);
            return;
        }

        player.setCanPickupItems(true);
        event.setCancelled(false);
    }

    private boolean isArmor(Material material) {
        return material == Material.LEATHER_HELMET
            || material == Material.LEATHER_CHESTPLATE
            || material == Material.LEATHER_LEGGINGS
            || material == Material.LEATHER_BOOTS
            || material == Material.CHAINMAIL_HELMET
            || material == Material.CHAINMAIL_CHESTPLATE
            || material == Material.CHAINMAIL_LEGGINGS
            || material == Material.CHAINMAIL_BOOTS
            || material == Material.IRON_HELMET
            || material == Material.IRON_CHESTPLATE
            || material == Material.IRON_LEGGINGS
            || material == Material.IRON_BOOTS
            || material == Material.DIAMOND_HELMET
            || material == Material.DIAMOND_CHESTPLATE
            || material == Material.DIAMOND_LEGGINGS
            || material == Material.DIAMOND_BOOTS
            || material == Material.GOLD_HELMET
            || material == Material.GOLD_CHESTPLATE
            || material == Material.GOLD_LEGGINGS
            || material == Material.GOLD_BOOTS;
    }

    private boolean isBlockedMerchantView(Player player, InventoryType type) {
        if (type != InventoryType.MERCHANT || player == null) {
            return false;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        return plugin.getSetupManager().isInSetup(player) || arena != null;
    }
}

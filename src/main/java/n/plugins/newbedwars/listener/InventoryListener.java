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
import org.bukkit.event.inventory.ClickType;
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

        if (shouldLockArmor(event, player)) {
            event.setCancelled(true);
            player.updateInventory();
            return;
        }

        if (shouldBlockSwordToContainer(event, player)) {
            event.setCancelled(true);
            player.updateInventory();
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

        if (shouldLockArmor(event, player)) {
            event.setCancelled(true);
            player.updateInventory();
            return;
        }

        if (shouldBlockSwordToContainer(event, player)) {
            event.setCancelled(true);
            player.updateInventory();
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
            return;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (arena != null && event.getItemDrop() != null && event.getItemDrop().getItemStack() != null
            && isSword(event.getItemDrop().getItemStack().getType())) {
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

    private boolean isSword(Material material) {
        return material == Material.WOOD_SWORD
            || material == Material.STONE_SWORD
            || material == Material.IRON_SWORD
            || material == Material.DIAMOND_SWORD
            || material == Material.GOLD_SWORD;
    }

    private boolean shouldLockArmor(InventoryClickEvent event, Player player) {
        if (event == null || player == null) {
            return false;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (arena == null || arena.getSpectators().contains(player.getUniqueId()) || plugin.getGameManager().isRespawning(player.getUniqueId())) {
            return false;
        }

        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            return true;
        }

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (event.isShiftClick() && current != null && isArmor(current.getType())) {
            return true;
        }

        if (cursor != null && isArmor(cursor.getType())) {
            return true;
        }

        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarButton = event.getHotbarButton();
            if (hotbarButton >= 0 && hotbarButton < 9) {
                ItemStack hotbarItem = player.getInventory().getItem(hotbarButton);
                if (hotbarItem != null && isArmor(hotbarItem.getType())) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean shouldLockArmor(InventoryDragEvent event, Player player) {
        if (event == null || player == null) {
            return false;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (arena == null || arena.getSpectators().contains(player.getUniqueId()) || plugin.getGameManager().isRespawning(player.getUniqueId())) {
            return false;
        }

        ItemStack oldCursor = event.getOldCursor();
        return oldCursor != null && isArmor(oldCursor.getType());
    }

    private boolean shouldBlockSwordToContainer(InventoryClickEvent event, Player player) {
        if (event == null || player == null) {
            return false;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (arena == null || arena.getSpectators().contains(player.getUniqueId()) || plugin.getGameManager().isRespawning(player.getUniqueId())) {
            return false;
        }

        InventoryType topType = event.getView().getTopInventory().getType();
        if (topType == InventoryType.CRAFTING || topType == InventoryType.PLAYER) {
            return false;
        }

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (event.isShiftClick() && current != null && isSword(current.getType())) {
            return true;
        }

        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() >= 0 && event.getRawSlot() < topSize && cursor != null && isSword(cursor.getType())) {
            return true;
        }

        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarButton = event.getHotbarButton();
            if (hotbarButton >= 0 && hotbarButton < 9) {
                ItemStack hotbarItem = player.getInventory().getItem(hotbarButton);
                if (hotbarItem != null && isSword(hotbarItem.getType())
                    && event.getRawSlot() >= 0 && event.getRawSlot() < topSize) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean shouldBlockSwordToContainer(InventoryDragEvent event, Player player) {
        if (event == null || player == null) {
            return false;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (arena == null || arena.getSpectators().contains(player.getUniqueId()) || plugin.getGameManager().isRespawning(player.getUniqueId())) {
            return false;
        }

        ItemStack oldCursor = event.getOldCursor();
        if (oldCursor == null || !isSword(oldCursor.getType())) {
            return false;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot != null && rawSlot.intValue() >= 0 && rawSlot.intValue() < topSize) {
                return true;
            }
        }

        return false;
    }

    private boolean isBlockedMerchantView(Player player, InventoryType type) {
        if (type != InventoryType.MERCHANT || player == null) {
            return false;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        return plugin.getSetupManager().isInSetup(player) || arena != null;
    }
}

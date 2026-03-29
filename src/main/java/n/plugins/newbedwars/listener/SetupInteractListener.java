package n.plugins.newbedwars.listener;

import n.plugins.newbedwars.NewBedWars;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class SetupInteractListener implements Listener {

    private final NewBedWars plugin;

    public SetupInteractListener(NewBedWars plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getSetupManager().isInSetup(player)) {
            return;
        }

        ItemStack item = player.getItemInHand();
        Action action = event.getAction();

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if (plugin.getSetupManager().handlePendingPoint(player, event.getClickedBlock())) {
                event.setCancelled(true);
                return;
            }

            if (plugin.getSetupManager().isPositionOneItem(item)) {
                event.setCancelled(true);
                plugin.getSetupManager().handleSelection(player, true);
                return;
            }

            if (plugin.getSetupManager().isPositionTwoItem(item)) {
                event.setCancelled(true);
                plugin.getSetupManager().handleSelection(player, false);
                return;
            }
        }

        if (plugin.getSetupManager().isMenuItem(item) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            plugin.getSetupManager().openMainMenu(player);
            return;
        }

        if (plugin.getSetupManager().isWaitingSpawnItem(item) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            plugin.getSetupManager().handleWaitingSpawnItem(player, event.getClickedBlock());
            return;
        }
    }
}

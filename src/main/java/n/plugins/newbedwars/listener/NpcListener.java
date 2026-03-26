package n.plugins.newbedwars.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import n.plugins.newbedwars.NewBedWars;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRemoveEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class NpcListener implements Listener {

    private final NewBedWars plugin;
    private final Map<UUID, Long> interactionCooldown;

    public NpcListener(NewBedWars plugin) {
        this.plugin = plugin;
        this.interactionCooldown = new HashMap<UUID, Long>();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onNpcClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        if (!plugin.getNpcManager().isBedWarsNpc(npc)) {
            return;
        }

        if (isCoolingDown(event.getClicker().getUniqueId())) {
            return;
        }

        handleNpcInteraction(event.getClicker(), npc);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(clicked);
        if (!plugin.getNpcManager().isBedWarsNpc(npc)) {
            return;
        }

        if (isCoolingDown(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        handleNpcInteraction(event.getPlayer(), npc);
    }

    @EventHandler
    public void onNpcSpawn(NPCSpawnEvent event) {
        if (plugin.getNpcManager().isBedWarsNpc(event.getNPC())) {
            plugin.getNpcManager().updateHologram(event.getNPC());
        }
    }

    @EventHandler
    public void onNpcRemove(NPCRemoveEvent event) {
        if (plugin.getNpcManager().isBedWarsNpc(event.getNPC())) {
            plugin.getNpcManager().removeHologram(event.getNPC().getId());
        }
    }

    private void handleNpcInteraction(org.bukkit.entity.Player player, NPC npc) {
        if (plugin.getNpcManager().isSoloNpc(npc)) {
            plugin.getMenuManager().openSoloQueueMenu(player);
            return;
        }

        if (plugin.getNpcManager().isItemShopNpc(npc)) {
            plugin.getMenuManager().openItemShop(player);
            return;
        }

        if (plugin.getNpcManager().isUpgradeShopNpc(npc)) {
            plugin.getMenuManager().openUpgradeShop(player);
        }
    }

    private boolean isCoolingDown(UUID uniqueId) {
        long now = System.currentTimeMillis();
        Long lastInteraction = interactionCooldown.get(uniqueId);
        if (lastInteraction != null && now - lastInteraction.longValue() < 300L) {
            return true;
        }

        interactionCooldown.put(uniqueId, now);
        return false;
    }
}

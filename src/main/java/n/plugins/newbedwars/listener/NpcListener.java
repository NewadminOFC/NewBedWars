package n.plugins.newbedwars.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.TeamColor;
import n.plugins.newbedwars.npc.BedWarsNpcType;
import n.plugins.newbedwars.util.SoundUtil;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRemoveEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class NpcListener implements Listener {

    private final NewBedWars plugin;
    private final Map<UUID, Long> interactionCooldown;

    public NpcListener(NewBedWars plugin) {
        this.plugin = plugin;
        this.interactionCooldown = new HashMap<UUID, Long>();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        if (!plugin.getNpcManager().isBedWarsNpc(npc)) {
            return;
        }

        event.setCancelled(true);

        if (isCoolingDown(event.getClicker().getUniqueId())) {
            return;
        }

        handleNpcInteraction(event.getClicker(), npc);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityInteractAt(PlayerInteractAtEntityEvent event) {
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
        if (plugin.getSetupManager().isInSetup(player) && (plugin.getNpcManager().isItemShopNpc(npc) || plugin.getNpcManager().isUpgradeShopNpc(npc))) {
            String arenaName = plugin.getNpcManager().getArenaName(npc);
            TeamColor color = plugin.getNpcManager().getTeamColor(npc);
            BedWarsNpcType type = plugin.getNpcManager().getNpcType(npc);
            Arena arena = plugin.getArenaManager().getArena(arenaName);
            if (plugin.getSetupManager().canEditNpc(player, arena) && color != null && type != null) {
                openMenuNextTick(player, new Runnable() {
                    @Override
                    public void run() {
                        plugin.getMenuManager().openSetupNpcMenu(player, arena, color, type);
                    }
                });
                return;
            }
        }

        if (plugin.getNpcManager().isSoloNpc(npc)) {
            openMenuNextTick(player, new Runnable() {
                @Override
                public void run() {
                    SoundUtil.playConfigured(plugin, player, "sound-effects.npc-open", "CLICK", 1.0F, 1.2F);
                    plugin.getMenuManager().openSoloQueueMenu(player);
                }
            });
            return;
        }

        if (plugin.getNpcManager().isItemShopNpc(npc)) {
            openMenuNextTick(player, new Runnable() {
                @Override
                public void run() {
                    SoundUtil.playConfigured(plugin, player, "sound-effects.shop-open", "CLICK", 1.0F, 1.2F);
                    plugin.getMenuManager().openItemShop(player);
                }
            });
            return;
        }

        if (plugin.getNpcManager().isUpgradeShopNpc(npc)) {
            openMenuNextTick(player, new Runnable() {
                @Override
                public void run() {
                    SoundUtil.playConfigured(plugin, player, "sound-effects.shop-open", "CLICK", 1.0F, 1.2F);
                    plugin.getMenuManager().openUpgradeShop(player);
                }
            });
        }
    }

    private void openMenuNextTick(final org.bukkit.entity.Player player, final Runnable runnable) {
        player.closeInventory();
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (player == null || !player.isOnline()) {
                    return;
                }
                runnable.run();
            }
        });
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

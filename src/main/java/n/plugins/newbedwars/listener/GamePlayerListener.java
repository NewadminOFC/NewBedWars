package n.plugins.newbedwars.listener;

import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class GamePlayerListener implements Listener {

    private final NewBedWars plugin;

    public GamePlayerListener(NewBedWars plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.getGameManager().handleDeath(event.getEntity(), event);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getGameManager().handleRespawn(event.getPlayer(), event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        handleLeave(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        handleLeave(event.getPlayer());
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        if (plugin.getArenaManager().getArenaByPlayer(player.getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }

        Player player = (Player) entity;
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (arena == null) {
            return;
        }

        if (arena.getState() == ArenaState.WAITING
            || arena.getState() == ArenaState.STARTING
            || arena.getSpectators().contains(player.getUniqueId())
            || plugin.getGameManager().isRespawning(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void handleLeave(Player player) {
        if (plugin.getSetupManager().isInSetup(player)) {
            plugin.getSetupManager().stopSession(player, false);
        }

        if (plugin.getArenaManager().getArenaByPlayer(player.getUniqueId()) != null) {
            plugin.getGameManager().leaveArena(player, true);
            plugin.getLobbyManager().teleportToMainWorld(player);
        }
    }
}

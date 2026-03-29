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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
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
    public void onJoin(PlayerJoinEvent event) {
        String message = event.getJoinMessage();
        event.setJoinMessage(null);
        broadcastWorldMessage(event.getPlayer(), message);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getArenaManager().getArenaByPlayer(event.getPlayer().getUniqueId()) != null) {
            event.setQuitMessage(null);
        } else {
            String message = event.getQuitMessage();
            event.setQuitMessage(null);
            broadcastWorldMessage(event.getPlayer(), message);
        }
        handleLeave(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        if (plugin.getArenaManager().getArenaByPlayer(event.getPlayer().getUniqueId()) != null) {
            event.setLeaveMessage(null);
        } else {
            String message = event.getLeaveMessage();
            event.setLeaveMessage(null);
            broadcastWorldMessage(event.getPlayer(), message);
        }
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

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (arena == null || arena.getState() != ArenaState.INGAME) {
            return;
        }

        Double voidY = resolveVoidY(arena);
        if (voidY == null || player.getLocation().getY() > voidY.doubleValue()) {
            return;
        }

        if (arena.getSpectators().contains(player.getUniqueId()) || plugin.getGameManager().isRespawning(player.getUniqueId())) {
            org.bukkit.Location safeLocation = arena.getSpectatorSpawn();
            if (safeLocation != null) {
                player.teleport(safeLocation);
            }
            return;
        }

        if (player.isDead() || player.getHealth() <= 0.0D) {
            return;
        }

        player.setLastDamageCause(new EntityDamageEvent(player, EntityDamageEvent.DamageCause.VOID, 1000.0D));
        player.setHealth(0.0D);
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

    private Double resolveVoidY(Arena arena) {
        if (arena == null
            || arena.getState() != ArenaState.INGAME
            || !isAntiVoidEnabled()
            || !arena.hasAntiVoidY()) {
            return null;
        }

        return arena.getAntiVoidY();
    }

    private boolean isAntiVoidEnabled() {
        if (plugin.getConfig().isBoolean("anti-void")) {
            return plugin.getConfig().getBoolean("anti-void", true);
        }
        return plugin.getConfig().getBoolean("anti-void.enabled", true);
    }

    private void broadcastWorldMessage(Player source, String message) {
        if (source == null || source.getWorld() == null || message == null || message.trim().isEmpty()) {
            return;
        }

        for (Player viewer : source.getWorld().getPlayers()) {
            if (viewer == null || !viewer.isOnline() || viewer.equals(source)) {
                continue;
            }

            viewer.sendMessage(message);
        }
    }
}

package n.plugins.newbedwars.listener;

import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaState;
import n.plugins.newbedwars.arena.TeamColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

public class CombatListener implements Listener {

    private final NewBedWars plugin;

    public CombatListener(NewBedWars plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        Arena attackerArena = plugin.getArenaManager().getArenaByPlayer(attacker.getUniqueId());
        Arena victimArena = plugin.getArenaManager().getArenaByPlayer(victim.getUniqueId());
        if (attackerArena == null || victimArena == null || attackerArena != victimArena || attackerArena.getState() != ArenaState.INGAME) {
            return;
        }

        if (isNonCombatPlayer(attackerArena, attacker) || isNonCombatPlayer(attackerArena, victim)) {
            event.setCancelled(true);
            return;
        }

        if (shouldCancelFriendlyFire(attackerArena, attacker, victim)) {
            event.setCancelled(true);
            return;
        }

        plugin.getGameManager().tagCombat(attacker, victim);

        if (!isPvpManagerEnabled() || !(event.getDamager() instanceof Player)) {
            return;
        }

        applyCustomCombatProfile(attacker, victim);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }

        if (damager instanceof Projectile) {
            ProjectileSource source = ((Projectile) damager).getShooter();
            if (source instanceof Player) {
                return (Player) source;
            }
        }

        return null;
    }

    private boolean isNonCombatPlayer(Arena arena, Player player) {
        return arena.getSpectators().contains(player.getUniqueId()) || plugin.getGameManager().isRespawning(player.getUniqueId());
    }

    private boolean shouldCancelFriendlyFire(Arena arena, Player attacker, Player victim) {
        if (!plugin.getConfig().getBoolean("pvp-manager.cancel-friendly-fire", true)) {
            return false;
        }

        TeamColor attackerColor = plugin.getTeamManager().getColor(arena, attacker.getUniqueId());
        TeamColor victimColor = plugin.getTeamManager().getColor(arena, victim.getUniqueId());
        return attackerColor != null && attackerColor == victimColor;
    }

    private boolean isPvpManagerEnabled() {
        if (plugin.getConfig().isBoolean("PvP-Manager")) {
            return plugin.getConfig().getBoolean("PvP-Manager", true);
        }
        return plugin.getConfig().getBoolean("pvp-manager.enabled", true);
    }

    private void applyCustomCombatProfile(final Player attacker, final Player victim) {
        final double horizontal = plugin.getConfig().getDouble("pvp-manager.horizontal", 0.39D)
            + (attacker.isSprinting() ? plugin.getConfig().getDouble("pvp-manager.sprint-extra-horizontal", 0.10D) : 0.0D);
        final double vertical = plugin.getConfig().getDouble("pvp-manager.vertical", 0.34D)
            + (attacker.isSprinting() ? plugin.getConfig().getDouble("pvp-manager.sprint-extra-vertical", 0.02D) : 0.0D);
        final double maxHorizontal = Math.max(0.1D, plugin.getConfig().getDouble("pvp-manager.max-horizontal", 0.90D));
        final double maxVertical = Math.max(0.1D, plugin.getConfig().getDouble("pvp-manager.max-vertical", 0.42D));
        final boolean preserveHigherY = plugin.getConfig().getBoolean("pvp-manager.preserve-higher-y", true);
        final Vector direction = resolveHorizontalDirection(attacker, victim);
        final Vector currentVelocity = victim.getVelocity().clone();

        int maximumNoDamageTicks = Math.max(1, plugin.getConfig().getInt("pvp-manager.maximum-no-damage-ticks", 12));
        victim.setMaximumNoDamageTicks(maximumNoDamageTicks);

        if (plugin.getConfig().getBoolean("pvp-manager.reset-sprint-on-hit", true)) {
            attacker.setSprinting(false);
        }

        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!attacker.isOnline() || !victim.isOnline()) {
                    return;
                }

                Vector velocity = direction.clone().multiply(horizontal);
                double newY = preserveHigherY ? Math.max(currentVelocity.getY(), vertical) : vertical;
                velocity.setY(Math.min(maxVertical, Math.max(0.0D, newY)));

                double horizontalLength = Math.sqrt((velocity.getX() * velocity.getX()) + (velocity.getZ() * velocity.getZ()));
                if (horizontalLength > maxHorizontal && horizontalLength > 0.0D) {
                    double scale = maxHorizontal / horizontalLength;
                    velocity.setX(velocity.getX() * scale);
                    velocity.setZ(velocity.getZ() * scale);
                }

                victim.setVelocity(velocity);
            }
        });
    }

    private Vector resolveHorizontalDirection(Player attacker, Player victim) {
        Vector direction = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
        direction.setY(0.0D);

        if (direction.lengthSquared() <= 0.0001D) {
            direction = attacker.getLocation().getDirection().clone();
            direction.setY(0.0D);
        }

        if (direction.lengthSquared() <= 0.0001D) {
            direction = new Vector(0.0D, 0.0D, 1.0D);
        }

        return direction.normalize();
    }
}

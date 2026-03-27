package n.plugins.newbedwars.listener;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

public class GameItemListener implements Listener {

    public static final String META_SPECIAL_FIREBALL = "newbedwars_special_fireball";
    public static final String META_SPECIAL_TNT = "newbedwars_special_tnt";
    public static final String META_SPECIAL_OWNER = "newbedwars_special_owner";
    public static final String META_TNT_UNLOCKED = "newbedwars_tnt_unlocked";

    private final NewBedWars plugin;
    private final List<RecentBlast> recentBlasts = new ArrayList<RecentBlast>();

    public GameItemListener(NewBedWars plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (arena == null) {
            return;
        }

        ItemStack hand = player.getItemInHand();
        if ((arena.getState() == ArenaState.WAITING || arena.getState() == ArenaState.STARTING)
            && plugin.getGameManager().handleWaitingLobbyItemUse(player, hand)) {
            event.setCancelled(true);
            return;
        }

        if (arena.getState() != ArenaState.INGAME) {
            return;
        }

        if (arena.getSpectators().contains(player.getUniqueId()) || plugin.getGameManager().isRespawning(player.getUniqueId())) {
            return;
        }

        if (hand == null || hand.getType() != Material.FIREBALL) {
            return;
        }

        event.setCancelled(true);
        try {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        } catch (Throwable ignored) {
        }
        removeOneFromHand(player);
        launchFireball(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpecialExplode(EntityExplodeEvent event) {
        Entity explosive = event.getEntity();
        if (!isSpecialExplosive(explosive)) {
            return;
        }

        String path = explosive.hasMetadata(META_SPECIAL_FIREBALL) ? "special-items.fireball" : "special-items.tnt";
        double radius = plugin.getConfig().getDouble(path + ".knockback-radius", explosive instanceof Fireball ? 5.2D : 5.6D);
        recordBlast(event.getLocation(), radius);
        applyExplosionKnockback(explosive, event.getLocation(), radius, path);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (isSpecialExplosive(event.getDamager())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
            && event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return;
        }

        if (!isRecentSpecialBlast(event.getEntity().getLocation())) {
            return;
        }

        event.setCancelled(true);
    }

    private void launchFireball(Player player) {
        Fireball fireball = player.launchProjectile(Fireball.class);
        Vector direction = player.getEyeLocation().getDirection().normalize().multiply(plugin.getConfig().getDouble("special-items.fireball.speed", 2.75D));
        fireball.setVelocity(direction);
        fireball.setYield((float) plugin.getConfig().getDouble("special-items.fireball.yield", 2.2D));
        fireball.setIsIncendiary(plugin.getConfig().getBoolean("special-items.fireball.incendiary", false));
        fireball.setMetadata(META_SPECIAL_FIREBALL, new FixedMetadataValue(plugin, true));
        fireball.setMetadata(META_SPECIAL_OWNER, new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        try {
            fireball.setBounce(false);
        } catch (Throwable ignored) {
        }

        player.playSound(player.getLocation(), Sound.GHAST_FIREBALL, 1.0F, 1.15F);
    }

    private void applyExplosionKnockback(Entity explosive, Location origin, double radius, String configPath) {
        if (origin == null || origin.getWorld() == null) {
            return;
        }

        if (explosive instanceof TNTPrimed) {
            applyTntJumpKnockback((TNTPrimed) explosive, origin, radius, configPath);
            return;
        }

        boolean fireball = explosive instanceof Fireball;
        Player owner = getExplosiveOwner(explosive);
        double radiusSafe = Math.max(0.5D, radius);
        double closeDistance = plugin.getConfig().getDouble(configPath + ".close-distance", fireball ? 1.85D : 2.0D);
        double closeHorizontal = plugin.getConfig().getDouble(configPath + ".close-horizontal",
            plugin.getConfig().getDouble(configPath + ".knockback-horizontal", fireball ? 3.1D : 2.45D));
        double closeVertical = plugin.getConfig().getDouble(configPath + ".close-vertical",
            plugin.getConfig().getDouble(configPath + ".knockback-vertical", fireball ? 0.8D : 0.78D));
        double farHorizontal = plugin.getConfig().getDouble(configPath + ".far-horizontal",
            plugin.getConfig().getDouble(configPath + ".knockback-horizontal", fireball ? 2.35D : 2.1D) * (fireball ? 0.84D : 0.86D));
        double farVertical = plugin.getConfig().getDouble(configPath + ".far-vertical",
            plugin.getConfig().getDouble(configPath + ".knockback-vertical", fireball ? 0.34D : 0.42D) * (fireball ? 0.38D : 0.54D));
        double selfHorizontalMultiplier = plugin.getConfig().getDouble(configPath + ".self-horizontal-multiplier", fireball ? 1.12D : 1.0D);
        double selfVerticalMultiplier = plugin.getConfig().getDouble(configPath + ".self-vertical-multiplier", fireball ? 1.08D : 1.0D);
        double maxVelocity = plugin.getConfig().getDouble(configPath + ".max-velocity", fireball ? 3.95D : 3.35D);

        for (Entity nearby : origin.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (!(nearby instanceof Player)) {
                continue;
            }

            Player target = (Player) nearby;
            Arena arena = plugin.getArenaManager().getArenaByPlayer(target.getUniqueId());
            if (arena == null || arena.getState() != ArenaState.INGAME) {
                continue;
            }

            if (arena.getSpectators().contains(target.getUniqueId()) || plugin.getGameManager().isRespawning(target.getUniqueId())) {
                continue;
            }

            double distance = target.getLocation().distance(origin);
            if (distance > radius) {
                continue;
            }

            boolean close = distance <= closeDistance;
            boolean self = owner != null && owner.getUniqueId().equals(target.getUniqueId());
            Vector direction = resolveKnockbackDirection(explosive, origin, target, owner, close);

            double scale;
            if (close) {
                double closeFactor = 1.0D - Math.min(0.35D, distance / Math.max(0.5D, closeDistance) * 0.25D);
                scale = Math.max(0.82D, closeFactor);
            } else {
                double farProgress = (distance - closeDistance) / Math.max(0.5D, radiusSafe - closeDistance);
                scale = Math.max(0.72D, 1.0D - (farProgress * 0.32D));
            }

            double horizontal = (close ? closeHorizontal : farHorizontal) * scale;
            double vertical = (close ? closeVertical : farVertical) * scale;
            if (self) {
                horizontal *= selfHorizontalMultiplier;
                vertical *= selfVerticalMultiplier;
            }

            Vector velocity = direction.multiply(horizontal);
            velocity.setY(vertical);
            if (!fireball && target.isOnGround()) {
                velocity.setY(velocity.getY() + 0.08D);
            }
            velocity = limitVelocity(velocity, fireball ? 3.75D : 3.35D);
            velocity = limitVelocity(velocity, maxVelocity);
            target.setVelocity(velocity);
            target.setFallDistance(0.0F);
        }
    }

    private void applyTntJumpKnockback(TNTPrimed tnt, Location origin, double radius, String configPath) {
        Player owner = getExplosiveOwner(tnt);
        double topRadius = plugin.getConfig().getDouble(configPath + ".top-radius", 1.15D);
        double topVertical = plugin.getConfig().getDouble(configPath + ".top-vertical",
            plugin.getConfig().getDouble(configPath + ".knockback-vertical", 0.78D) * 1.45D);
        double topHorizontal = plugin.getConfig().getDouble(configPath + ".top-horizontal", 0.14D);
        double closeDistance = plugin.getConfig().getDouble(configPath + ".close-distance", 2.15D);
        double frontHorizontal = plugin.getConfig().getDouble(configPath + ".front-horizontal",
            plugin.getConfig().getDouble(configPath + ".knockback-horizontal", 2.45D) * 1.16D);
        double frontVertical = plugin.getConfig().getDouble(configPath + ".front-vertical",
            plugin.getConfig().getDouble(configPath + ".knockback-vertical", 0.78D) * 0.52D);
        double maxVelocity = plugin.getConfig().getDouble(configPath + ".max-velocity", 3.25D);
        double chainRadius = plugin.getConfig().getDouble(configPath + ".chain-radius", Math.max(2.6D, radius * 0.7D));
        double chainHorizontal = plugin.getConfig().getDouble(configPath + ".chain-horizontal", frontHorizontal * 0.25D);
        double chainVertical = plugin.getConfig().getDouble(configPath + ".chain-vertical", 0.10D);
        double chainMaxVelocity = plugin.getConfig().getDouble(configPath + ".chain-max-velocity", 0.95D);
        double radiusSafe = Math.max(0.5D, radius);

        for (Entity nearby : origin.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (!(nearby instanceof Player)) {
                continue;
            }

            Player target = (Player) nearby;
            Arena arena = plugin.getArenaManager().getArenaByPlayer(target.getUniqueId());
            if (arena == null || arena.getState() != ArenaState.INGAME) {
                continue;
            }

            if (arena.getSpectators().contains(target.getUniqueId()) || plugin.getGameManager().isRespawning(target.getUniqueId())) {
                continue;
            }

            Vector horizontalOffset = target.getLocation().toVector().subtract(origin.toVector());
            horizontalOffset.setY(0.0D);
            double horizontalDistance = horizontalOffset.length();
            double fullDistance = target.getLocation().distance(origin);
            if (fullDistance > radius) {
                continue;
            }

            double yDiff = target.getLocation().getY() - origin.getY();
            boolean onTop = horizontalDistance <= topRadius && yDiff >= 0.55D;
            boolean glued = horizontalDistance <= topRadius && yDiff >= -0.40D && yDiff <= 1.20D;
            boolean self = owner != null && owner.getUniqueId().equals(target.getUniqueId());

            Vector velocity;
            if (onTop || glued) {
                Vector direction = horizontalOffset.lengthSquared() <= 0.01D ? new Vector(0.0D, 0.0D, 0.0D) : horizontalOffset.normalize().multiply(topHorizontal);
                double upward = topVertical * (self ? 1.06D : 1.0D);
                if (glued && !onTop) {
                    upward += 0.16D;
                }
                velocity = new Vector(direction.getX(), upward, direction.getZ());
            } else {
                Vector direction = horizontalOffset.lengthSquared() <= 0.01D ? resolveDefaultHorizontalDirection(owner, target) : horizontalOffset.normalize();
                double progress = Math.min(1.0D, horizontalDistance / radiusSafe);
                double horizontal = frontHorizontal * Math.max(0.74D, 1.0D - (progress * 0.22D));
                double vertical = frontVertical;

                if (horizontalDistance <= closeDistance) {
                    double closeBoost = 1.0D - Math.min(1.0D, horizontalDistance / Math.max(0.5D, closeDistance));
                    horizontal += closeBoost * 0.34D;
                    vertical += closeBoost * 0.10D;
                }

                if (self) {
                    horizontal *= 1.05D;
                    vertical *= 1.04D;
                }

                velocity = direction.multiply(horizontal);
                velocity.setY(vertical);
            }

            velocity = limitVelocity(velocity, maxVelocity);
            target.setVelocity(velocity);
            target.setFallDistance(0.0F);
        }

        pushNearbyTnt(tnt, origin, chainRadius, chainHorizontal, chainVertical, chainMaxVelocity);
    }

    private Vector resolveKnockbackDirection(Entity explosive, Location origin, Player target, Player owner, boolean close) {
        if (explosive instanceof Fireball) {
            Vector forward = explosive.getVelocity() == null ? new Vector(0.0D, 0.0D, 0.0D) : explosive.getVelocity().clone();
            forward.setY(0.0D);

            Vector away = target.getLocation().toVector().subtract(origin.toVector());
            away.setY(0.0D);

            if (forward.lengthSquared() <= 0.01D) {
                forward = away.clone();
            }
            if (away.lengthSquared() <= 0.01D) {
                away = forward.clone();
            }
            if (forward.lengthSquared() <= 0.01D) {
                forward = new Vector(0.0D, 0.0D, 1.0D);
            }
            if (away.lengthSquared() <= 0.01D) {
                away = new Vector(0.0D, 0.0D, 1.0D);
            }

            Vector direction;
            if (owner != null && owner.getUniqueId().equals(target.getUniqueId())) {
                Vector look = target.getEyeLocation().getDirection().clone();
                look.setY(0.0D);
                if (look.lengthSquared() <= 0.01D) {
                    look = forward.clone();
                }
                direction = forward.normalize().multiply(close ? 1.45D : 1.2D)
                    .add(look.normalize().multiply(close ? 1.0D : 0.85D))
                    .add(away.normalize().multiply(close ? 0.18D : 0.10D));
            } else {
                direction = forward.normalize().multiply(close ? 1.25D : 1.05D)
                    .add(away.normalize().multiply(close ? 0.60D : 0.45D));
            }
            if (direction.lengthSquared() <= 0.01D) {
                direction = forward.clone();
            }
            direction.setY(0.0D);
            if (direction.lengthSquared() <= 0.01D) {
                direction = new Vector(0.0D, 0.0D, 1.0D);
            }
            return direction.normalize();
        }

        Vector direction = target.getLocation().toVector().subtract(origin.toVector());
        if (direction.lengthSquared() <= 0.01D) {
            direction = explosive != null ? explosive.getVelocity().clone() : target.getLocation().getDirection().clone();
        }
        if (direction.lengthSquared() <= 0.01D) {
            direction = new Vector(0.0D, 0.0D, 1.0D);
        }
        return direction.normalize();
    }

    private Player getExplosiveOwner(Entity explosive) {
        if (explosive == null || !explosive.hasMetadata(META_SPECIAL_OWNER)) {
            return null;
        }

        try {
            String raw = explosive.getMetadata(META_SPECIAL_OWNER).get(0).asString();
            if (raw == null || raw.isEmpty()) {
                return null;
            }
            java.util.UUID uniqueId = java.util.UUID.fromString(raw);
            return plugin.getServer().getPlayer(uniqueId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Vector resolveDefaultHorizontalDirection(Player owner, Player target) {
        if (owner != null) {
            Vector ownerLook = owner.getEyeLocation().getDirection().clone();
            ownerLook.setY(0.0D);
            if (ownerLook.lengthSquared() > 0.01D) {
                return ownerLook.normalize();
            }
        }

        if (target != null) {
            Vector targetLook = target.getEyeLocation().getDirection().clone();
            targetLook.setY(0.0D);
            if (targetLook.lengthSquared() > 0.01D) {
                return targetLook.normalize();
            }
        }

        return new Vector(0.0D, 0.0D, 1.0D);
    }

    private void pushNearbyTnt(TNTPrimed source, Location origin, double radius, double horizontal, double vertical, double maxVelocity) {
        if (origin == null || origin.getWorld() == null) {
            return;
        }

        double radiusSafe = Math.max(0.5D, radius);
        for (Entity nearby : origin.getWorld().getNearbyEntities(origin, radiusSafe, radiusSafe, radiusSafe)) {
            if (!(nearby instanceof TNTPrimed) || nearby.equals(source)) {
                continue;
            }

            TNTPrimed targetTnt = (TNTPrimed) nearby;
            if (!targetTnt.hasMetadata(META_SPECIAL_TNT)) {
                continue;
            }

            Vector offset = targetTnt.getLocation().toVector().subtract(origin.toVector());
            double horizontalDistance = Math.sqrt((offset.getX() * offset.getX()) + (offset.getZ() * offset.getZ()));
            double distance = targetTnt.getLocation().distance(origin);
            if (distance > radiusSafe) {
                continue;
            }

            Vector direction = offset.clone();
            direction.setY(0.0D);
            if (direction.lengthSquared() <= 0.01D) {
                direction = new Vector(0.0D, 0.0D, 1.0D);
            } else {
                direction.normalize();
            }

            double scale = Math.max(0.60D, 1.0D - (distance / radiusSafe) * 0.35D);
            double upward = vertical;
            if (horizontalDistance <= 0.65D) {
                upward += 0.06D;
            }

            Vector velocity = direction.multiply(horizontal * scale);
            velocity.setY(upward);
            velocity = limitVelocity(velocity, maxVelocity);
            targetTnt.setMetadata(META_TNT_UNLOCKED, new FixedMetadataValue(plugin, true));
            targetTnt.setVelocity(velocity);
        }
    }

    private Vector limitVelocity(Vector velocity, double maxLength) {
        if (velocity == null) {
            return new Vector(0.0D, 0.0D, 0.0D);
        }

        double length = velocity.length();
        if (length <= maxLength || length <= 0.0001D) {
            return velocity;
        }

        return velocity.normalize().multiply(maxLength);
    }

    private boolean isSpecialExplosive(Entity entity) {
        return entity != null && (entity.hasMetadata(META_SPECIAL_FIREBALL) || entity.hasMetadata(META_SPECIAL_TNT));
    }

    private void recordBlast(Location location, double radius) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        cleanupBlasts();
        recentBlasts.add(new RecentBlast(location.clone(), radius, System.currentTimeMillis() + 1500L));
    }

    private boolean isRecentSpecialBlast(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        cleanupBlasts();
        for (RecentBlast blast : recentBlasts) {
            if (!blast.location.getWorld().getName().equalsIgnoreCase(location.getWorld().getName())) {
                continue;
            }

            double maxDistance = Math.max(6.0D, blast.radius + 1.5D);
            if (blast.location.distanceSquared(location) <= (maxDistance * maxDistance)) {
                return true;
            }
        }
        return false;
    }

    private void cleanupBlasts() {
        long now = System.currentTimeMillis();
        Iterator<RecentBlast> iterator = recentBlasts.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().expireAt < now) {
                iterator.remove();
            }
        }
    }

    private void removeOneFromHand(Player player) {
        ItemStack hand = player.getItemInHand();
        if (hand == null) {
            return;
        }

        if (hand.getAmount() <= 1) {
            player.setItemInHand(null);
        } else {
            hand.setAmount(hand.getAmount() - 1);
            player.setItemInHand(hand);
        }
        player.updateInventory();
    }

    private static final class RecentBlast {

        private final Location location;
        private final double radius;
        private final long expireAt;

        private RecentBlast(Location location, double radius, long expireAt) {
            this.location = location;
            this.radius = radius;
            this.expireAt = expireAt;
        }
    }
}

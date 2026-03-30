package n.plugins.newbedwars.listener;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaState;
import n.plugins.newbedwars.arena.TeamColor;
import n.plugins.newbedwars.util.ChatUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class GameItemListener implements Listener {

    public static final String META_SPECIAL_FIREBALL = "newbedwars_special_fireball";
    public static final String META_SPECIAL_TNT = "newbedwars_special_tnt";
    public static final String META_SPECIAL_OWNER = "newbedwars_special_owner";
    public static final String META_TNT_UNLOCKED = "newbedwars_tnt_unlocked";
    public static final String META_BRIDGE_EGG = "newbedwars_bridge_egg";
    public static final String META_BUG_BOMB = "newbedwars_bug_bomb";
    public static final String META_SUPPORT_TEAM = "newbedwars_support_team";
    public static final String META_SUPPORT_DURATION = "newbedwars_support_duration";

    private final NewBedWars plugin;
    private final List<RecentBlast> recentBlasts = new ArrayList<RecentBlast>();
    private final Map<UUID, FallDamageProtection> fallDamageProtections = new HashMap<UUID, FallDamageProtection>();
    private final Map<UUID, Long> fireballCooldowns = new HashMap<UUID, Long>();
    private final Map<UUID, Long> slingshotCooldowns = new HashMap<UUID, Long>();

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
        if (plugin.getGameManager().handleWaitingLobbyItemUse(player, hand)) {
            event.setCancelled(true);
            return;
        }

        if (arena.getState() != ArenaState.INGAME) {
            return;
        }

        if (arena.getSpectators().contains(player.getUniqueId()) || plugin.getGameManager().isRespawning(player.getUniqueId())) {
            cancelUse(event);
            return;
        }

        if (hand == null || hand.getType() == Material.AIR) {
            return;
        }

        ConfigurationSection specialReward = findRewardSection(hand, "special-use", null);
        String specialUse = specialReward == null ? null : specialReward.getString("special-use", "").trim().toLowerCase(Locale.ENGLISH);
        if ("bridge-egg".equals(specialUse)) {
            cancelUse(event);
            removeOneFromHand(player);
            launchBridgeEgg(player, arena);
            return;
        }

        if ("slingshot".equals(specialUse)) {
            cancelUse(event);
            launchSlingshot(player, specialReward);
            return;
        }

        if ("bug-bomb".equals(specialUse)) {
            cancelUse(event);
            removeOneFromHand(player);
            launchBugBomb(player, arena, specialReward);
            return;
        }

        if ("iron-golem".equals(specialUse)) {
            cancelUse(event);
            spawnIronGolem(player, arena, event, specialReward);
            return;
        }

        if (hand.getType() != Material.FIREBALL) {
            return;
        }


        cancelUse(event);
        long remainingCooldown = getRemainingFireballCooldownMillis(player.getUniqueId());
        if (remainingCooldown > 0L) {
            Map<String, String> placeholders = new HashMap<String, String>();
            placeholders.put("seconds", String.valueOf((int) Math.ceil(remainingCooldown / 1000.0D)));
            plugin.getMessageManager().send(player, "shops.fireball-cooldown", placeholders);
            return;
        }

        removeOneFromHand(player);
        startFireballCooldown(player.getUniqueId());
        launchFireball(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (arena == null || arena.getState() != ArenaState.INGAME) {
            return;
        }

        if (arena.getSpectators().contains(player.getUniqueId()) || plugin.getGameManager().isRespawning(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        final ConfigurationSection reward = findRewardSection(event.getItem(), "special-use", "custom-potion");
        if (reward == null) {
            return;
        }

        final PotionEffectType effectType = PotionEffectType.getByName(reward.getString("effect-type", "").toUpperCase(Locale.ENGLISH));
        if (effectType == null) {
            return;
        }

        final int durationTicks = Math.max(20, reward.getInt("duration-seconds", 45) * 20);
        final int amplifier = Math.max(0, reward.getInt("amplifier", 0));
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }

                player.removePotionEffect(effectType);
                player.addPotionEffect(new PotionEffect(effectType, durationTicks, amplifier, false, false), true);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile == null) {
            return;
        }

        if (projectile.hasMetadata(META_BUG_BOMB)) {
            spawnBugBombMob(projectile);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEggThrow(PlayerEggThrowEvent event) {
        Egg egg = event.getEgg();
        if (egg != null && egg.hasMetadata(META_BRIDGE_EGG)) {
            event.setHatching(false);
        }
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
        if (isSupportMob(event.getDamager()) && event.getEntity() instanceof Player) {
            if (!canSupportMobHit(event.getDamager(), (Player) event.getEntity())) {
                event.setCancelled(true);
                return;
            }

            Player owner = getExplosiveOwner(event.getDamager());
            if (owner != null) {
                plugin.getGameManager().tagCombat(owner, (Player) event.getEntity());
            }
            return;
        }

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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSupportMobDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof IronGolem) || !isSupportMob(entity)) {
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        FallDamageProtection protection = consumeFallDamageProtection(player.getUniqueId());
        if (protection == null || !isFallDamageCapEnabled(protection.configPath)) {
            return;
        }

        double minimumHealth = Math.max(1.0D, Math.min(player.getMaxHealth(), protection.minimumHearts * 2.0D));
        double maxAllowedFinalDamage = player.getHealth() - minimumHealth;
        if (maxAllowedFinalDamage <= 0.0D) {
            event.setDamage(0.0D);
            return;
        }

        double finalDamage = event.getFinalDamage();
        if (finalDamage <= maxAllowedFinalDamage) {
            return;
        }

        if (finalDamage <= 0.0D) {
            event.setDamage(0.0D);
            return;
        }

        double scaledDamage = event.getDamage() * (maxAllowedFinalDamage / finalDamage);
        event.setDamage(Math.max(0.0D, scaledDamage));
    }

    private void launchSlingshot(Player player, ConfigurationSection reward) {
        if (player == null || !player.isOnline()) {
            return;
        }

        long remaining = getRemainingSlingshotCooldown(player.getUniqueId());
        if (remaining > 0L) {
            return;
        }

        removeOneFromHand(player);

        double forward = reward == null ? 1.10D : reward.getDouble("forward", 1.10D);
        double upward = reward == null ? 1.00D : reward.getDouble("upward", 1.00D);
        double maxY = reward == null ? 1.25D : reward.getDouble("max-upward", 1.25D);

        Vector direction = player.getLocation().getDirection().clone();
        direction.setY(0.0D);

        if (direction.lengthSquared() <= 0.01D) {
            direction = new Vector(0.0D, 0.0D, 0.0D);
        } else {
            direction.normalize().multiply(forward);
        }

        Vector velocity = player.getVelocity().clone();
        velocity.setX(direction.getX());
        velocity.setZ(direction.getZ());
        velocity.setY(Math.min(maxY, upward));

        player.setVelocity(velocity);
        player.setFallDistance(0.0F);
        player.playSound(player.getLocation(), Sound.ENDERDRAGON_WINGS, 1.0F, 1.2F);

        startSlingshotCooldown(player.getUniqueId());
    }

    private void startSlingshotCooldown(UUID uuid) {
        if (uuid == null) {
            return;
        }

        slingshotCooldowns.put(uuid, System.currentTimeMillis() + 8000L);
    }


    private long getRemainingSlingshotCooldown(UUID uuid) {
        if (uuid == null) {
            return 0L;
        }

        Long expireAt = slingshotCooldowns.get(uuid);
        if (expireAt == null) {
            return 0L;
        }

        long remaining = expireAt.longValue() - System.currentTimeMillis();
        if (remaining <= 0L) {
            slingshotCooldowns.remove(uuid);
            return 0L;
        }

        return remaining;
    }

    private void cancelUse(PlayerInteractEvent event) {
        if (event == null) {
            return;
        }

        event.setCancelled(true);
        try {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        } catch (Throwable ignored) {
        }
    }

    private ConfigurationSection findRewardSection(ItemStack item, String field, String expectedValue) {
        if (item == null || item.getType() == Material.AIR || field == null || field.trim().isEmpty()) {
            return null;
        }

        ConfigurationSection items = plugin.getConfig().getConfigurationSection("item-shop.items");
        if (items == null) {
            return null;
        }

        for (String key : items.getKeys(false)) {
            ConfigurationSection itemSection = items.getConfigurationSection(key);
            ConfigurationSection reward = itemSection == null ? null : itemSection.getConfigurationSection("reward");
            if (reward == null) {
                continue;
            }

            String configured = reward.getString(field, "").trim();
            if (configured.isEmpty()) {
                continue;
            }

            if (expectedValue != null && !configured.equalsIgnoreCase(expectedValue)) {
                continue;
            }

            if (matchesConfiguredReward(item, reward)) {
                return reward;
            }
        }

        return null;
    }

    private boolean matchesConfiguredReward(ItemStack item, ConfigurationSection reward) {
        Material material = parseMaterial(reward.getString("material"), null);
        if (material == null || item.getType() != material) {
            return false;
        }

        if (reward.contains("data") && item.getDurability() != (short) reward.getInt("data", 0)) {
            return false;
        }

        String configuredName = reward.getString("name", "").trim();
        if (!configuredName.isEmpty()) {
            return strip(configuredName).equalsIgnoreCase(strip(item));
        }

        return true;
    }

    private void launchBridgeEgg(Player player, Arena arena) {
        TeamColor color = plugin.getTeamManager().getColor(arena, player.getUniqueId());
        if (color == null) {
            return;
        }

        Egg egg = player.launchProjectile(Egg.class);
        egg.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(1.35D));
        egg.setMetadata(META_BRIDGE_EGG, new FixedMetadataValue(plugin, true));
        egg.setMetadata(META_SPECIAL_OWNER, new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        startBridgeEggTask(egg, arena, color);
    }

    private void startBridgeEggTask(final Egg egg, final Arena arena, final TeamColor color) {
        final int[] taskId = new int[1];
        taskId[0] = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (egg == null || !egg.isValid() || egg.isDead() || arena == null || arena.getState() != ArenaState.INGAME) {
                    plugin.getServer().getScheduler().cancelTask(taskId[0]);
                    return;
                }

                Vector direction = egg.getVelocity() == null ? new Vector(0.0D, 0.0D, 0.0D) : egg.getVelocity().clone();
                if (direction.lengthSquared() > 0.01D) {
                    direction.normalize();
                }

                placeBridgeBlock(arena, color, egg.getLocation().clone().subtract(0.0D, 1.15D, 0.0D));
                placeBridgeBlock(arena, color, egg.getLocation().clone().add(direction.multiply(0.55D)).subtract(0.0D, 1.15D, 0.0D));
            }
        }, 1L, 1L);
    }

    private void placeBridgeBlock(Arena arena, TeamColor color, Location location) {
        if (arena == null || color == null || location == null || location.getWorld() == null) {
            return;
        }

        org.bukkit.block.Block block = location.getBlock();
        if (block.getType() != Material.AIR) {
            return;
        }

        arena.registerSnapshot(block.getState());
        block.setType(Material.WOOL);
        block.setData((byte) color.getWoolData());
        arena.addPlacedBlock(block.getLocation());
    }

    private void launchBugBomb(Player player, Arena arena, ConfigurationSection reward) {
        TeamColor color = plugin.getTeamManager().getColor(arena, player.getUniqueId());
        if (color == null) {
            return;
        }

        Snowball snowball = player.launchProjectile(Snowball.class);
        snowball.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(1.15D));
        snowball.setMetadata(META_BUG_BOMB, new FixedMetadataValue(plugin, true));
        snowball.setMetadata(META_SUPPORT_TEAM, new FixedMetadataValue(plugin, color.name()));
        snowball.setMetadata(META_SPECIAL_OWNER, new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        snowball.setMetadata(META_SUPPORT_DURATION, new FixedMetadataValue(plugin, reward == null ? 15 : reward.getInt("duration-seconds", 15)));
    }

    private void spawnBugBombMob(Projectile projectile) {
        if (projectile == null || projectile.getWorld() == null) {
            return;
        }

        Player owner = getExplosiveOwner(projectile);
        if (owner == null) {
            return;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(owner.getUniqueId());
        if (arena == null || arena.getState() != ArenaState.INGAME) {
            return;
        }

        TeamColor color = plugin.getTeamManager().getColor(arena, owner.getUniqueId());
        if (color == null) {
            return;
        }

        Location spawnLocation = findMobSpawnLocation(projectile.getLocation(), false);
        if (spawnLocation == null) {
            return;
        }

        Silverfish silverfish = projectile.getWorld().spawn(spawnLocation, Silverfish.class);
        silverfish.setCustomNameVisible(false);
        silverfish.setMetadata(META_SPECIAL_OWNER, new FixedMetadataValue(plugin, owner.getUniqueId().toString()));
        int durationSeconds = projectile.hasMetadata(META_SUPPORT_DURATION)
            ? projectile.getMetadata(META_SUPPORT_DURATION).get(0).asInt()
            : 15;
        startSupportMobTask(silverfish, arena, color, durationSeconds, 12.0D);
    }

    private void spawnIronGolem(Player player, Arena arena, PlayerInteractEvent event, ConfigurationSection reward) {
        TeamColor color = plugin.getTeamManager().getColor(arena, player.getUniqueId());
        if (color == null) {
            return;
        }

        Location preferred = event.getClickedBlock() == null
            ? player.getLocation().add(player.getLocation().getDirection().normalize())
            : event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5D, 0.0D, 0.5D);
        Location spawnLocation = findMobSpawnLocation(preferred, true);
        if (spawnLocation == null) {
            return;
        }

        IronGolem golem = player.getWorld().spawn(spawnLocation, IronGolem.class);
        try {
            golem.setPlayerCreated(true);
        } catch (Throwable ignored) {
        }
        golem.setCustomNameVisible(false);
        golem.setMetadata(META_SPECIAL_OWNER, new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        removeOneFromHand(player);
        startSupportMobTask(golem, arena, color, reward == null ? 240 : reward.getInt("duration-seconds", 240), 18.0D);
    }

    private Location findMobSpawnLocation(Location preferred, boolean requireLargeSpace) {
        if (preferred == null || preferred.getWorld() == null) {
            return null;
        }

        Location base = preferred.getBlock().getLocation();
        for (int y = -1; y <= 2; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Location candidate = base.clone().add(x, y, z).add(0.5D, 0.0D, 0.5D);
                    if (isFreeMobSpace(candidate, requireLargeSpace)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean isFreeMobSpace(Location location, boolean requireLargeSpace) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        org.bukkit.block.Block feet = location.getBlock();
        org.bukkit.block.Block head = feet.getRelative(org.bukkit.block.BlockFace.UP);
        org.bukkit.block.Block top = head.getRelative(org.bukkit.block.BlockFace.UP);
        org.bukkit.block.Block floor = feet.getRelative(org.bukkit.block.BlockFace.DOWN);
        if (feet.getType() != Material.AIR || head.getType() != Material.AIR) {
            return false;
        }

        if (requireLargeSpace && top.getType() != Material.AIR) {
            return false;
        }

        return floor.getType().isSolid();
    }

    private void startSupportMobTask(final Creature creature, final Arena arena, final TeamColor teamColor, final int durationSeconds, final double range) {
        if (creature == null || arena == null || teamColor == null) {
            return;
        }

        creature.setMetadata(META_SUPPORT_TEAM, new FixedMetadataValue(plugin, teamColor.name()));
        final long expireAt = System.currentTimeMillis() + (durationSeconds * 1000L);
        final int[] taskId = new int[1];
        taskId[0] = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (creature == null || !creature.isValid() || creature.isDead() || arena.getState() != ArenaState.INGAME) {
                    if (creature != null && creature.isValid()) {
                        creature.remove();
                    }
                    plugin.getServer().getScheduler().cancelTask(taskId[0]);
                    return;
                }

                if (System.currentTimeMillis() >= expireAt) {
                    creature.remove();
                    plugin.getServer().getScheduler().cancelTask(taskId[0]);
                    return;
                }

                Player target = findNearestEnemyPlayer(arena, teamColor, creature.getLocation(), range);
                creature.setTarget(target);
            }
        }, 1L, 10L);
    }

    private Player findNearestEnemyPlayer(Arena arena, TeamColor teamColor, Location source, double range) {
        if (arena == null || teamColor == null || source == null || source.getWorld() == null) {
            return null;
        }

        double bestDistance = range * range;
        Player best = null;
        for (UUID uniqueId : arena.getPlayers()) {
            if (arena.getSpectators().contains(uniqueId) || plugin.getGameManager().isRespawning(uniqueId)) {
                continue;
            }

            if (teamColor == plugin.getTeamManager().getColor(arena, uniqueId)) {
                continue;
            }

            Player player = plugin.getServer().getPlayer(uniqueId);
            if (player == null || !player.isOnline() || !player.getWorld().getName().equalsIgnoreCase(source.getWorld().getName())) {
                continue;
            }

            double distance = player.getLocation().distanceSquared(source);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private boolean canSupportMobHit(Entity damager, Player target) {
        if (damager == null || target == null || !damager.hasMetadata(META_SUPPORT_TEAM)) {
            return false;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(target.getUniqueId());
        if (arena == null || arena.getState() != ArenaState.INGAME) {
            return false;
        }

        if (arena.getSpectators().contains(target.getUniqueId()) || plugin.getGameManager().isRespawning(target.getUniqueId())) {
            return false;
        }

        TeamColor supportTeam;
        try {
            supportTeam = TeamColor.valueOf(damager.getMetadata(META_SUPPORT_TEAM).get(0).asString());
        } catch (Exception ignored) {
            return false;
        }

        TeamColor targetTeam = plugin.getTeamManager().getColor(arena, target.getUniqueId());
        return targetTeam != null && supportTeam != targetTeam;
    }

    private boolean isSupportMob(Entity entity) {
        return entity instanceof Creature && entity != null && entity.hasMetadata(META_SUPPORT_TEAM);
    }

    private Material parseMaterial(String raw, Material fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }

        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private String strip(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return "";
        }
        return strip(item.getItemMeta().getDisplayName());
    }

    private String strip(String text) {
        return org.bukkit.ChatColor.stripColor(ChatUtil.color(text == null ? "" : text));
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

    private void startFireballCooldown(UUID uniqueId) {
        if (uniqueId == null) {
            return;
        }

        int cooldownSeconds = Math.max(0, plugin.getConfig().getInt("special-items.fireball.cooldown-seconds", 3));
        if (cooldownSeconds <= 0) {
            fireballCooldowns.remove(uniqueId);
            return;
        }

        fireballCooldowns.put(uniqueId, System.currentTimeMillis() + (cooldownSeconds * 1000L));
    }

    private long getRemainingFireballCooldownMillis(UUID uniqueId) {
        if (uniqueId == null) {
            return 0L;
        }

        Long expireAt = fireballCooldowns.get(uniqueId);
        if (expireAt == null) {
            return 0L;
        }

        long remaining = expireAt.longValue() - System.currentTimeMillis();
        if (remaining <= 0L) {
            fireballCooldowns.remove(uniqueId);
            return 0L;
        }

        return remaining;
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
            if (owner != null && !self) {
                plugin.getGameManager().tagCombat(owner, target);
            }
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
            trackFallDamageProtection(target, configPath);
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
            if (owner != null && !self) {
                plugin.getGameManager().tagCombat(owner, target);
            }

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
            trackFallDamageProtection(target, configPath);
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

        Iterator<Map.Entry<UUID, FallDamageProtection>> protectionIterator = fallDamageProtections.entrySet().iterator();
        while (protectionIterator.hasNext()) {
            if (protectionIterator.next().getValue().expireAt < now) {
                protectionIterator.remove();
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

    private void trackFallDamageProtection(Player player, String configPath) {
        if (player == null || configPath == null || !isFallDamageCapEnabled(configPath)) {
            return;
        }

        cleanupBlasts();
        int durationTicks = Math.max(1, plugin.getConfig().getInt(configPath + ".fall-damage-cap.duration-ticks", 120));
        double minimumHearts = Math.max(0.5D, plugin.getConfig().getDouble(configPath + ".fall-damage-cap.minimum-hearts", 5.0D));
        long expireAt = System.currentTimeMillis() + (durationTicks * 50L);
        fallDamageProtections.put(player.getUniqueId(), new FallDamageProtection(configPath, minimumHearts, expireAt));
    }

    private FallDamageProtection consumeFallDamageProtection(UUID uniqueId) {
        if (uniqueId == null) {
            return null;
        }

        cleanupBlasts();
        return fallDamageProtections.remove(uniqueId);
    }

    private boolean isFallDamageCapEnabled(String configPath) {
        return plugin.getConfig().getBoolean(configPath + ".fall-damage-cap.enabled", false);
    }

    private static final class FallDamageProtection {

        private final String configPath;
        private final double minimumHearts;
        private final long expireAt;

        private FallDamageProtection(String configPath, double minimumHearts, long expireAt) {
            this.configPath = configPath;
            this.minimumHearts = minimumHearts;
            this.expireAt = expireAt;
        }
    }
}

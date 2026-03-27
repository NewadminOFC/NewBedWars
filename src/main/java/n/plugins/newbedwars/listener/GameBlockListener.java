package n.plugins.newbedwars.listener;

import java.util.Locale;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaState;
import n.plugins.newbedwars.arena.ArenaTeam;
import n.plugins.newbedwars.arena.TeamColor;
import n.plugins.newbedwars.util.LocationUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class GameBlockListener implements Listener {

    private static final String META_TNT_INITIAL_FUSE = "newbedwars_tnt_initial_fuse";
    private final NewBedWars plugin;

    public GameBlockListener(NewBedWars plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Arena arena = plugin.getArenaManager().getArenaByPlayer(event.getPlayer().getUniqueId());
        if (arena == null) {
            return;
        }

        if (arena.getState() != ArenaState.INGAME
            || arena.getSpectators().contains(event.getPlayer().getUniqueId())
            || plugin.getGameManager().isRespawning(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (event.getBlockPlaced().getType() == Material.TNT) {
            event.setCancelled(true);
            removeOneFromHand(event.getPlayer());
            spawnPrimedTnt(event.getPlayer(), event.getBlockPlaced().getLocation());
            return;
        }

        arena.registerSnapshot(event.getBlockReplacedState());
        arena.addPlacedBlock(event.getBlockPlaced().getLocation());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (arena == null) {
            return;
        }

        if (arena.getState() != ArenaState.INGAME
            || arena.getSpectators().contains(player.getUniqueId())
            || plugin.getGameManager().isRespawning(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        Block block = event.getBlock();
        if (isShopMarker(arena, block)) {
            event.setCancelled(true);
            return;
        }

        ArenaTeam bedTeam = plugin.getTeamManager().getTeamByBedLocation(arena, block.getLocation());
        if (bedTeam != null && !bedTeam.isBedDestroyed()) {
            TeamColor playerColor = plugin.getTeamManager().getColor(arena, player.getUniqueId());
            if (playerColor == bedTeam.getColor()) {
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true);
            event.setExpToDrop(0);
            destroyBed(arena, bedTeam, player);
            return;
        }

        if (!arena.isPlacedBlock(block.getLocation())) {
            event.setCancelled(true);
            return;
        }

        arena.registerSnapshot(block.getState());
        arena.removePlacedBlock(block.getLocation());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (arena == null
            || arena.getState() != ArenaState.INGAME
            || arena.getSpectators().contains(player.getUniqueId())
            || plugin.getGameManager().isRespawning(player.getUniqueId())) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        ArenaTeam blockTeam = getTeamByTeamChest(arena, block);
        if (blockTeam != null) {
            event.setCancelled(true);
            if (plugin.getTeamManager().getColor(arena, player.getUniqueId()) != blockTeam.getColor()) {
                return;
            }

            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                storeHandItemInTeamChest(player, block);
            } else {
                openTeamChest(player, block);
            }
            return;
        }

        ArenaTeam enderTeam = getTeamByEnderChest(arena, block);
        if (enderTeam != null) {
            event.setCancelled(true);
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                storeHandItemInInventory(player, player.getEnderChest());
            } else {
                player.openInventory(player.getEnderChest());
            }
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (isItemShop(arena, block)) {
            event.setCancelled(true);
            plugin.getMenuManager().openItemShop(player);
        } else if (isUpgradeShop(arena, block)) {
            event.setCancelled(true);
            plugin.getMenuManager().openUpgradeShop(player);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        filterExplosion(event.blockList(), event.getLocation());
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        filterExplosion(event.blockList(), event.getBlock().getLocation());
    }

    private boolean isShopMarker(Arena arena, Block block) {
        return isItemShop(arena, block) || isUpgradeShop(arena, block);
    }

    private ArenaTeam getTeamByTeamChest(Arena arena, Block block) {
        for (TeamColor color : plugin.getTeamManager().getActiveColors()) {
            ArenaTeam team = arena.getTeam(color);
            if (team == null) {
                continue;
            }

            if (LocationUtil.sameBlock(arena.getMatchLocation(team.getTeamChestLocation()), block.getLocation())) {
                return team;
            }
        }
        return null;
    }

    private ArenaTeam getTeamByEnderChest(Arena arena, Block block) {
        for (TeamColor color : plugin.getTeamManager().getActiveColors()) {
            ArenaTeam team = arena.getTeam(color);
            if (team == null) {
                continue;
            }

            if (LocationUtil.sameBlock(arena.getMatchLocation(team.getEnderChestLocation()), block.getLocation())) {
                return team;
            }
        }
        return null;
    }

    private boolean isItemShop(Arena arena, Block block) {
        for (TeamColor color : plugin.getTeamManager().getActiveColors()) {
            ArenaTeam team = arena.getTeam(color);
            if (LocationUtil.sameBlock(arena.getMatchLocation(team.getItemShopLocation()), block.getLocation())) {
                return true;
            }
        }
        return false;
    }

    private boolean isUpgradeShop(Arena arena, Block block) {
        for (TeamColor color : plugin.getTeamManager().getActiveColors()) {
            ArenaTeam team = arena.getTeam(color);
            if (LocationUtil.sameBlock(arena.getMatchLocation(team.getUpgradeShopLocation()), block.getLocation())) {
                return true;
            }
        }
        return false;
    }

    private void filterExplosion(java.util.List<Block> blocks, org.bukkit.Location sourceLocation) {
        if (blocks == null || blocks.isEmpty() || sourceLocation == null) {
            return;
        }

        Arena sourceArena = findArenaByWorld(sourceLocation.getWorld().getName());
        if (sourceArena == null || sourceArena.getState() != ArenaState.INGAME) {
            return;
        }

        java.util.Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (block == null) {
                iterator.remove();
                continue;
            }

            if (isShopMarker(sourceArena, block)) {
                iterator.remove();
                continue;
            }

            ArenaTeam bedTeam = plugin.getTeamManager().getTeamByBedLocation(sourceArena, block.getLocation());
            if (bedTeam != null && !bedTeam.isBedDestroyed()) {
                iterator.remove();
                continue;
            }

            if (!sourceArena.isPlacedBlock(block.getLocation())) {
                iterator.remove();
                continue;
            }

            sourceArena.registerSnapshot(block.getState());
            sourceArena.removePlacedBlock(block.getLocation());
        }
    }

    private Arena findArenaByWorld(String worldName) {
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            if (arena.getMatchWorld() == null) {
                continue;
            }

            if (arena.getMatchWorld().getName().equalsIgnoreCase(worldName)) {
                return arena;
            }
        }
        return null;
    }

    private void destroyBed(final Arena arena, final ArenaTeam team, final Player breaker) {
        plugin.getGameManager().markBedDestroyed(arena, team, breaker);
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (team.getBedData() == null) {
                    return;
                }

                if (team.getBedData().getHead() != null) {
                    org.bukkit.Location headLocation = arena.getMatchLocation(team.getBedData().getHead());
                    if (headLocation != null) {
                        Block head = headLocation.getBlock();
                        arena.registerSnapshot(head.getState());
                        removeBlockSilently(head);
                    }
                }
                if (team.getBedData().getFoot() != null) {
                    org.bukkit.Location footLocation = arena.getMatchLocation(team.getBedData().getFoot());
                    if (footLocation != null) {
                        Block foot = footLocation.getBlock();
                        arena.registerSnapshot(foot.getState());
                        removeBlockSilently(foot);
                    }
                }
            }
        });
    }

    private void spawnPrimedTnt(Player player, org.bukkit.Location blockLocation) {
        if (player == null || blockLocation == null || blockLocation.getWorld() == null) {
            return;
        }

        TNTPrimed tnt = (TNTPrimed) blockLocation.getWorld().spawn(blockLocation.clone().add(0.5D, 0.0D, 0.5D), TNTPrimed.class);
        int fuseTicks = plugin.getConfig().getInt("special-items.tnt.fuse-ticks", 40);
        tnt.setFuseTicks(fuseTicks);
        tnt.setYield((float) plugin.getConfig().getDouble("special-items.tnt.yield", 4.0D));
        tnt.setIsIncendiary(plugin.getConfig().getBoolean("special-items.tnt.incendiary", false));
        tnt.setMetadata(GameItemListener.META_SPECIAL_TNT, new FixedMetadataValue(plugin, true));
        tnt.setMetadata(GameItemListener.META_SPECIAL_OWNER, new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        tnt.setMetadata(GameItemListener.META_TNT_UNLOCKED, new FixedMetadataValue(plugin, false));
        tnt.setMetadata(META_TNT_INITIAL_FUSE, new FixedMetadataValue(plugin, fuseTicks));
        tnt.setVelocity(new org.bukkit.util.Vector(0.0D, 0.0D, 0.0D));

        if (plugin.getConfig().getBoolean("special-items.tnt.timer-enabled", true)) {
            startTntTimerTask(tnt);
        }
    }

    private void startTntTimerTask(final TNTPrimed tnt) {
        final int[] taskId = new int[1];
        taskId[0] = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (tnt == null || !tnt.isValid() || tnt.isDead()) {
                    plugin.getServer().getScheduler().cancelTask(taskId[0]);
                    return;
                }

                int initialFuse = readInitialFuseTicks(tnt);
                double remainingPercent = initialFuse <= 0 ? 0.0D : Math.max(0.0D, Math.min(1.0D, tnt.getFuseTicks() / (double) initialFuse));
                String color = remainingPercent > 0.5D ? "&a" : remainingPercent > 0.25D ? "&e" : "&c";
                String seconds = String.format(Locale.US, "%.2f", Math.max(0.0D, tnt.getFuseTicks() / 20.0D));
                String format = plugin.getConfig().getString("special-items.tnt.timer-format", "%color%%seconds%s");
                if (!format.contains("%color%") && ("&c%seconds%s".equalsIgnoreCase(format) || "%seconds%s".equalsIgnoreCase(format))) {
                    format = "%color%%seconds%s";
                }
                String text = format.replace("%color%", color).replace("%seconds%", seconds);
                if (!isTntUnlocked(tnt)) {
                    tnt.setVelocity(new org.bukkit.util.Vector(0.0D, 0.0D, 0.0D));
                }
                tnt.setCustomNameVisible(true);
                tnt.setCustomName(n.plugins.newbedwars.util.ChatUtil.color(text));
            }
        }, 0L, 1L);
    }

    private int readInitialFuseTicks(TNTPrimed tnt) {
        if (tnt == null || !tnt.hasMetadata(META_TNT_INITIAL_FUSE)) {
            return plugin.getConfig().getInt("special-items.tnt.fuse-ticks", 40);
        }

        try {
            return tnt.getMetadata(META_TNT_INITIAL_FUSE).get(0).asInt();
        } catch (Exception ignored) {
            return plugin.getConfig().getInt("special-items.tnt.fuse-ticks", 40);
        }
    }

    private boolean isTntUnlocked(TNTPrimed tnt) {
        if (tnt == null || !tnt.hasMetadata(GameItemListener.META_TNT_UNLOCKED)) {
            return false;
        }

        try {
            return tnt.getMetadata(GameItemListener.META_TNT_UNLOCKED).get(0).asBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private void removeBlockSilently(Block block) {
        if (block == null) {
            return;
        }

        try {
            block.setTypeIdAndData(0, (byte) 0, false);
        } catch (Throwable ignored) {
            block.setType(Material.AIR);
        }
    }

    private void removeOneFromHand(Player player) {
        if (player == null) {
            return;
        }

        org.bukkit.inventory.ItemStack hand = player.getItemInHand();
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

    private void openTeamChest(Player player, Block block) {
        if (player == null || block == null || !(block.getState() instanceof Chest)) {
            return;
        }

        player.openInventory(((Chest) block.getState()).getInventory());
    }

    private void storeHandItemInTeamChest(Player player, Block block) {
        if (player == null || block == null || !(block.getState() instanceof Chest)) {
            return;
        }

        Chest chestState = (Chest) block.getState();
        storeHandItemInInventory(player, chestState.getInventory());
        chestState.update();
    }

    private void storeHandItemInInventory(Player player, Inventory inventory) {
        if (player == null || inventory == null) {
            return;
        }

        ItemStack hand = player.getItemInHand();
        if (hand == null || hand.getType() == Material.AIR || isSword(hand.getType())) {
            return;
        }

        ItemStack toStore = hand.clone();
        java.util.HashMap<Integer, ItemStack> leftovers = inventory.addItem(toStore);
        if (leftovers.isEmpty()) {
            player.setItemInHand(null);
        } else {
            ItemStack leftover = leftovers.values().iterator().next();
            player.setItemInHand(leftover);
        }

        player.updateInventory();
    }

    private boolean isSword(Material material) {
        return material == Material.WOOD_SWORD
            || material == Material.STONE_SWORD
            || material == Material.IRON_SWORD
            || material == Material.DIAMOND_SWORD
            || material == Material.GOLD_SWORD;
    }
}

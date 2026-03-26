package n.plugins.newbedwars.listener;

import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaState;
import n.plugins.newbedwars.arena.ArenaTeam;
import n.plugins.newbedwars.arena.TeamColor;
import n.plugins.newbedwars.util.LocationUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class GameBlockListener implements Listener {

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
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
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
                        head.setType(Material.AIR);
                    }
                }
                if (team.getBedData().getFoot() != null) {
                    org.bukkit.Location footLocation = arena.getMatchLocation(team.getBedData().getFoot());
                    if (footLocation != null) {
                        Block foot = footLocation.getBlock();
                        arena.registerSnapshot(foot.getState());
                        foot.setType(Material.AIR);
                    }
                }
            }
        });
    }
}

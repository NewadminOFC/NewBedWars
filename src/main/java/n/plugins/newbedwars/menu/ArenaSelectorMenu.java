package n.plugins.newbedwars.menu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.BedWarsMode;
import n.plugins.newbedwars.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public class ArenaSelectorMenu extends BaseMenu {

    private final Map<Integer, String> arenaBySlot;
    private final BedWarsMode mode;

    public ArenaSelectorMenu(NewBedWars plugin) {
        this(plugin, BedWarsMode.ONE_VS_ONE);
    }

    public ArenaSelectorMenu(NewBedWars plugin, BedWarsMode mode) {
        super(plugin);
        this.arenaBySlot = new HashMap<Integer, String>();
        this.mode = mode == null ? BedWarsMode.ONE_VS_ONE : mode;
    }

    @Override
    protected String getTitle() {
        return text("menus.arena-selector.title", placeholders("mode", mode.getDisplayName()));
    }

    @Override
    protected int getSize() {
        return 54;
    }

    @Override
    protected void draw(Player player) {
        arenaBySlot.clear();
        List<Arena> arenas = plugin.getGameManager().getJoinableArenas(mode);
        if (arenas.isEmpty()) {
            inventory.setItem(22, new ItemBuilder(Material.BARRIER)
                .name(text("menus.arena-selector.empty.name"))
                .lore(textList("menus.arena-selector.empty.lore", placeholders("mode", mode.getDisplayName())))
                .build());
            inventory.setItem(49, new ItemBuilder(Material.ARROW).name(text("menus.common.back")).build());
            return;
        }

        int slot = 10;
        for (Arena arena : arenas) {
            if (slot >= inventory.getSize()) {
                break;
            }

            int mapPlayers = plugin.getArenaManager().countPlayersForTemplate(arena.getTemplateName());
            int openInstances = plugin.getArenaManager().countOpenInstances(arena.getTemplateName());
            inventory.setItem(slot, new ItemBuilder(Material.MAP)
                .name(text("menus.arena-selector.entry.name", placeholders("arena", arena.getDisplayName())))
                .lore(textList("menus.arena-selector.entry.lore", placeholders(
                    "mode", arena.getMode().getDisplayName(),
                    "world", arena.getWorldName(),
                    "players_on_map", String.valueOf(mapPlayers),
                    "open_instances", String.valueOf(Math.max(1, openInstances)),
                    "max_players", String.valueOf(arena.getMode().getMaxPlayers()),
                    "ready", arena.isReady() ? text("menus.common.yes") : text("menus.common.no")
                ))).build());
            arenaBySlot.put(Integer.valueOf(slot), arena.getName());
            slot++;
            if (slot % 9 == 8) {
                slot += 2;
            }
        }

        inventory.setItem(49, new ItemBuilder(Material.ARROW).name(text("menus.common.back")).build());
    }

    @Override
    public void handleClick(Player player, int slot, ClickType clickType) {
        if (slot == 49) {
            plugin.getMenuManager().openQueueMenu(player, mode);
            return;
        }

        String arenaName = arenaBySlot.get(Integer.valueOf(slot));
        if (arenaName == null) {
            return;
        }

        Arena arena = plugin.getArenaManager().getConfiguredArena(arenaName);
        if (arena != null) {
            player.closeInventory();
            plugin.getGameManager().joinArena(player, arena);
        }
    }
}

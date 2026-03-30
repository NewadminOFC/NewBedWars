package n.plugins.newbedwars.menu;

import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.BedWarsMode;
import n.plugins.newbedwars.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public class SoloQueueMenu extends BaseMenu {

    private final BedWarsMode mode;

    public SoloQueueMenu(NewBedWars plugin) {
        this(plugin, BedWarsMode.ONE_VS_ONE);
    }

    public SoloQueueMenu(NewBedWars plugin, BedWarsMode mode) {
        super(plugin);
        this.mode = mode == null ? BedWarsMode.ONE_VS_ONE : mode;
    }

    @Override
    protected String getTitle() {
        return text("menus.queue.title", placeholders("mode", mode.getDisplayName()));
    }

    @Override
    protected int getSize() {
        return 27;
    }

    @Override
    protected void draw(Player player) {
        inventory.setItem(11, new ItemBuilder(Material.BED)
            .name(text("menus.queue.quick-join.name"))
            .lore(textList("menus.queue.quick-join.lore", placeholders("mode", mode.getDisplayName()))).glow().build());

        inventory.setItem(15, new ItemBuilder(Material.MAP)
            .name(text("menus.queue.selector.name"))
            .lore(textList("menus.queue.selector.lore", placeholders("mode", mode.getDisplayName()))).build());
    }

    @Override
    public void handleClick(Player player, int slot, ClickType clickType) {
        if (slot == 11) {
            player.closeInventory();
            plugin.getGameManager().quickJoin(player, mode);
        } else if (slot == 15) {
            plugin.getMenuManager().openArenaSelectorMenu(player, mode);
        }
    }
}

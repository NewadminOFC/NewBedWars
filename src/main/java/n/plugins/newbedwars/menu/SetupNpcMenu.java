package n.plugins.newbedwars.menu;

import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.TeamColor;
import n.plugins.newbedwars.npc.BedWarsNpcType;
import n.plugins.newbedwars.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public class SetupNpcMenu extends BaseMenu {

    private final Arena arena;
    private final TeamColor color;
    private final BedWarsNpcType type;

    public SetupNpcMenu(NewBedWars plugin, Arena arena, TeamColor color, BedWarsNpcType type) {
        super(plugin);
        this.arena = arena;
        this.color = color;
        this.type = type;
    }

    @Override
    protected String getTitle() {
        return text("menus.setup-npc.title", placeholders("type", type.getDisplayName()));
    }

    @Override
    protected int getSize() {
        return 27;
    }

    @Override
    protected void draw(Player player) {
        inventory.setItem(11, new ItemBuilder(Material.BARRIER)
            .name(text("menus.setup-npc.remove.name"))
            .lore(textList("menus.setup-npc.remove.lore", placeholders(
                "type", type.getDisplayName(),
                "team", color.getColoredName()
            ))).build());
        inventory.setItem(15, new ItemBuilder(Material.ARROW)
            .name(text("menus.common.back-green"))
            .lore(textList("menus.setup-npc.back.lore")).build());
    }

    @Override
    public void handleClick(Player player, int slot, ClickType clickType) {
        if (slot == 11) {
            plugin.getSetupManager().removeTeamNpc(player, arena, color, type);
            return;
        }

        if (slot == 15) {
            plugin.getMenuManager().openTeamSetupMenu(player, arena, color);
        }
    }
}

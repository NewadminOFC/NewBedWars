package n.plugins.newbedwars.menu;

import java.util.Collections;
import java.util.List;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public class SetupConfirmMenu extends BaseMenu {

    private final Arena arena;

    public SetupConfirmMenu(NewBedWars plugin, Arena arena) {
        super(plugin);
        this.arena = arena;
    }

    @Override
    protected String getTitle() {
        return text("menus.setup-confirm.title", placeholders("arena", arena.getName()));
    }

    @Override
    protected int getSize() {
        return 27;
    }

    @Override
    protected void draw(Player player) {
        List<String> issues = arena.validateSetup();
        inventory.setItem(11, new ItemBuilder(Material.WOOL, 1, (short) 5)
            .name(text("menus.setup-confirm.confirm.name"))
            .lore(textList("menus.setup-confirm.confirm.lore", placeholders(
                "status", issues.isEmpty() ? text("menus.setup-confirm.confirm.ready") : text("menus.setup-confirm.confirm.pending")
            ))).glow().build());
        inventory.setItem(13, new ItemBuilder(Material.PAPER)
            .name(text("menus.setup-confirm.validation.name"))
            .lore(textList("menus.setup-confirm.validation.lore", placeholders(
                "arena", arena.getName(),
                "issues", String.valueOf(issues.size()),
                "status", issues.isEmpty() ? text("menus.setup-confirm.validation.none") : text("menus.setup-confirm.validation.review")
            ))).build());
        inventory.setItem(15, new ItemBuilder(Material.WOOL, 1, (short) 14)
            .name(text("menus.setup-confirm.cancel.name"))
            .lore(textList("menus.setup-confirm.cancel.lore")).build());
    }

    @Override
    public void handleClick(Player player, int slot, ClickType clickType) {
        if (slot == 15) {
            plugin.getMenuManager().openSetupMainMenu(player, arena);
            return;
        }

        if (slot != 11) {
            return;
        }

        List<String> issues = arena.validateSetup();
        if (!issues.isEmpty()) {
            plugin.getMessageManager().send(player, "setup.arena-invalid");
            for (String issue : issues) {
                plugin.getMessageManager().send(player, "setup.arena-invalid-entry", Collections.singletonMap("issue", issue));
            }
            plugin.getMenuManager().openSetupMainMenu(player, arena);
            return;
        }

        arena.setReady(true);
        plugin.getArenaManager().saveArena(arena);
        plugin.getMessageManager().send(player, "setup.arena-ready", Collections.singletonMap("arena", arena.getName()));
        plugin.getSetupManager().finishSession(player);
    }
}

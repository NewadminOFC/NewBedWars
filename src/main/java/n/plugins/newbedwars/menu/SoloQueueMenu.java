package n.plugins.newbedwars.menu;

import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public class SoloQueueMenu extends BaseMenu {

    public SoloQueueMenu(NewBedWars plugin) {
        super(plugin);
    }

    @Override
    protected String getTitle() {
        return "\u00A78BedWars - 1v1";
    }

    @Override
    protected int getSize() {
        return 27;
    }

    @Override
    protected void draw(Player player) {
        inventory.setItem(11, new ItemBuilder(Material.BED)
            .name("&bBedWars - 1v1")
            .lore(
                "&7Entre automaticamente",
                "&7na melhor arena disponivel.",
                "",
                "&eClique para jogar agora"
            ).glow().build());

        inventory.setItem(15, new ItemBuilder(Material.MAP)
            .name("&eEscolher Arena")
            .lore(
                "&7Veja as arenas prontas",
                "&7e escolha onde entrar.",
                "",
                "&eClique para abrir"
            ).build());

        inventory.setItem(22, new ItemBuilder(Material.BARRIER).name("&cFechar").build());
    }

    @Override
    public void handleClick(Player player, int slot, ClickType clickType) {
        if (slot == 11) {
            player.closeInventory();
            plugin.getGameManager().quickJoin(player);
        } else if (slot == 15) {
            plugin.getMenuManager().openArenaSelectorMenu(player);
        } else if (slot == 22) {
            player.closeInventory();
        }
    }
}

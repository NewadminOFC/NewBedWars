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
        return "\u00A78BedWars - " + mode.getDisplayName();
    }

    @Override
    protected int getSize() {
        return 27;
    }

    @Override
    protected void draw(Player player) {
        inventory.setItem(11, new ItemBuilder(Material.BED)
            .name("&bBedWars - " + mode.getDisplayName())
            .lore(
                "&7Entre automaticamente",
                "&7na melhor arena disponivel.",
                "&7Times: &f" + mode.getActiveColors().size(),
                "&7Jogadores por time: &f" + mode.getTeamSize(),
                "&7Maximo: &f" + mode.getMaxPlayers(),
                "",
                "&eClique para jogar agora"
            ).glow().build());

        inventory.setItem(15, new ItemBuilder(Material.MAP)
            .name("&eEscolher Arena")
            .lore(
                "&7Veja as arenas prontas",
                "&7e escolha onde entrar.",
                "&7Modo: &f" + mode.getDisplayName(),
                "",
                "&eClique para abrir"
            ).build());

        inventory.setItem(22, new ItemBuilder(Material.BARRIER).name("&cFechar").build());
    }

    @Override
    public void handleClick(Player player, int slot, ClickType clickType) {
        if (slot == 11) {
            player.closeInventory();
            plugin.getGameManager().quickJoin(player, mode);
        } else if (slot == 15) {
            plugin.getMenuManager().openArenaSelectorMenu(player, mode);
        } else if (slot == 22) {
            player.closeInventory();
        }
    }
}

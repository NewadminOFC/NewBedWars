package n.plugins.newbedwars.menu;

import java.util.Arrays;
import java.util.List;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaTeam;
import n.plugins.newbedwars.arena.TeamColor;
import n.plugins.newbedwars.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public class SetupMainMenu extends BaseMenu {

    private static final List<Integer> TEAM_SLOTS = Arrays.asList(11, 15);
    private final Arena arena;

    public SetupMainMenu(NewBedWars plugin, Arena arena) {
        super(plugin);
        this.arena = arena;
    }

    @Override
    protected String getTitle() {
        return "\u00A78Setup " + arena.getName();
    }

    @Override
    protected int getSize() {
        return 27;
    }

    @Override
    protected void draw(Player player) {
        inventory.setItem(4, new ItemBuilder(Material.BOOK)
            .name("&bArena: &f" + arena.getName())
            .lore(
                "&7Modo: &f1v1",
                "&7Spawn de espera: " + status(arena.getWaitingSpawn() != null),
                "&7Area de espera: " + status(arena.getWaitingRegion() != null && arena.getWaitingRegion().isComplete()),
                "&7Diamante: &f" + arena.getGlobalGenerators(n.plugins.newbedwars.arena.GeneratorType.DIAMOND).size(),
                "&7Esmeralda: &f" + arena.getGlobalGenerators(n.plugins.newbedwars.arena.GeneratorType.EMERALD).size(),
                "&7Pronta: " + status(arena.isReady())
            ).build());

        List<TeamColor> activeColors = TeamColor.getOneVsOneColors();
        for (int index = 0; index < activeColors.size() && index < TEAM_SLOTS.size(); index++) {
            TeamColor color = activeColors.get(index);
            ArenaTeam team = arena.getTeam(color);
            inventory.setItem(TEAM_SLOTS.get(index), new ItemBuilder(Material.WOOL, 1, color.getWoolData())
                .name(color.getColoredName())
                .lore(
                    "&7Status: " + status(team.isSetupComplete()),
                    "&7Confirmado: " + status(team.isConfirmed()),
                    "&7" + team.getProgressLine(),
                    "",
                    "&eClique para configurar o time"
                ).build());
        }

        inventory.setItem(20, new ItemBuilder(Material.DIAMOND)
            .name("&bAdicionar gerador de diamante")
            .lore(
                "&7Configurados: &f" + arena.getGlobalGenerators(n.plugins.newbedwars.arena.GeneratorType.DIAMOND).size(),
                "",
                "&eClique para adicionar mais um"
            ).build());
        inventory.setItem(24, new ItemBuilder(Material.EMERALD)
            .name("&aAdicionar gerador de esmeralda")
            .lore(
                "&7Configurados: &f" + arena.getGlobalGenerators(n.plugins.newbedwars.arena.GeneratorType.EMERALD).size(),
                "",
                "&eClique para adicionar mais um"
            ).build());

        inventory.setItem(22, new ItemBuilder(Material.EMERALD_BLOCK)
            .name("&aFinalizar")
            .lore(
                "&71v1 com Vermelho e Azul",
                "&7Valida toda a arena",
                "&7e marca como pronta.",
                "",
                "&eClique para continuar"
            ).glow().build());
    }

    @Override
    public void handleClick(Player player, int slot, ClickType clickType) {
        if (slot == 22) {
            plugin.getMenuManager().openSetupConfirmMenu(player, arena);
            return;
        }

        if (slot == 20) {
            plugin.getSetupManager().beginArenaPointSetup(player, arena, n.plugins.newbedwars.setup.SetupPointAction.ARENA_DIAMOND_GENERATOR);
            return;
        }

        if (slot == 24) {
            plugin.getSetupManager().beginArenaPointSetup(player, arena, n.plugins.newbedwars.setup.SetupPointAction.ARENA_EMERALD_GENERATOR);
            return;
        }

        List<TeamColor> activeColors = TeamColor.getOneVsOneColors();
        for (int index = 0; index < TEAM_SLOTS.size() && index < activeColors.size(); index++) {
            if (TEAM_SLOTS.get(index) == slot) {
                plugin.getMenuManager().openTeamSetupMenu(player, arena, activeColors.get(index));
                return;
            }
        }
    }

    private String status(boolean configured) {
        return configured ? "\u00A7aOK" : "\u00A7cPendente";
    }
}

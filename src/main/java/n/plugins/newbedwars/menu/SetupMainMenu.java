package n.plugins.newbedwars.menu;

import java.util.Arrays;
import java.util.List;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaTeam;
import n.plugins.newbedwars.arena.GeneratorType;
import n.plugins.newbedwars.arena.TeamColor;
import n.plugins.newbedwars.setup.SetupPointAction;
import n.plugins.newbedwars.setup.SetupRegionAction;
import n.plugins.newbedwars.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public class SetupMainMenu extends BaseMenu {

    private static final List<Integer> TEAM_SLOTS = Arrays.asList(14, 16);
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
        return 36;
    }

    @Override
    protected void draw(Player player) {
        inventory.setItem(4, new ItemBuilder(Material.BOOK)
            .name("&bArena: &f" + arena.getName())
            .lore(
                "&7Modo: &f1v1",
                "&7Spawn de espera: " + status(arena.getWaitingSpawn() != null),
                "&7Area de espera: " + status(arena.getWaitingRegion() != null && arena.getWaitingRegion().isComplete()),
                "&7Diamante: &f" + arena.getGlobalGenerators(GeneratorType.DIAMOND).size(),
                "&7Esmeralda: &f" + arena.getGlobalGenerators(GeneratorType.EMERALD).size(),
                "&7Pronta: " + status(arena.isReady())
            ).build());

        inventory.setItem(10, action(Material.NETHER_STAR, "&bSpawn de espera", arena.getWaitingSpawn() != null,
            "&eClique esquerdo para configurar",
            "&cClique direito para limpar"));
        inventory.setItem(12, action(Material.WOOD_AXE, "&eArea de espera", arena.getWaitingRegion() != null && arena.getWaitingRegion().isComplete(),
            "&eClique esquerdo para marcar /pos1 e /pos2",
            "&cClique direito para limpar"));

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

        inventory.setItem(28, action(Material.DIAMOND, "&bGeradores de diamante", !arena.getGlobalGenerators(GeneratorType.DIAMOND).isEmpty(),
            "&eClique esquerdo para adicionar mais um",
            "&cClique direito para limpar todos"));
        inventory.setItem(30, action(Material.EMERALD, "&aGeradores de esmeralda", !arena.getGlobalGenerators(GeneratorType.EMERALD).isEmpty(),
            "&eClique esquerdo para adicionar mais um",
            "&cClique direito para limpar todos"));

        inventory.setItem(32, new ItemBuilder(Material.EMERALD_BLOCK)
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
        if (slot == 32) {
            plugin.getMenuManager().openSetupConfirmMenu(player, arena);
            return;
        }

        if (slot == 10) {
            if (clickType.isRightClick()) {
                plugin.getSetupManager().clearArenaPoint(player, arena, SetupPointAction.ARENA_WAITING_SPAWN);
            } else {
                plugin.getSetupManager().beginArenaPointSetup(player, arena, SetupPointAction.ARENA_WAITING_SPAWN);
            }
            return;
        }

        if (slot == 12) {
            if (clickType.isRightClick()) {
                plugin.getSetupManager().clearArenaRegion(player, arena, SetupRegionAction.WAITING_AREA);
            } else {
                plugin.getSetupManager().beginArenaRegionSetup(player, arena, SetupRegionAction.WAITING_AREA);
            }
            return;
        }

        if (slot == 28) {
            if (clickType.isRightClick()) {
                plugin.getSetupManager().clearArenaPoint(player, arena, SetupPointAction.ARENA_DIAMOND_GENERATOR);
            } else {
                plugin.getSetupManager().beginArenaPointSetup(player, arena, SetupPointAction.ARENA_DIAMOND_GENERATOR);
            }
            return;
        }

        if (slot == 30) {
            if (clickType.isRightClick()) {
                plugin.getSetupManager().clearArenaPoint(player, arena, SetupPointAction.ARENA_EMERALD_GENERATOR);
            } else {
                plugin.getSetupManager().beginArenaPointSetup(player, arena, SetupPointAction.ARENA_EMERALD_GENERATOR);
            }
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

    private ItemStack action(Material material, String title, boolean configured, String leftClick, String rightClick) {
        return new ItemBuilder(material)
            .name(title)
            .lore(
                "&7Status: " + status(configured),
                "",
                leftClick,
                rightClick
            ).build();
    }

    private String status(boolean configured) {
        return configured ? "\u00A7aOK" : "\u00A7cPendente";
    }
}

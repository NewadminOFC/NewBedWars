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

    private final Arena arena;

    public SetupMainMenu(NewBedWars plugin, Arena arena) {
        super(plugin);
        this.arena = arena;
    }

    @Override
    protected String getTitle() {
        return text("menus.setup-main.title", placeholders("arena", arena.getName()));
    }

    @Override
    protected int getSize() {
        return 54;
    }

    @Override
    protected void draw(Player player) {
        inventory.setItem(4, new ItemBuilder(Material.BOOK)
            .name(text("menus.setup-main.info.name", placeholders("arena", arena.getName())))
            .lore(textList("menus.setup-main.info.lore", placeholders(
                "mode", arena.getMode().getDisplayName(),
                "active_teams", String.valueOf(arena.getMode().getActiveColors().size()),
                "team_size", String.valueOf(arena.getMode().getTeamSize()),
                "max_players", String.valueOf(arena.getMode().getMaxPlayers()),
                "waiting_spawn_status", status(arena.getWaitingSpawn() != null),
                "waiting_area_status", status(arena.getWaitingRegion() != null && arena.getWaitingRegion().isComplete()),
                "anti_void_status", antiVoidStatus(),
                "diamond_count", String.valueOf(arena.getGlobalGenerators(GeneratorType.DIAMOND).size()),
                "emerald_count", String.valueOf(arena.getGlobalGenerators(GeneratorType.EMERALD).size()),
                "ready_status", status(arena.isReady())
            ))).build());

        inventory.setItem(10, action(Material.NETHER_STAR, text("menus.setup-main.items.waiting-spawn"), arena.getWaitingSpawn() != null,
            text("menus.common.left-configure"),
            text("menus.common.right-clear")));
        inventory.setItem(12, action(Material.WOOD_AXE, text("menus.setup-main.items.waiting-area"), arena.getWaitingRegion() != null && arena.getWaitingRegion().isComplete(),
            text("menus.setup-main.items.waiting-area-left"),
            text("menus.common.right-clear")));
        inventory.setItem(14, action(Material.FEATHER, text("menus.setup-main.items.anti-void"), arena.hasAntiVoidY(),
            text("menus.setup-main.items.anti-void-left"),
            text("menus.common.right-clear")));
        inventory.setItem(16, new ItemBuilder(Material.NAME_TAG)
            .name(text("menus.setup-main.items.mode.name"))
            .lore(textList("menus.setup-main.items.mode.lore", placeholders(
                "mode", arena.getMode().getDisplayName(),
                "arena", arena.getName()
            ))).build());

        List<TeamColor> activeColors = plugin.getTeamManager().getActiveColors(arena);
        List<Integer> teamSlots = resolveTeamSlots(activeColors.size());
        for (int index = 0; index < activeColors.size() && index < teamSlots.size(); index++) {
            TeamColor color = activeColors.get(index);
            ArenaTeam team = arena.getTeam(color);
            inventory.setItem(teamSlots.get(index).intValue(), new ItemBuilder(Material.WOOL, 1, color.getWoolData())
                .name(color.getColoredName())
                .lore(textList("menus.setup-main.items.team.lore", placeholders(
                    "status", status(team.isSetupComplete()),
                    "confirmed", status(team.isConfirmed()),
                    "progress", team.getProgressLine()
                ))).build());
        }

        inventory.setItem(37, action(Material.DIAMOND, text("menus.setup-main.items.diamond-generators"), !arena.getGlobalGenerators(GeneratorType.DIAMOND).isEmpty(),
            text("menus.setup-main.items.generator-left"),
            text("menus.setup-main.items.generator-right")));
        inventory.setItem(39, action(Material.EMERALD, text("menus.setup-main.items.emerald-generators"), !arena.getGlobalGenerators(GeneratorType.EMERALD).isEmpty(),
            text("menus.setup-main.items.generator-left"),
            text("menus.setup-main.items.generator-right")));
        inventory.setItem(41, buildModeItem(player));

        inventory.setItem(43, new ItemBuilder(Material.EMERALD_BLOCK)
            .name(text("menus.setup-main.items.finish.name"))
            .lore(textList("menus.setup-main.items.finish.lore", placeholders(
                "mode", arena.getMode().getDisplayName(),
                "active_teams", String.valueOf(arena.getMode().getActiveColors().size())
            ))).glow().build());
    }

    @Override
    public void handleClick(Player player, int slot, ClickType clickType) {
        if (slot == 43) {
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

        if (slot == 14) {
            if (clickType.isRightClick()) {
                plugin.getSetupManager().clearArenaPoint(player, arena, SetupPointAction.ARENA_ANTI_VOID);
            } else {
                plugin.getSetupManager().beginArenaPointSetup(player, arena, SetupPointAction.ARENA_ANTI_VOID);
            }
            return;
        }

        if (slot == 37) {
            if (clickType.isRightClick()) {
                plugin.getSetupManager().clearArenaPoint(player, arena, SetupPointAction.ARENA_DIAMOND_GENERATOR);
            } else {
                plugin.getSetupManager().beginArenaPointSetup(player, arena, SetupPointAction.ARENA_DIAMOND_GENERATOR);
            }
            return;
        }

        if (slot == 39) {
            if (clickType.isRightClick()) {
                plugin.getSetupManager().clearArenaPoint(player, arena, SetupPointAction.ARENA_EMERALD_GENERATOR);
            } else {
                plugin.getSetupManager().beginArenaPointSetup(player, arena, SetupPointAction.ARENA_EMERALD_GENERATOR);
            }
            return;
        }

        if (slot == 41) {
            player.closeInventory();
            plugin.getSetupManager().toggleBuildMode(player);
            return;
        }

        List<TeamColor> activeColors = plugin.getTeamManager().getActiveColors(arena);
        List<Integer> teamSlots = resolveTeamSlots(activeColors.size());
        for (int index = 0; index < teamSlots.size() && index < activeColors.size(); index++) {
            if (teamSlots.get(index).intValue() == slot) {
                plugin.getMenuManager().openTeamSetupMenu(player, arena, activeColors.get(index));
                return;
            }
        }
    }

    private ItemStack action(Material material, String title, boolean configured, String leftClick, String rightClick) {
        return new ItemBuilder(material)
            .name(title)
            .lore(textList("menus.setup-main.action.lore", placeholders(
                "status", status(configured),
                "left_click", leftClick,
                "right_click", rightClick
            ))).build();
    }

    private String status(boolean configured) {
        return configured ? text("menus.common.ok") : text("menus.common.pending");
    }

    private String antiVoidStatus() {
        if (!arena.hasAntiVoidY()) {
            return text("menus.common.pending");
        }
        double value = arena.getAntiVoidY().doubleValue();
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 0.001D) {
            return text("menus.setup-main.info.anti-void-value", placeholders("value", String.valueOf((int) rounded)));
        }
        return text("menus.setup-main.info.anti-void-value", placeholders("value", String.format(java.util.Locale.US, "%.1f", value)));
    }

    private ItemStack buildModeItem(Player player) {
        boolean enabled = plugin.getSetupManager().isBuildModeEnabled(player);
        ItemBuilder builder = new ItemBuilder(Material.BRICK)
            .name(text("menus.setup-main.items.build-mode.name"))
            .lore(textList("menus.setup-main.items.build-mode.lore", placeholders(
                "status", status(enabled)
            )));
        if (enabled) {
            builder.glow();
        }
        return builder.build();
    }

    private List<Integer> resolveTeamSlots(int amount) {
        if (amount <= 2) {
            return Arrays.asList(21, 23);
        }
        if (amount <= 4) {
            return Arrays.asList(20, 22, 24, 26);
        }
        return Arrays.asList(19, 20, 21, 22, 23, 24, 25, 26);
    }
}

package n.plugins.newbedwars.menu;

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

public class TeamSetupMenu extends BaseMenu {

    private final Arena arena;
    private final TeamColor color;

    public TeamSetupMenu(NewBedWars plugin, Arena arena, TeamColor color) {
        super(plugin);
        this.arena = arena;
        this.color = color;
    }

    @Override
    protected String getTitle() {
        return text("menus.team-setup.title", placeholders("team", color.getDisplayName()));
    }

    @Override
    protected int getSize() {
        return 36;
    }

    @Override
    protected void draw(Player player) {
        ArenaTeam team = arena.getTeam(color);
        inventory.setItem(10, action(Material.BEACON, text("menus.team-setup.items.spawn"), team.getSpawnLocation() != null));
        inventory.setItem(11, action(Material.BED, text("menus.team-setup.items.bed"), team.getBedData() != null && team.getBedData().isConfigured()));
        inventory.setItem(12, action(Material.CHEST, text("menus.team-setup.items.chest"), team.getTeamChestLocation() != null));
        inventory.setItem(13, action(Material.ENDER_CHEST, text("menus.team-setup.items.ender-chest"), team.getEnderChestLocation() != null));
        inventory.setItem(14, action(Material.IRON_INGOT, text("menus.team-setup.items.iron-generator"), !team.getGenerators(GeneratorType.IRON).isEmpty()));
        inventory.setItem(15, action(Material.GOLD_INGOT, text("menus.team-setup.items.gold-generator"), !team.getGenerators(GeneratorType.GOLD).isEmpty()));
        inventory.setItem(16, action(Material.EMERALD, text("menus.team-setup.items.item-shop"), team.getItemShopLocation() != null));
        inventory.setItem(19, action(Material.ANVIL, text("menus.team-setup.items.upgrade-shop"), team.getUpgradeShopLocation() != null));
        inventory.setItem(20, action(Material.GRASS, text("menus.team-setup.items.island-region"), team.getIslandRegion() != null && team.getIslandRegion().isComplete()));
        inventory.setItem(21, action(Material.OBSIDIAN, text("menus.team-setup.items.protection-region"), team.getProtectionRegion() != null && team.getProtectionRegion().isComplete()));

        List<String> missing = team.getMissingSetup();
        inventory.setItem(23, new ItemBuilder(Material.PAPER)
            .name(text("menus.team-setup.items.progress.name"))
            .lore(textList("menus.team-setup.items.progress.lore", placeholders(
                "team", color.getColoredName(),
                "confirmed", team.isConfirmed() ? text("menus.common.yes") : text("menus.common.no"),
                "overall_status", team.isSetupComplete() ? text("menus.team-setup.status.complete") : text("menus.team-setup.status.incomplete"),
                "missing", missing.isEmpty() ? text("menus.team-setup.status.nothing-missing") : text("menus.team-setup.status.missing-prefix") + join(missing)
            ))).build());

        inventory.setItem(27, new ItemBuilder(Material.ARROW).name(text("menus.common.back")).build());
        inventory.setItem(35, new ItemBuilder(Material.EMERALD).name(text("menus.team-setup.items.confirm")).glow().build());
    }

    @Override
    public void handleClick(Player player, int slot, ClickType clickType) {
        if (slot == 27) {
            plugin.getMenuManager().openSetupMainMenu(player, arena);
            return;
        }

        if (slot == 35) {
            ArenaTeam team = arena.getTeam(color);
            if (team.isSetupComplete()) {
                team.setConfirmed(true);
                plugin.getArenaManager().saveArena(arena);
                plugin.getSetupManager().refreshArenaSetupVisuals(arena);
                plugin.getMessageManager().send(player, "setup.team-confirmed", java.util.Collections.singletonMap("team", color.getColoredName()));
                plugin.getMenuManager().openSetupMainMenu(player, arena);
            } else {
                plugin.getMessageManager().send(player, "setup.team-invalid");
                open(player);
            }
            return;
        }

        if (slot == 10) {
            handlePointAction(player, clickType, SetupPointAction.TEAM_SPAWN);
        } else if (slot == 11) {
            handlePointAction(player, clickType, SetupPointAction.TEAM_BED);
        } else if (slot == 12) {
            handlePointAction(player, clickType, SetupPointAction.TEAM_CHEST);
        } else if (slot == 13) {
            handlePointAction(player, clickType, SetupPointAction.TEAM_ENDER_CHEST);
        } else if (slot == 14) {
            handlePointAction(player, clickType, SetupPointAction.TEAM_IRON_GENERATOR);
        } else if (slot == 15) {
            handlePointAction(player, clickType, SetupPointAction.TEAM_GOLD_GENERATOR);
        } else if (slot == 16) {
            handlePointAction(player, clickType, SetupPointAction.TEAM_ITEM_SHOP);
        } else if (slot == 19) {
            handlePointAction(player, clickType, SetupPointAction.TEAM_UPGRADE_SHOP);
        } else if (slot == 20) {
            handleRegionAction(player, clickType, SetupRegionAction.TEAM_ISLAND);
        } else if (slot == 21) {
            handleRegionAction(player, clickType, SetupRegionAction.TEAM_PROTECTION);
        }
    }

    private void handlePointAction(Player player, ClickType clickType, SetupPointAction action) {
        if (clickType.isRightClick()) {
            plugin.getSetupManager().clearTeamPoint(player, arena, color, action);
            return;
        }
        plugin.getSetupManager().beginPointSetup(player, arena, color, action);
    }

    private void handleRegionAction(Player player, ClickType clickType, SetupRegionAction action) {
        if (clickType.isRightClick()) {
            plugin.getSetupManager().clearTeamRegion(player, arena, color, action);
            return;
        }
        plugin.getSetupManager().beginRegionSetup(player, arena, color, action);
    }

    private ItemStack action(Material material, String title, boolean done) {
        return new ItemBuilder(material)
            .name(title)
            .lore(textList("menus.team-setup.action.lore", placeholders(
                "status", done ? text("menus.team-setup.status.configured") : text("menus.common.pending")
            ))).build();
    }

    private String join(List<String> list) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            builder.append(list.get(i));
            if (i + 1 < list.size()) {
                builder.append(", ");
            }
        }
        return builder.toString();
    }
}

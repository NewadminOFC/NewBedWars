package n.plugins.newbedwars.menu;

import java.util.List;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaTeam;
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
        return "§8Time " + color.getDisplayName();
    }

    @Override
    protected int getSize() {
        return 36;
    }

    @Override
    protected void draw(Player player) {
        ArenaTeam team = arena.getTeam(color);
        inventory.setItem(10, action(Material.BEACON, "&bDefinir spawn do time", team.getSpawnLocation() != null));
        inventory.setItem(11, action(Material.BED, "&cDefinir cama", team.getBedData() != null && team.getBedData().isConfigured()));
        inventory.setItem(12, action(Material.IRON_INGOT, "&fDefinir gerador de ferro", !team.getGenerators(n.plugins.newbedwars.arena.GeneratorType.IRON).isEmpty()));
        inventory.setItem(13, action(Material.GOLD_INGOT, "&6Definir gerador de ouro", !team.getGenerators(n.plugins.newbedwars.arena.GeneratorType.GOLD).isEmpty()));
        inventory.setItem(14, action(Material.CHEST, "&eDefinir loja de itens", team.getItemShopLocation() != null));
        inventory.setItem(15, action(Material.ANVIL, "&bDefinir loja de upgrades", team.getUpgradeShopLocation() != null));
        inventory.setItem(16, action(Material.GRASS, "&aDefinir regiao da ilha", team.getIslandRegion() != null && team.getIslandRegion().isComplete()));
        inventory.setItem(19, action(Material.OBSIDIAN, "&5Definir protecao inicial", team.getProtectionRegion() != null && team.getProtectionRegion().isComplete()));

        List<String> missing = team.getMissingSetup();
        inventory.setItem(22, new ItemBuilder(Material.PAPER)
            .name("&fPreview do progresso")
            .lore(
                "&7Time: " + color.getColoredName(),
                "&7Confirmado: " + (team.isConfirmed() ? "§aSim" : "§cNao"),
                "&7Status geral: " + (team.isSetupComplete() ? "§aCompleto" : "§cIncompleto"),
                "&7Faltando: " + (missing.isEmpty() ? "§aNada" : "§c" + join(missing)),
                "",
                "&eUse o menu para iniciar cada etapa."
            ).build());

        inventory.setItem(27, new ItemBuilder(Material.ARROW).name("&cVoltar").build());
        inventory.setItem(35, new ItemBuilder(Material.EMERALD).name("&aConfirmar time").glow().build());
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
                plugin.getMessageManager().send(player, "setup.team-confirmed", java.util.Collections.singletonMap("team", color.getColoredName()));
                plugin.getMenuManager().openSetupMainMenu(player, arena);
            } else {
                plugin.getMessageManager().send(player, "setup.team-invalid");
                open(player);
            }
            return;
        }

        if (slot == 10) {
            plugin.getSetupManager().beginPointSetup(player, arena, color, SetupPointAction.TEAM_SPAWN);
        } else if (slot == 11) {
            plugin.getSetupManager().beginPointSetup(player, arena, color, SetupPointAction.TEAM_BED);
        } else if (slot == 12) {
            plugin.getSetupManager().beginPointSetup(player, arena, color, SetupPointAction.TEAM_IRON_GENERATOR);
        } else if (slot == 13) {
            plugin.getSetupManager().beginPointSetup(player, arena, color, SetupPointAction.TEAM_GOLD_GENERATOR);
        } else if (slot == 14) {
            plugin.getSetupManager().beginPointSetup(player, arena, color, SetupPointAction.TEAM_ITEM_SHOP);
        } else if (slot == 15) {
            plugin.getSetupManager().beginPointSetup(player, arena, color, SetupPointAction.TEAM_UPGRADE_SHOP);
        } else if (slot == 16) {
            plugin.getSetupManager().beginRegionSetup(player, arena, color, SetupRegionAction.TEAM_ISLAND);
        } else if (slot == 19) {
            plugin.getSetupManager().beginRegionSetup(player, arena, color, SetupRegionAction.TEAM_PROTECTION);
        }
    }

    private ItemStack action(Material material, String title, boolean done) {
        return new ItemBuilder(material)
            .name(title)
            .lore(
                "&7Status: " + (done ? "§aConfigurado" : "§cPendente"),
                "",
                "&eClique para iniciar"
            ).build();
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

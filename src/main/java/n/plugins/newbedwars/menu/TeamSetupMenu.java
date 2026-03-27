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
        return "\u00A78Time " + color.getDisplayName();
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
        inventory.setItem(12, action(Material.CHEST, "&6Definir bau do time", team.getTeamChestLocation() != null));
        inventory.setItem(13, action(Material.ENDER_CHEST, "&5Definir ender chest", team.getEnderChestLocation() != null));
        inventory.setItem(14, action(Material.IRON_INGOT, "&fDefinir gerador de ferro", !team.getGenerators(GeneratorType.IRON).isEmpty()));
        inventory.setItem(15, action(Material.GOLD_INGOT, "&6Definir gerador de ouro", !team.getGenerators(GeneratorType.GOLD).isEmpty()));
        inventory.setItem(16, action(Material.EMERALD, "&eDefinir loja de itens", team.getItemShopLocation() != null));
        inventory.setItem(19, action(Material.ANVIL, "&bDefinir loja de upgrades", team.getUpgradeShopLocation() != null));
        inventory.setItem(20, action(Material.GRASS, "&aDefinir regiao da ilha", team.getIslandRegion() != null && team.getIslandRegion().isComplete()));
        inventory.setItem(21, action(Material.OBSIDIAN, "&5Definir protecao inicial", team.getProtectionRegion() != null && team.getProtectionRegion().isComplete()));

        List<String> missing = team.getMissingSetup();
        inventory.setItem(23, new ItemBuilder(Material.PAPER)
            .name("&fPreview do progresso")
            .lore(
                "&7Time: " + color.getColoredName(),
                "&7Confirmado: " + (team.isConfirmed() ? "\u00A7aSim" : "\u00A7cNao"),
                "&7Status geral: " + (team.isSetupComplete() ? "\u00A7aCompleto" : "\u00A7cIncompleto"),
                "&7Faltando: " + (missing.isEmpty() ? "\u00A7aNada" : "\u00A7c" + join(missing)),
                "",
                "&eEsquerdo: configurar",
                "&cDireito: limpar"
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
            .lore(
                "&7Status: " + (done ? "\u00A7aConfigurado" : "\u00A7cPendente"),
                "",
                "&eClique esquerdo para configurar",
                "&cClique direito para limpar"
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

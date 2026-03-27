package n.plugins.newbedwars.menu;

import java.util.UUID;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaState;
import n.plugins.newbedwars.arena.TeamColor;
import n.plugins.newbedwars.manager.TeamManager;
import n.plugins.newbedwars.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public class TeamSelectorMenu extends BaseMenu {

    private final Arena arena;

    public TeamSelectorMenu(NewBedWars plugin, Arena arena) {
        super(plugin);
        this.arena = arena;
    }

    @Override
    protected String getTitle() {
        return "\u00A78Escolher Time";
    }

    @Override
    protected int getSize() {
        return 27;
    }

    @Override
    protected void draw(Player player) {
        drawTeamItem(player, TeamColor.RED, 11);
        drawTeamItem(player, TeamColor.BLUE, 15);
        inventory.setItem(22, new ItemBuilder(Material.BARRIER)
            .name("&cFechar")
            .build());
    }

    private void drawTeamItem(Player viewer, TeamColor color, int slot) {
        UUID occupant = plugin.getTeamManager().getOccupant(arena, color);
        boolean selected = plugin.getTeamManager().getColor(arena, viewer.getUniqueId()) == color;
        boolean available = occupant == null || occupant.equals(viewer.getUniqueId());

        String occupantName = "&aLivre";
        if (occupant != null && !occupant.equals(viewer.getUniqueId())) {
            Player online = Bukkit.getPlayer(occupant);
            occupantName = online == null ? "&cOcupado" : "&c" + online.getName();
        }

        ItemBuilder builder = new ItemBuilder(Material.WOOL, 1, color.getWoolData())
            .name(color.getChatColor() + "" + org.bukkit.ChatColor.BOLD + color.getDisplayName())
            .lore(
                selected ? "&aVoce ja esta neste time." : available ? "&aDisponivel para escolher." : "&cTime ocupado.",
                "&7Jogador: " + occupantName,
                "",
                selected ? "&eTime atual" : available ? "&eClique para entrar" : "&cIndisponivel"
            );

        if (selected) {
            builder.glow();
        }

        inventory.setItem(slot, builder.build());
    }

    @Override
    public void handleClick(Player player, int slot, ClickType clickType) {
        if (slot == 22) {
            player.closeInventory();
            return;
        }

        TeamColor color = slot == 11 ? TeamColor.RED : slot == 15 ? TeamColor.BLUE : null;
        if (color == null) {
            return;
        }

        if (arena == null || plugin.getArenaManager().getArenaByPlayer(player.getUniqueId()) != arena) {
            player.closeInventory();
            return;
        }

        if (arena.getState() != ArenaState.WAITING && arena.getState() != ArenaState.STARTING) {
            player.closeInventory();
            return;
        }

        TeamManager.TeamSelectionResult result = plugin.getTeamManager().selectTeam(arena, player.getUniqueId(), color);
        if (result == TeamManager.TeamSelectionResult.SUCCESS) {
            plugin.getGameManager().giveWaitingLobbyItems(player);
            plugin.getMessageManager().send(player, "game.team-selector-selected",
                java.util.Collections.singletonMap("team", color.getColoredName()));
            player.closeInventory();
            return;
        }

        if (result == TeamManager.TeamSelectionResult.ALREADY_SELECTED) {
            plugin.getMessageManager().send(player, "game.team-selector-already",
                java.util.Collections.singletonMap("team", color.getColoredName()));
        } else if (result == TeamManager.TeamSelectionResult.TEAM_OCCUPIED) {
            plugin.getMessageManager().send(player, "game.team-selector-occupied",
                java.util.Collections.singletonMap("team", color.getColoredName()));
        }

        draw(player);
        player.updateInventory();
    }
}

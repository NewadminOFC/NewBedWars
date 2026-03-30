package n.plugins.newbedwars.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final Map<Integer, TeamColor> colorsBySlot;

    public TeamSelectorMenu(NewBedWars plugin, Arena arena) {
        super(plugin);
        this.arena = arena;
        this.colorsBySlot = new HashMap<Integer, TeamColor>();
    }

    @Override
    protected String getTitle() {
        return text("menus.team-selector.title");
    }

    @Override
    protected int getSize() {
        return 45;
    }

    @Override
    protected void draw(Player player) {
        colorsBySlot.clear();

        List<TeamColor> activeColors = plugin.getTeamManager().getActiveColors(arena);
        List<Integer> slots = resolveTeamSlots(activeColors.size());
        for (int index = 0; index < activeColors.size() && index < slots.size(); index++) {
            TeamColor color = activeColors.get(index);
            int slot = slots.get(index).intValue();
            colorsBySlot.put(Integer.valueOf(slot), color);
            drawTeamItem(player, color, slot);
        }

        inventory.setItem(40, new ItemBuilder(Material.BARRIER)
            .name(text("menus.common.close"))
            .build());
    }

    private void drawTeamItem(Player viewer, TeamColor color, int slot) {
        List<UUID> members = plugin.getTeamManager().getPlayersInTeam(arena, color);
        boolean selected = plugin.getTeamManager().getColor(arena, viewer.getUniqueId()) == color;
        boolean available = plugin.getTeamManager().isTeamAvailable(arena, viewer.getUniqueId(), color);
        int teamSize = plugin.getTeamManager().getTeamSize(arena);

        List<String> names = new ArrayList<String>();
        for (UUID memberId : members) {
            Player online = Bukkit.getPlayer(memberId);
            names.add(online == null ? "&7Offline" : "&f" + online.getName());
        }
        if (names.isEmpty()) {
            names.add("&aLivre");
        }

        ItemBuilder builder = new ItemBuilder(Material.WOOL, 1, color.getWoolData())
            .name(color.getChatColor() + "" + org.bukkit.ChatColor.BOLD + color.getDisplayName())
            .lore(textList("menus.team-selector.team.lore", placeholders(
                "status", selected ? text("menus.team-selector.team.status-selected")
                    : available ? text("menus.team-selector.team.status-available")
                    : text("menus.team-selector.team.status-full"),
                "players", String.valueOf(members.size()),
                "team_size", String.valueOf(teamSize),
                "members", join(names),
                "action", selected ? text("menus.team-selector.team.action-selected")
                    : available ? text("menus.team-selector.team.action-available")
                    : text("menus.team-selector.team.action-unavailable")
            )));

        if (selected) {
            builder.glow();
        }

        inventory.setItem(slot, builder.build());
    }

    @Override
    public void handleClick(Player player, int slot, ClickType clickType) {
        if (slot == 40) {
            player.closeInventory();
            return;
        }

        TeamColor color = colorsBySlot.get(Integer.valueOf(slot));
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

    private List<Integer> resolveTeamSlots(int amount) {
        if (amount <= 2) {
            return java.util.Arrays.asList(20, 24);
        }
        if (amount <= 4) {
            return java.util.Arrays.asList(19, 21, 23, 25);
        }
        return java.util.Arrays.asList(10, 12, 14, 16, 28, 30, 32, 34);
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "&aLivre";
        }

        List<String> copy = new ArrayList<String>(values);
        Collections.sort(copy);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < copy.size(); index++) {
            if (index > 0) {
                builder.append("&7, ");
            }
            builder.append(copy.get(index));
        }
        return builder.toString();
    }
}

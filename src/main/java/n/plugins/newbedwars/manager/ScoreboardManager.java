package n.plugins.newbedwars.manager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaState;
import n.plugins.newbedwars.arena.ArenaTeam;
import n.plugins.newbedwars.arena.TeamColor;
import n.plugins.newbedwars.util.ChatUtil;
import n.plugins.newbedwars.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public class ScoreboardManager {

    private final NewBedWars plugin;
    private final Map<UUID, Scoreboard> boards;
    private final SimpleDateFormat dateFormat;
    private int taskId = -1;

    public ScoreboardManager(NewBedWars plugin) {
        this.plugin = plugin;
        this.boards = new HashMap<UUID, Scoreboard>();
        this.dateFormat = new SimpleDateFormat("dd/MM/yy");
    }

    public void start() {
        if (taskId != -1) {
            return;
        }

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                updateAll();
            }
        }, 20L, 20L);
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public void clear(Player player) {
        boards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
            if (arena == null) {
                updateLobby(player);
                continue;
            }

            updateArena(player, arena);
        }
    }

    private void updateLobby(Player player) {
        List<String> lines = plugin.getConfig().getStringList("scoreboard.lobby");
        createBoard(player, lines, null);
    }

    private void updateArena(Player player, Arena arena) {
        List<String> lines = plugin.getConfig().getStringList("scoreboard." + arena.getState().name().toLowerCase());
        createBoard(player, lines, arena);
    }

    private void createBoard(Player player, List<String> lines, Arena arena) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        plugin.getNpcManager().applyHiddenNameTeam(scoreboard);

        Objective objective = scoreboard.registerNewObjective("bw", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(ChatUtil.color(plugin.getConfig().getString("scoreboard.title", "&bBEDWARS")));

        int score = lines.size();
        for (String line : lines) {
            String replaced = replacePlaceholders(line, player, arena) + uniqueSuffix(score);
            objective.getScore(ChatUtil.color(replaced)).setScore(score);
            score--;
        }

        boards.put(player.getUniqueId(), scoreboard);
        player.setScoreboard(scoreboard);
    }

    private String replacePlaceholders(String line, Player player, Arena arena) {
        String currentDate = dateFormat.format(new Date());
        String online = String.valueOf(Bukkit.getOnlinePlayers().size());
        String readyArenas = String.valueOf(countReadyArenas());

        line = line
            .replace("%date%", currentDate)
            .replace("%online%", online)
            .replace("%ready_arenas%", readyArenas)
            .replace("%mode%", "1v1");

        if (arena == null) {
            return line;
        }

        TeamColor color = plugin.getTeamManager().getColor(arena, player.getUniqueId());
        ArenaTeam ownTeam = color == null ? null : arena.getTeam(color);
        int bedsAlive = 0;
        for (ArenaTeam team : arena.getTeams().values()) {
            if (!team.isBedDestroyed()) {
                bedsAlive++;
            }
        }

        String winner = "\u00A77Nenhum";
        List<ArenaTeam> aliveTeams = plugin.getTeamManager().getAliveTeams(arena);
        if (!aliveTeams.isEmpty()) {
            winner = aliveTeams.get(0).getColor().getColoredName();
        }

        line = line
            .replace("%arena%", arena.getName())
            .replace("%status%", arena.getState().getDisplayName())
            .replace("%players%", String.valueOf(arena.getPlayerCount()))
            .replace("%alive_players%", String.valueOf(arena.getAlivePlayers()))
            .replace("%min_players%", String.valueOf(plugin.getGameManager().getArenaCapacity()))
            .replace("%max_players%", String.valueOf(plugin.getGameManager().getArenaCapacity()))
            .replace("%countdown%", String.valueOf(arena.getState() == ArenaState.ENDING ? arena.getEndCountdown() : arena.getCountdown()))
            .replace("%time%", TimeUtil.formatSeconds(arena.getElapsedTime()))
            .replace("%team%", color == null ? "\u00A77Sem time" : color.getColoredName())
            .replace("%beds_alive%", String.valueOf(bedsAlive))
            .replace("%your_bed%", ownTeam == null ? "\u00A77-" : (ownTeam.isBedDestroyed() ? "\u00A7cDestruida" : "\u00A7aInteira"))
            .replace("%winner%", winner)
            .replace("%next_event_name%", getNextEventName(arena))
            .replace("%next_event_time%", getNextEventTime(arena));

        for (TeamColor teamColor : TeamColor.values()) {
            line = line.replace("%team_line_" + teamColor.name().toLowerCase() + "%", formatTeamLine(arena, player, teamColor));
        }

        return line;
    }

    private int countReadyArenas() {
        int total = 0;
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            if (arena.isReady()) {
                total++;
            }
        }
        return total;
    }

    private String getNextEventName(Arena arena) {
        if (arena.getState() == ArenaState.STARTING) {
            return "Inicio";
        }

        int diamondTime = plugin.getConfig().getInt("events.diamond-tier-seconds", 300);
        int bedBreakTime = plugin.getConfig().getInt("events.bed-break-seconds", 600);
        if (arena.getElapsedTime() < diamondTime) {
            return "Diamante I";
        }
        if (arena.getElapsedTime() < bedBreakTime) {
            return "Camas";
        }
        return "Final";
    }

    private String getNextEventTime(Arena arena) {
        if (arena.getState() == ArenaState.STARTING) {
            return TimeUtil.formatSeconds(arena.getCountdown());
        }

        int diamondTime = plugin.getConfig().getInt("events.diamond-tier-seconds", 300);
        int bedBreakTime = plugin.getConfig().getInt("events.bed-break-seconds", 600);
        if (arena.getElapsedTime() < diamondTime) {
            return TimeUtil.formatSeconds(diamondTime - arena.getElapsedTime());
        }
        if (arena.getElapsedTime() < bedBreakTime) {
            return TimeUtil.formatSeconds(bedBreakTime - arena.getElapsedTime());
        }
        return "00:00";
    }

    private String formatTeamLine(Arena arena, Player viewer, TeamColor color) {
        if (!plugin.getTeamManager().isActiveColor(color)) {
            return "";
        }

        ArenaTeam team = arena.getTeam(color);
        boolean occupied = arena.getPlayerTeams().containsValue(color);
        boolean ownTeam = color == plugin.getTeamManager().getColor(arena, viewer.getUniqueId());
        String ownSuffix = ownTeam ? " \u00A77(Voce)" : "";
        String marker;

        if (!occupied) {
            marker = "\u00A77-";
        } else if (team.isEliminated()) {
            marker = "\u00A78\u2718";
        } else if (team.isBedDestroyed()) {
            marker = "\u00A7c\u2718";
        } else {
            marker = "\u00A7a\u2714";
        }

        return color.getChatColor() + "\u25A0 " + color.getDisplayName() + ownSuffix + " " + marker;
    }

    private String uniqueSuffix(int index) {
        ChatColor[] colors = ChatColor.values();
        return colors[index % colors.length].toString();
    }
}

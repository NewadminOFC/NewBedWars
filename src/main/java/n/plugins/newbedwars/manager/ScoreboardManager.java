package n.plugins.newbedwars.manager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
import org.bukkit.scoreboard.Team;

public class ScoreboardManager {

    private static final ChatColor[] ENTRY_COLORS = new ChatColor[] {
        ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA,
        ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY,
        ChatColor.DARK_GRAY, ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA,
        ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.YELLOW, ChatColor.WHITE
    };

    private final NewBedWars plugin;
    private final Map<UUID, BoardContext> boards;
    private final SimpleDateFormat dateFormat;
    private final SimpleDateFormat dateTimeFormat;
    private int taskId = -1;

    private static final class BoardContext {
        private final Scoreboard scoreboard;
        private final Objective objective;
        private final Map<Integer, Team> lineTeams;
        private final Map<Integer, String> entries;
        private final Map<String, Team> playerListTeams;
        private final String layoutKey;
        private String lastHeader = "";
        private String lastFooter = "";

        private BoardContext(Scoreboard scoreboard, Objective objective, Map<Integer, Team> lineTeams, Map<Integer, String> entries,
                             Map<String, Team> playerListTeams, String layoutKey) {
            this.scoreboard = scoreboard;
            this.objective = objective;
            this.lineTeams = lineTeams;
            this.entries = entries;
            this.playerListTeams = playerListTeams;
            this.layoutKey = layoutKey;
        }
    }

    private static final class LineParts {
        private final String prefix;
        private final String suffix;

        private LineParts(String prefix, String suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }
    }

    private static final class PlayerListStyle {
        private final String order;
        private final String prefix;
        private final String suffix;

        private PlayerListStyle(String order, String prefix, String suffix) {
            this.order = order;
            this.prefix = prefix;
            this.suffix = suffix;
        }
    }

    public ScoreboardManager(NewBedWars plugin) {
        this.plugin = plugin;
        this.boards = new HashMap<UUID, BoardContext>();
        this.dateFormat = new SimpleDateFormat("dd/MM/yy");
        this.dateTimeFormat = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
    }

    public void start() {
        if (taskId != -1) {
            return;
        }

        long updateTicks = Math.max(1L, plugin.getConfig().getLong("scoreboard.update-ticks", 10L));
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                updateAll();
            }
        }, updateTicks, updateTicks);
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
        boards.clear();
    }

    public void clear(Player player) {
        if (player == null) {
            return;
        }

        boards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        sendTabList(player, "", "");
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
            if (arena == null) {
                updateLobby(player);
            } else {
                updateArena(player, arena);
            }
        }
    }

    private void updateLobby(Player player) {
        List<String> rawLines = plugin.getConfig().getStringList("scoreboard.lobby");
        updateBoard(player, rawLines, null, "lobby");
        updateTabList(player, null);
    }

    private void updateArena(Player player, Arena arena) {
        List<String> rawLines = plugin.getConfig().getStringList("scoreboard." + arena.getState().name().toLowerCase());
        updateBoard(player, rawLines, arena, arena.getState().name().toLowerCase());
        updateTabList(player, arena);
    }

    private void updateBoard(Player player, List<String> rawLines, Arena arena, String layoutBase) {
        List<String> lines = prepareBoardLines(rawLines, player, arena);
        String layoutKey = layoutBase + ":" + lines.size();
        BoardContext context = boards.get(player.getUniqueId());
        if (context == null || !context.layoutKey.equals(layoutKey)) {
            context = createBoard(layoutKey, lines.size());
            boards.put(player.getUniqueId(), context);
            player.setScoreboard(context.scoreboard);
        }

        plugin.getNpcManager().applyHiddenNameTeam(context.scoreboard);
        context.objective.setDisplayName(ChatUtil.color(plugin.getConfig().getString("scoreboard.title", "&bBEDWARS")));

        int score = lines.size();
        for (int index = 0; index < lines.size(); index++) {
            String rendered = ChatUtil.color(replacePlaceholders(lines.get(index), player, arena));
            updateLine(context, index, rendered, score);
            score--;
        }

        if (isTabListActive(arena)) {
            updatePlayerList(context, player, arena);
        } else {
            clearPlayerListTeams(context);
        }
    }

    private BoardContext createBoard(String layoutKey, int lineCount) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        plugin.getNpcManager().applyHiddenNameTeam(scoreboard);

        Objective objective = scoreboard.registerNewObjective("bw", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        Map<Integer, Team> teams = new HashMap<Integer, Team>();
        Map<Integer, String> entries = new HashMap<Integer, String>();
        Map<String, Team> playerListTeams = new HashMap<String, Team>();
        for (int index = 0; index < lineCount; index++) {
            Team team = scoreboard.registerNewTeam("bw_line_" + index);
            String entry = uniqueEntry(index);
            team.addEntry(entry);
            objective.getScore(entry).setScore(lineCount - index);
            teams.put(index, team);
            entries.put(index, entry);
        }

        return new BoardContext(scoreboard, objective, teams, entries, playerListTeams, layoutKey);
    }

    private void updateLine(BoardContext context, int index, String line, int score) {
        Team team = context.lineTeams.get(index);
        String entry = context.entries.get(index);
        if (team == null || entry == null) {
            return;
        }

        LineParts parts = splitLine(line);
        team.setPrefix(parts.prefix);
        team.setSuffix(parts.suffix);
        context.objective.getScore(entry).setScore(score);
    }

    private void updateTabList(Player player, Arena arena) {
        if (!plugin.getConfig().getBoolean("tablist.enabled", true)) {
            return;
        }

        BoardContext context = boards.get(player.getUniqueId());
        if (!isTabListActive(arena)) {
            if (context != null) {
                context.lastHeader = "";
                context.lastFooter = "";
            }
            sendTabList(player, "", "");
            return;
        }

        String header = renderTabSection(plugin.getConfig().getStringList("tablist.header"), player, arena);
        String footer = renderTabSection(plugin.getConfig().getStringList("tablist.footer"), player, arena);

        if (context != null && header.equals(context.lastHeader) && footer.equals(context.lastFooter)) {
            return;
        }

        if (context != null) {
            context.lastHeader = header;
            context.lastFooter = footer;
        }

        sendTabList(player, header, footer);
    }

    private String renderTabSection(List<String> lines, Player player, Arena arena) {
        List<String> rendered = new ArrayList<String>();
        for (String line : lines) {
            rendered.add(ChatUtil.color(replacePlaceholders(line, player, arena)));
        }
        return joinLines(rendered);
    }

    private String replacePlaceholders(String line, Player player, Arena arena) {
        String currentDate = dateFormat.format(new Date());
        String currentDateTime = dateTimeFormat.format(new Date());
        String online = String.valueOf(Bukkit.getOnlinePlayers().size());
        String readyArenas = String.valueOf(countReadyArenas());

        line = line
            .replace("%date%", currentDate)
            .replace("%date_time%", currentDateTime)
            .replace("%clock%", currentDateTime)
            .replace("%online%", online)
            .replace("%ready_arenas%", readyArenas)
            .replace("%mode%", arena == null ? "Lobby" : arena.getMode().getDisplayName())
            .replace("%arena%", arena == null ? "Lobby" : arena.getDisplayName())
            .replace("%status%", arena == null ? "Lobby" : arena.getState().getDisplayName())
            .replace("%players%", arena == null ? "0" : String.valueOf(arena.getPlayerCount()))
            .replace("%alive_players%", arena == null ? "0" : String.valueOf(arena.getAlivePlayers()))
            .replace("%min_players%", String.valueOf(arena == null ? plugin.getGameManager().getArenaCapacity() : plugin.getGameManager().getArenaCapacity(arena)))
            .replace("%max_players%", String.valueOf(arena == null ? plugin.getGameManager().getArenaCapacity() : plugin.getGameManager().getArenaCapacity(arena)))
            .replace("%countdown%", arena == null ? "0" : String.valueOf(arena.getState() == ArenaState.ENDING ? arena.getEndCountdown() : arena.getCountdown()))
            .replace("%time%", arena == null ? "00:00" : TimeUtil.formatSeconds(arena.getElapsedTime()))
            .replace("%team%", arena == null ? "\u00A77Sem time" : plugin.getTeamManager().getDisplay(arena, player.getUniqueId()))
            .replace("%beds_alive%", arena == null ? "0" : String.valueOf(countBedsAlive(arena)))
            .replace("%next_event_name%", arena == null ? "-" : getNextEventName(arena))
            .replace("%next_event_time%", arena == null ? "00:00" : getNextEventTime(arena));

        if (arena == null) {
            line = line
                .replace("%your_bed%", "\u00A77-")
                .replace("%winner%", "\u00A77Nenhum");

            for (TeamColor teamColor : TeamColor.values()) {
                line = line.replace("%team_line_" + teamColor.name().toLowerCase() + "%", "");
            }
            return line;
        }

        TeamColor color = plugin.getTeamManager().getColor(arena, player.getUniqueId());
        ArenaTeam ownTeam = color == null ? null : arena.getTeam(color);
        String winner = "\u00A77Nenhum";
        List<ArenaTeam> aliveTeams = plugin.getTeamManager().getAliveTeams(arena);
        if (!aliveTeams.isEmpty()) {
            winner = aliveTeams.get(0).getColor().getColoredName();
        }

        line = line
            .replace("%your_bed%", ownTeam == null ? "\u00A77-" : (ownTeam.isBedDestroyed() ? "\u00A7cDestruida" : "\u00A7aInteira"))
            .replace("%winner%", winner);

        for (TeamColor teamColor : TeamColor.values()) {
            line = line.replace("%team_line_" + teamColor.name().toLowerCase() + "%", formatTeamLine(arena, player, teamColor));
        }

        return line;
    }

    private List<String> prepareBoardLines(List<String> rawLines, Player player, Arena arena) {
        List<String> rendered = new ArrayList<String>();
        if (rawLines == null) {
            return rendered;
        }

        for (String rawLine : rawLines) {
            rendered.add(ChatUtil.color(replacePlaceholders(rawLine, player, arena)));
        }

        List<String> compacted = new ArrayList<String>();
        boolean previousBlank = true;
        for (String line : rendered) {
            boolean blank = isBlankLine(line);
            if (blank) {
                if (previousBlank) {
                    continue;
                }
                compacted.add("");
            } else {
                compacted.add(line);
            }
            previousBlank = blank;
        }

        while (!compacted.isEmpty() && isBlankLine(compacted.get(0))) {
            compacted.remove(0);
        }

        while (!compacted.isEmpty() && isBlankLine(compacted.get(compacted.size() - 1))) {
            compacted.remove(compacted.size() - 1);
        }

        if (compacted.isEmpty()) {
            compacted.add(" ");
        }

        return compacted;
    }

    private boolean isBlankLine(String line) {
        return line == null || ChatColor.stripColor(line).trim().isEmpty();
    }

    private int countReadyArenas() {
        int total = 0;
        for (Arena arena : plugin.getArenaManager().getConfiguredArenas()) {
            if (arena.isReady()) {
                total++;
            }
        }
        return total;
    }

    private int countBedsAlive(Arena arena) {
        int total = 0;
        for (TeamColor color : plugin.getTeamManager().getActiveColors(arena)) {
            ArenaTeam team = arena.getTeam(color);
            if (!team.isBedDestroyed()) {
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
        if (!plugin.getTeamManager().isActiveColor(arena, color)) {
            return "";
        }

        ArenaTeam team = arena.getTeam(color);
        boolean occupied = arena.getPlayerTeams().containsValue(color);
        boolean ownTeam = color == plugin.getTeamManager().getColor(arena, viewer.getUniqueId());
        String ownSuffix = ownTeam ? " \u00A77(Voce)" : "";
        String marker;

        if (!occupied) {
            marker = "\u00A78\u2718";
        } else if (!team.isBedDestroyed()) {
            marker = "\u00A7a\u2714";
        } else {
            int aliveCount = countAlivePlayers(arena, color);
            marker = aliveCount > 0 ? "\u00A7f" + aliveCount : "\u00A7c\u2718";
        }

        return color.getChatColor() + "\u25A0 " + color.getDisplayName() + ownSuffix + " " + marker;
    }

    private void updatePlayerList(BoardContext context, Player viewer, Arena viewerArena) {
        Map<String, Team> activeTeams = new HashMap<String, Team>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            PlayerListStyle style = resolvePlayerListStyle(viewerArena, target);
            String teamName = buildPlayerListTeamName(style, target);
            Team team = context.playerListTeams.get(teamName);
            if (team == null) {
                team = context.scoreboard.getTeam(teamName);
            }
            if (team == null) {
                team = context.scoreboard.registerNewTeam(teamName);
            }

            applyPlayerListStyle(team, style);
            if (!team.hasEntry(target.getName())) {
                removeEntryFromForeignTeams(context.scoreboard, team, target.getName());
                team.addEntry(target.getName());
            }

            activeTeams.put(teamName, team);
        }

        for (Map.Entry<String, Team> entry : new ArrayList<Map.Entry<String, Team>>(context.playerListTeams.entrySet())) {
            if (activeTeams.containsKey(entry.getKey())) {
                continue;
            }

            Team team = entry.getValue();
            if (team != null) {
                try {
                    team.unregister();
                } catch (IllegalStateException ignored) {
                }
            }
        }

        context.playerListTeams.clear();
        context.playerListTeams.putAll(activeTeams);
    }

    private void clearPlayerListTeams(BoardContext context) {
        if (context == null) {
            return;
        }

        for (Team team : new ArrayList<Team>(context.playerListTeams.values())) {
            if (team == null) {
                continue;
            }
            try {
                team.unregister();
            } catch (IllegalStateException ignored) {
            }
        }
        context.playerListTeams.clear();
    }

    private void applyPlayerListStyle(Team team, PlayerListStyle style) {
        if (team == null || style == null) {
            return;
        }

        team.setPrefix(trimTeamText(style.prefix));
        team.setSuffix(trimTeamText(style.suffix));
    }

    private String buildPlayerListTeamName(PlayerListStyle style, Player target) {
        String order = style == null || style.order == null ? "90" : style.order.replaceAll("[^A-Za-z0-9]", "");
        if (order.isEmpty()) {
            order = "90";
        }

        String teamName = "pl" + order + compactPlayerId(target);
        if (teamName.length() > 16) {
            teamName = teamName.substring(0, 16);
        }
        return teamName;
    }

    private String compactPlayerId(Player player) {
        String raw = Integer.toHexString(Math.abs(player.getUniqueId().hashCode()));
        if (raw.length() > 12) {
            raw = raw.substring(0, 12);
        }
        return raw;
    }

    private void removeEntryFromForeignTeams(Scoreboard scoreboard, Team targetTeam, String entry) {
        for (Team team : scoreboard.getTeams()) {
            if (team == null || team.equals(targetTeam)) {
                continue;
            }
            if (team.hasEntry(entry)) {
                team.removeEntry(entry);
            }
        }
    }

    private PlayerListStyle resolvePlayerListStyle(Arena viewerArena, Player target) {
        Arena targetArena = plugin.getArenaManager().getArenaByPlayer(target.getUniqueId());
        TeamColor targetColor = targetArena == null ? null : plugin.getTeamManager().getColor(targetArena, target.getUniqueId());
        String path = "tablist.player-list.lobby";

        if (viewerArena != null && targetArena != null && viewerArena.getName().equalsIgnoreCase(targetArena.getName())) {
            if (targetArena.getSpectators().contains(target.getUniqueId())) {
                path = "tablist.player-list.same-arena.spectator";
            } else if (targetColor != null) {
                String colorPath = "tablist.player-list.same-arena." + targetColor.name().toLowerCase();
                path = plugin.getConfig().contains(colorPath) ? colorPath : "tablist.player-list.same-arena.default";
            } else {
                path = "tablist.player-list.same-arena.default";
            }
        } else if (targetArena != null) {
            path = "tablist.player-list.other-arena";
        }

        String prefix = plugin.getConfig().getString(path + ".prefix", "&7");
        String suffix = plugin.getConfig().getString(path + ".suffix", "");
        String order = plugin.getConfig().getString(path + ".order", "90");

        prefix = ChatUtil.color(replacePlayerListPlaceholders(prefix, target, targetArena, targetColor));
        suffix = ChatUtil.color(replacePlayerListPlaceholders(suffix, target, targetArena, targetColor));
        return new PlayerListStyle(order, prefix, suffix);
    }

    private String replacePlayerListPlaceholders(String text, Player target, Arena targetArena, TeamColor targetColor) {
        if (text == null) {
            return "";
        }

        String teamName = targetColor == null ? "\u00A77Sem time" : targetColor.getColoredName();
        String plainTeamName = targetColor == null ? "Sem time" : targetColor.getDisplayName();
        String arenaName = targetArena == null ? "Lobby" : targetArena.getDisplayName();
        String status = targetArena == null ? "Lobby" : targetArena.getState().getDisplayName();

        return text
            .replace("%player%", target == null ? "" : target.getName())
            .replace("%team%", teamName)
            .replace("%team_plain%", plainTeamName)
            .replace("%arena%", arenaName)
            .replace("%status%", status)
            .replace("%mode%", targetArena == null ? "Lobby" : targetArena.getMode().getDisplayName())
            .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
    }

    private String trimTeamText(String text) {
        if (text == null) {
            return "";
        }

        return text.length() <= 16 ? text : text.substring(0, 16);
    }

    private boolean isTabListActive(Arena arena) {
        if (!plugin.getConfig().getBoolean("tablist.only-ingame", true)) {
            return arena != null;
        }

        return arena != null && (arena.getState() == ArenaState.INGAME || arena.getState() == ArenaState.ENDING);
    }

    private int countAlivePlayers(Arena arena, TeamColor color) {
        int total = 0;
        for (Map.Entry<UUID, TeamColor> entry : arena.getPlayerTeams().entrySet()) {
            if (entry.getValue() != color) {
                continue;
            }

            UUID uniqueId = entry.getKey();
            if (!arena.getPlayers().contains(uniqueId)) {
                continue;
            }

            if (arena.getSpectators().contains(uniqueId)) {
                continue;
            }

            total++;
        }
        return total;
    }

    private String uniqueEntry(int index) {
        if (index < ENTRY_COLORS.length) {
            return ENTRY_COLORS[index].toString();
        }

        ChatColor first = ENTRY_COLORS[index % ENTRY_COLORS.length];
        ChatColor second = ENTRY_COLORS[(index / ENTRY_COLORS.length) % ENTRY_COLORS.length];
        return first.toString() + second.toString();
    }

    private LineParts splitLine(String line) {
        if (line == null) {
            return new LineParts("", "");
        }

        if (line.length() <= 16) {
            return new LineParts(line, "");
        }

        int split = 16;
        if (line.charAt(15) == ChatColor.COLOR_CHAR) {
            split = 15;
        }

        String prefix = line.substring(0, split);
        String color = ChatColor.getLastColors(prefix);
        String remaining = line.substring(split);
        int suffixLength = Math.max(0, 16 - color.length());
        if (remaining.length() > suffixLength) {
            remaining = remaining.substring(0, suffixLength);
        }

        return new LineParts(prefix, color + remaining);
    }

    private String joinLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(index));
        }
        return builder.toString();
    }

    private void sendTabList(Player player, String header, String footer) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            String version = packageName.substring(packageName.lastIndexOf('.') + 1);

            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
            Object craftPlayer = craftPlayerClass.cast(player);
            Object handle = craftPlayerClass.getMethod("getHandle").invoke(craftPlayer);
            Object connection = handle.getClass().getField("playerConnection").get(handle);

            Class<?> serializerClass = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent$ChatSerializer");
            Class<?> componentClass = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent");
            Class<?> packetClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutPlayerListHeaderFooter");

            Method serializerMethod = serializerClass.getMethod("a", String.class);
            Object headerComponent = serializerMethod.invoke(null, jsonText(header));
            Object footerComponent = serializerMethod.invoke(null, jsonText(footer));
            Object packet = packetClass.newInstance();

            Field headerField = packetClass.getDeclaredField("a");
            headerField.setAccessible(true);
            headerField.set(packet, headerComponent);

            Field footerField = packetClass.getDeclaredField("b");
            footerField.setAccessible(true);
            footerField.set(packet, footerComponent);

            Class<?> packetBaseClass = Class.forName("net.minecraft.server." + version + ".Packet");
            connection.getClass().getMethod("sendPacket", packetBaseClass).invoke(connection, packet);
        } catch (Exception ignored) {
        }
    }

    private String jsonText(String text) {
        return "{\"text\":\"" + escapeJson(text) + "\"}";
    }

    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }

        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "");
    }
}

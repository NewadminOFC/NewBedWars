package n.plugins.newbedwars.manager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private String cachedServerVersion;
    private Class<?> cachedCraftPlayerClass;
    private Method cachedGetHandleMethod;
    private Field cachedPlayerConnectionField;
    private Class<?> cachedSerializerClass;
    private Method cachedSerializerMethod;
    private Class<?> cachedPacketClass;
    private Field cachedHeaderField;
    private Field cachedFooterField;
    private Class<?> cachedPacketBaseClass;
    private Method cachedSendPacketMethod;
    private boolean reflectionInitialized;
    private boolean reflectionFailed;
    private int cachedReadyArenas = -1;
    private long readyArenasCacheTime;
    private String cachedDate = "";
    private String cachedDateTime = "";
    private long dateTimeCacheTime;

    private boolean cachedTablistEnabled = true;
    private boolean cachedTablistOnlyIngame = true;
    private long tablistConfigCacheTime;
    private static final long TABLIST_CONFIG_TTL = 5000L;

    private final Map<String, String[]> playerListStyleCache = new HashMap<String, String[]>();
    private long playerListStyleCacheTime;
    private static final long PLAYERLIST_STYLE_TTL = 5000L;

    private static final class BoardContext {
        private final Scoreboard scoreboard;
        private final Objective objective;
        private final Map<Integer, Team> lineTeams;
        private final Map<Integer, String> entries;
        private final Map<String, Team> playerListTeams;
        private final String layoutKey;
        private String lastHeader = "";
        private String lastFooter = "";
        private long lastRenderTime;
        private long lastPlayerListTime;

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

        long updateTicks = Math.max(1L, plugin.getConfig().getLong("scoreboard.update-ticks", 20L));
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

    public void restartScheduler() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        start();
    }

    public void clear(Player player) {
        if (player == null) {
            return;
        }

        boards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        sendTabList(player, "", "");
    }

    private int cachedOnlineCount;
    private long lastVisibilityUpdate;
    private long performanceConfigCacheTime;
    private long cachedLobbyUpdateTicks = 100L;
    private long cachedArenaUpdateTicks = 20L;
    private long cachedPlayerListUpdateTicks = 100L;
    private long cachedVisibilityUpdateTicks = 100L;

    private void updateAll() {
        refreshPerformanceConfig();
        cachedOnlineCount = Bukkit.getOnlinePlayers().size();
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
            BoardContext context = boards.get(player.getUniqueId());
            long intervalTicks = arena == null ? cachedLobbyUpdateTicks : cachedArenaUpdateTicks;
            if (context != null && now - context.lastRenderTime < ticksToMillis(intervalTicks)) {
                continue;
            }
            if (arena == null) {
                updateLobby(player);
            } else {
                updateArena(player, arena);
            }
            context = boards.get(player.getUniqueId());
            if (context != null) {
                context.lastRenderTime = now;
            }
        }

        if (now - lastVisibilityUpdate >= ticksToMillis(cachedVisibilityUpdateTicks)) {
            lastVisibilityUpdate = now;
            updateVisibility();
        }
    }

    private void refreshPerformanceConfig() {
        long now = System.currentTimeMillis();
        if (now - performanceConfigCacheTime < 5000L) {
            return;
        }
        performanceConfigCacheTime = now;
        cachedLobbyUpdateTicks = Math.max(20L, plugin.getConfig().getLong("scoreboard.lobby-update-ticks", 100L));
        cachedArenaUpdateTicks = Math.max(10L, plugin.getConfig().getLong("scoreboard.arena-update-ticks", 20L));
        cachedPlayerListUpdateTicks = Math.max(20L, plugin.getConfig().getLong("scoreboard.player-list-update-ticks", 100L));
        cachedVisibilityUpdateTicks = Math.max(20L, plugin.getConfig().getLong("scoreboard.visibility-update-ticks", 100L));
    }

    private long ticksToMillis(long ticks) {
        return Math.max(1L, ticks) * 50L;
    }

    private List<String> cachedLobbyLines;
    private long lobbyLinesCacheTime;
    private final Map<String, List<String>> cachedArenaLines = new HashMap<String, List<String>>();
    private long arenaLinesCacheTime;

    private List<String> getCachedLobbyLines() {
        long now = System.currentTimeMillis();
        if (cachedLobbyLines == null || now - lobbyLinesCacheTime > 5000L) {
            cachedLobbyLines = plugin.getConfig().getStringList("scoreboard.lobby");
            lobbyLinesCacheTime = now;
        }
        return cachedLobbyLines;
    }

    private List<String> getCachedArenaLines(String stateKey) {
        long now = System.currentTimeMillis();
        if (now - arenaLinesCacheTime > 5000L) {
            cachedArenaLines.clear();
            arenaLinesCacheTime = now;
        }
        List<String> lines = cachedArenaLines.get(stateKey);
        if (lines == null) {
            lines = plugin.getConfig().getStringList("scoreboard." + stateKey);
            cachedArenaLines.put(stateKey, lines);
        }
        return lines;
    }

    private void updateLobby(Player player) {
        updateBoard(player, getCachedLobbyLines(), null, "lobby");
        updateTabList(player, null);
    }

    private void updateArena(Player player, Arena arena) {
        String stateKey = arena.getState().name().toLowerCase();
        updateBoard(player, getCachedArenaLines(stateKey), arena, stateKey);
        updateTabList(player, arena);
    }

    private String cachedScoreboardTitle;
    private long titleCacheTime;

    private void updateBoard(Player player, List<String> rawLines, Arena arena, String layoutBase) {
        List<String> lines = prepareBoardLines(rawLines, player, arena);
        String layoutKey = layoutBase + ":" + lines.size();
        BoardContext context = boards.get(player.getUniqueId());
        if (context == null || !context.layoutKey.equals(layoutKey)) {
            context = createBoard(layoutKey, lines.size());
            boards.put(player.getUniqueId(), context);
            player.setScoreboard(context.scoreboard);
        }

        long now = System.currentTimeMillis();
        if (cachedScoreboardTitle == null || now - titleCacheTime >= 5000L) {
            cachedScoreboardTitle = ChatUtil.color(plugin.getConfig().getString("scoreboard.title", "&bBEDWARS"));
            titleCacheTime = now;
        }
        context.objective.setDisplayName(cachedScoreboardTitle);

        int score = lines.size();
        for (int index = 0; index < lines.size(); index++) {
            updateLine(context, index, lines.get(index), score);
            score--;
        }

        if (isTabListActive(arena)) {
            if (now - context.lastPlayerListTime >= ticksToMillis(cachedPlayerListUpdateTicks)) {
                context.lastPlayerListTime = now;
                updatePlayerList(context, player, arena);
            }
        } else {
            clearPlayerListTeams(context);
        }
    }

    private BoardContext createBoard(String layoutKey, int lineCount) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        plugin.getNpcManager().applyHiddenNameTeam(scoreboard);

        Objective objective = scoreboard.registerNewObjective("bw", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        Map<Integer, Team> teams = new HashMap<Integer, Team>(lineCount * 2);
        Map<Integer, String> entries = new HashMap<Integer, String>(lineCount * 2);
        Map<String, Team> playerListTeams = new HashMap<String, Team>(16);
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
        String currentPrefix = team.getPrefix();
        String currentSuffix = team.getSuffix();
        if (!parts.prefix.equals(currentPrefix)) {
            team.setPrefix(parts.prefix);
        }
        if (!parts.suffix.equals(currentSuffix)) {
            team.setSuffix(parts.suffix);
        }
        if (context.objective.getScore(entry).getScore() != score) {
            context.objective.getScore(entry).setScore(score);
        }
    }

    private List<String> cachedTabHeader;
    private List<String> cachedTabFooter;
    private long tabCacheTime;

    private void updateTabList(Player player, Arena arena) {
        long now = System.currentTimeMillis();
        if (now - tablistConfigCacheTime >= TABLIST_CONFIG_TTL) {
            cachedTablistEnabled = plugin.getConfig().getBoolean("tablist.enabled", true);
            cachedTablistOnlyIngame = plugin.getConfig().getBoolean("tablist.only-ingame", true);
            tablistConfigCacheTime = now;
        }
        if (!cachedTablistEnabled) {
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

        if (cachedTabHeader == null || now - tabCacheTime >= 5000L) {
            cachedTabHeader = plugin.getConfig().getStringList("tablist.header");
            cachedTabFooter = plugin.getConfig().getStringList("tablist.footer");
            tabCacheTime = now;
        }

        String header = renderTabSection(cachedTabHeader, player, arena);
        String footer = renderTabSection(cachedTabFooter, player, arena);

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
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(lines.size() * 32);
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(ChatUtil.color(replacePlaceholders(lines.get(i), player, arena)));
        }
        return builder.toString();
    }

    private String replacePlaceholders(String line, Player player, Arena arena) {
        long now = System.currentTimeMillis();
        if (now - dateTimeCacheTime >= 1000L) {
            Date date = new Date(now);
            cachedDate = dateFormat.format(date);
            cachedDateTime = dateTimeFormat.format(date);
            dateTimeCacheTime = now;
        }
        String currentDate = cachedDate;
        String currentDateTime = cachedDateTime;
        String online = String.valueOf(cachedOnlineCount);
        String readyArenas = String.valueOf(getCachedReadyArenas());

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
            .replace("%min_players%", String.valueOf(arena == null ? plugin.getGameManager().getRequiredPlayersToStart() : plugin.getGameManager().getRequiredPlayersToStart(arena)))
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
        if (rawLines == null || rawLines.isEmpty()) {
            List<String> fallback = new ArrayList<String>(1);
            fallback.add(" ");
            return fallback;
        }

        List<String> rendered = new ArrayList<String>(rawLines.size());
        for (String rawLine : rawLines) {
            rendered.add(ChatUtil.color(replacePlaceholders(rawLine, player, arena)));
        }

        List<String> compacted = new ArrayList<String>(rendered.size());
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

        int start = 0;
        while (start < compacted.size() && isBlankLine(compacted.get(start))) {
            start++;
        }
        int end = compacted.size();
        while (end > start && isBlankLine(compacted.get(end - 1))) {
            end--;
        }
        if (start > 0 || end < compacted.size()) {
            compacted = new ArrayList<String>(compacted.subList(start, end));
        }

        if (compacted.isEmpty()) {
            compacted.add(" ");
        }

        return compacted;
    }

    private boolean isBlankLine(String line) {
        if (line == null || line.isEmpty()) {
            return true;
        }
        String stripped = ChatColor.stripColor(line);
        for (int i = 0; i < stripped.length(); i++) {
            if (stripped.charAt(i) != ' ') {
                return false;
            }
        }
        return true;
    }

    private int getCachedReadyArenas() {
        long now = System.currentTimeMillis();
        if (cachedReadyArenas < 0 || now - readyArenasCacheTime >= 5000L) {
            int total = 0;
            for (Arena arena : plugin.getArenaManager().getConfiguredArenas()) {
                if (arena.isReady()) {
                    total++;
                }
            }
            cachedReadyArenas = total;
            readyArenasCacheTime = now;
        }
        return cachedReadyArenas;
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

    private int cachedDiamondTime = -1;
    private int cachedBedBreakTime = -1;
    private long eventTimeCacheTime;

    private void refreshEventTimeCache() {
        long now = System.currentTimeMillis();
        if (now - eventTimeCacheTime < 5000L) {
            return;
        }
        eventTimeCacheTime = now;
        cachedDiamondTime = plugin.getConfig().getInt("events.diamond-tier-seconds", 300);
        cachedBedBreakTime = plugin.getConfig().getInt("events.bed-break-seconds", 600);
    }

    private String getNextEventName(Arena arena) {
        if (arena.getState() == ArenaState.STARTING) {
            return "Inicio";
        }

        refreshEventTimeCache();
        if (arena.getElapsedTime() < cachedDiamondTime) {
            return "Diamante I";
        }
        if (arena.getElapsedTime() < cachedBedBreakTime) {
            return "Camas";
        }
        return "Final";
    }

    private String getNextEventTime(Arena arena) {
        if (arena.getState() == ArenaState.STARTING) {
            return TimeUtil.formatSeconds(arena.getCountdown());
        }

        refreshEventTimeCache();
        if (arena.getElapsedTime() < cachedDiamondTime) {
            return TimeUtil.formatSeconds(cachedDiamondTime - arena.getElapsedTime());
        }
        if (arena.getElapsedTime() < cachedBedBreakTime) {
            return TimeUtil.formatSeconds(cachedBedBreakTime - arena.getElapsedTime());
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
            if (!shouldSeeTarget(viewer, target)) {
                continue;
            }

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

        java.util.Iterator<Map.Entry<String, Team>> iterator = context.playerListTeams.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Team> entry = iterator.next();
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
            iterator.remove();
        }

        context.playerListTeams.putAll(activeTeams);
    }

    private final Map<UUID, Set<UUID>> hiddenPlayersCache = new HashMap<UUID, Set<UUID>>();

    public void invalidateConfigurationCache() {
        performanceConfigCacheTime = 0L;
        lobbyLinesCacheTime = 0L;
        arenaLinesCacheTime = 0L;
        titleCacheTime = 0L;
        tabCacheTime = 0L;
        tablistConfigCacheTime = 0L;
        playerListStyleCacheTime = 0L;
        eventTimeCacheTime = 0L;
        cachedLobbyLines = null;
        cachedArenaLines.clear();
        cachedTabHeader = null;
        cachedTabFooter = null;
        cachedScoreboardTitle = null;
        playerListStyleCache.clear();
        for (BoardContext context : boards.values()) {
            context.lastRenderTime = 0L;
            context.lastPlayerListTime = 0L;
        }
    }

    private void updateVisibility() {
        List<Player> players = new ArrayList<Player>(Bukkit.getOnlinePlayers());
        for (Player viewer : players) {
            if (viewer == null || !viewer.isOnline()) {
                continue;
            }

            Set<UUID> currentlyHidden = hiddenPlayersCache.get(viewer.getUniqueId());
            if (currentlyHidden == null) {
                currentlyHidden = new HashSet<UUID>();
                hiddenPlayersCache.put(viewer.getUniqueId(), currentlyHidden);
            }

            Set<UUID> shouldHide = new HashSet<UUID>();
            for (Player target : players) {
                if (target == null || !target.isOnline() || viewer.equals(target)) {
                    continue;
                }

                if (!shouldSeeTarget(viewer, target)) {
                    shouldHide.add(target.getUniqueId());
                }
            }

            for (UUID id : shouldHide) {
                if (!currentlyHidden.contains(id)) {
                    Player target = Bukkit.getPlayer(id);
                    if (target != null && target.isOnline()) {
                        viewer.hidePlayer(target);
                    }
                }
            }

            for (UUID id : new ArrayList<UUID>(currentlyHidden)) {
                if (!shouldHide.contains(id)) {
                    Player target = Bukkit.getPlayer(id);
                    if (target != null && target.isOnline()) {
                        viewer.showPlayer(target);
                    }
                }
            }

            currentlyHidden.clear();
            currentlyHidden.addAll(shouldHide);
        }

        Set<UUID> onlineIds = new HashSet<UUID>();
        for (Player p : players) {
            if (p != null) {
                onlineIds.add(p.getUniqueId());
            }
        }
        java.util.Iterator<Map.Entry<UUID, Set<UUID>>> cacheIt = hiddenPlayersCache.entrySet().iterator();
        while (cacheIt.hasNext()) {
            if (!onlineIds.contains(cacheIt.next().getKey())) {
                cacheIt.remove();
            }
        }
    }

    private boolean shouldSeeTarget(Player viewer, Player target) {
        if (viewer == null || target == null) {
            return false;
        }

        if (!viewer.getWorld().equals(target.getWorld())) {
            return false;
        }

        UUID targetId = target.getUniqueId();
        Arena targetArena = plugin.getArenaManager().getArenaByPlayer(targetId);
        if (targetArena == null) {
            return true;
        }

        return !targetArena.getSpectators().contains(targetId)
            && !plugin.getGameManager().isRespawning(targetId);
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
        String order;
        if (style == null || style.order == null) {
            order = "90";
        } else {
            StringBuilder sb = new StringBuilder(style.order.length());
            for (int i = 0; i < style.order.length(); i++) {
                char c = style.order.charAt(i);
                if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                    sb.append(c);
                }
            }
            order = sb.length() == 0 ? "90" : sb.toString();
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
        UUID targetId = target.getUniqueId();
        Arena targetArena = plugin.getArenaManager().getArenaByPlayer(targetId);
        TeamColor targetColor = targetArena == null ? null : plugin.getTeamManager().getColor(targetArena, targetId);
        String path = "tablist.player-list.lobby";

        if (viewerArena != null && targetArena != null && viewerArena.getName().equalsIgnoreCase(targetArena.getName())) {
            if (targetArena.getSpectators().contains(targetId)) {
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

        long now = System.currentTimeMillis();
        String cacheKey = path;
        String[] cached = playerListStyleCache.get(cacheKey);
        if (cached == null || now - playerListStyleCacheTime >= PLAYERLIST_STYLE_TTL) {
            String prefix = plugin.getConfig().getString(path + ".prefix", "&7");
            String suffix = plugin.getConfig().getString(path + ".suffix", "");
            String order = plugin.getConfig().getString(path + ".order", "90");
            cached = new String[] { prefix, suffix, order };
            playerListStyleCache.put(cacheKey, cached);
            if (now - playerListStyleCacheTime >= PLAYERLIST_STYLE_TTL) {
                playerListStyleCacheTime = now;
            }
        }

        String prefix = ChatUtil.color(replacePlayerListPlaceholders(cached[0], target, targetArena, targetColor));
        String suffix = ChatUtil.color(replacePlayerListPlaceholders(cached[1], target, targetArena, targetColor));
        return new PlayerListStyle(cached[2], prefix, suffix);
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
            .replace("%online%", String.valueOf(cachedOnlineCount));
    }

    private String trimTeamText(String text) {
        if (text == null) {
            return "";
        }

        return text.length() <= 16 ? text : text.substring(0, 16);
    }

    private boolean isTabListActive(Arena arena) {
        if (!cachedTablistOnlyIngame) {
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
            if (arena.getPlayers().contains(uniqueId) && !arena.getSpectators().contains(uniqueId)) {
                total++;
            }
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

    private static final LineParts EMPTY_LINE_PARTS = new LineParts("", "");

    private LineParts splitLine(String line) {
        if (line == null || line.isEmpty()) {
            return EMPTY_LINE_PARTS;
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



    private void initTabListReflection() {
        if (reflectionInitialized) {
            return;
        }
        reflectionInitialized = true;
        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            cachedServerVersion = packageName.substring(packageName.lastIndexOf('.') + 1);
            cachedCraftPlayerClass = Class.forName("org.bukkit.craftbukkit." + cachedServerVersion + ".entity.CraftPlayer");
            cachedGetHandleMethod = cachedCraftPlayerClass.getMethod("getHandle");
            cachedSerializerClass = Class.forName("net.minecraft.server." + cachedServerVersion + ".IChatBaseComponent$ChatSerializer");
            cachedSerializerMethod = cachedSerializerClass.getMethod("a", String.class);
            cachedPacketClass = Class.forName("net.minecraft.server." + cachedServerVersion + ".PacketPlayOutPlayerListHeaderFooter");
            cachedHeaderField = cachedPacketClass.getDeclaredField("a");
            cachedHeaderField.setAccessible(true);
            cachedFooterField = cachedPacketClass.getDeclaredField("b");
            cachedFooterField.setAccessible(true);
            cachedPacketBaseClass = Class.forName("net.minecraft.server." + cachedServerVersion + ".Packet");
        } catch (Exception exception) {
            reflectionFailed = true;
        }
    }

    private void sendTabList(Player player, String header, String footer) {
        if (player == null || !player.isOnline()) {
            return;
        }

        initTabListReflection();
        if (reflectionFailed) {
            return;
        }

        try {
            Object craftPlayer = cachedCraftPlayerClass.cast(player);
            Object handle = cachedGetHandleMethod.invoke(craftPlayer);
            if (cachedPlayerConnectionField == null) {
                cachedPlayerConnectionField = handle.getClass().getField("playerConnection");
            }
            Object connection = cachedPlayerConnectionField.get(handle);

            Object headerComponent = cachedSerializerMethod.invoke(null, jsonText(header));
            Object footerComponent = cachedSerializerMethod.invoke(null, jsonText(footer));
            Object packet = cachedPacketClass.newInstance();

            cachedHeaderField.set(packet, headerComponent);
            cachedFooterField.set(packet, footerComponent);

            if (cachedSendPacketMethod == null) {
                cachedSendPacketMethod = connection.getClass().getMethod("sendPacket", cachedPacketBaseClass);
            }
            cachedSendPacketMethod.invoke(connection, packet);
        } catch (Exception ignored) {
        }
    }

    private String jsonText(String text) {
        return "{\"text\":\"" + escapeJson(text) + "\"}";
    }

    private String escapeJson(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }
}

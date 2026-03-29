package n.plugins.newbedwars.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.BedWarsMode;
import n.plugins.newbedwars.util.LocationUtil;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class BedWarsCommand implements CommandExecutor, TabCompleter {

    private final NewBedWars plugin;

    public BedWarsCommand(NewBedWars plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        if (subCommand.equals("create")) {
            return handleCreate(sender, args);
        }
        if (subCommand.equals("delete")) {
            return handleDelete(sender, args);
        }
        if (subCommand.equals("list")) {
            return handleList(sender);
        }
        if (subCommand.equals("mode")) {
            return handleMode(sender, args);
        }
        if (subCommand.equals("setup")) {
            return handleSetup(sender, args);
        }
        if (subCommand.equals("setlobby")) {
            return handleSetLobby(sender);
        }
        if (subCommand.equals("join")) {
            return handleJoin(sender, args);
        }
        if (subCommand.equals("leave")) {
            return handleLeave(sender);
        }
        if (subCommand.equals("reload")) {
            return handleReload(sender);
        }
        if (subCommand.equals("start")) {
            return handleStart(sender);
        }
        if (subCommand.equals("npc")) {
            return handleNpc(sender, args);
        }

        sendHelp(sender);
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }

        if (args.length < 2) {
            plugin.getMessageManager().send(sender, "errors.invalid-usage", Collections.singletonMap("usage", "/bw create <arena> [world] [mode]"));
            return true;
        }

        if (!(sender instanceof Player) && args.length < 3) {
            plugin.getMessageManager().send(sender, "errors.invalid-usage", Collections.singletonMap("usage", "/bw create <arena> <world> [mode]"));
            return true;
        }

        String name = args[1];
        if (plugin.getArenaManager().getConfiguredArena(name) != null) {
            plugin.getMessageManager().send(sender, "general.arena-exists");
            return true;
        }

        World world = null;
        BedWarsMode mode = BedWarsMode.ONE_VS_ONE;

        if (args.length >= 3) {
            World explicitWorld = LocationUtil.ensureWorldLoaded(args[2]);
            BedWarsMode inlineMode = BedWarsMode.fromInput(args[2]);

            if (explicitWorld != null) {
                world = explicitWorld;
                if (args.length >= 4) {
                    mode = parseMode(sender, args[3]);
                    if (mode == null) {
                        return true;
                    }
                }
            } else if (inlineMode != null && sender instanceof Player) {
                world = ((Player) sender).getWorld();
                mode = inlineMode;
            } else {
                plugin.getMessageManager().send(sender, "general.invalid-world");
                return true;
            }
        } else {
            world = ((Player) sender).getWorld();
        }

        plugin.getArenaManager().createArena(name, world.getName(), mode);
        plugin.getMessageManager().send(sender, "general.arena-created", map(
            "arena", name,
            "mode", mode.getDisplayName()
        ));
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }

        if (args.length < 2) {
            plugin.getMessageManager().send(sender, "errors.invalid-usage", Collections.singletonMap("usage", "/bw delete <arena>"));
            return true;
        }

        Arena arena = plugin.getArenaManager().getConfiguredArena(args[1]);
        if (arena == null) {
            plugin.getMessageManager().send(sender, "general.arena-not-found", Collections.singletonMap("arena", args[1]));
            return true;
        }

        plugin.getArenaManager().deleteArena(args[1]);
        plugin.getMessageManager().send(sender, "general.arena-deleted", Collections.singletonMap("arena", args[1]));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!hasAdmin(sender)) {
            return true;
        }

        if (plugin.getArenaManager().getConfiguredArenas().isEmpty()) {
            plugin.getMessageManager().send(sender, "general.arena-list-empty");
            return true;
        }

        sender.sendMessage(plugin.getMessageManager().get("general.arena-list-header"));
        for (Arena arena : plugin.getArenaManager().getConfiguredArenas()) {
            sender.sendMessage(plugin.getMessageManager().get("general.arena-list-entry", map(
                "arena", arena.getName(),
                "mode", arena.getMode().getDisplayName(),
                "state", arena.getState().name(),
                "players", String.valueOf(arena.getMode().getMaxPlayers()),
                "ready", arena.isReady() ? "§aPronta" : "§cPendente"
            )));
        }
        sender.sendMessage(plugin.getMessageManager().get("general.arena-list-header"));
        return true;
    }

    private boolean handleMode(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }

        if (args.length < 3) {
            plugin.getMessageManager().send(sender, "errors.invalid-usage", Collections.singletonMap("usage", "/bw mode <arena> <modo>"));
            return true;
        }

        Arena arena = plugin.getArenaManager().getConfiguredArena(args[1]);
        if (arena == null) {
            plugin.getMessageManager().send(sender, "general.arena-not-found", Collections.singletonMap("arena", args[1]));
            return true;
        }

        BedWarsMode mode = parseMode(sender, args[2]);
        if (mode == null) {
            return true;
        }

        arena.setMode(mode);
        arena.setState(n.plugins.newbedwars.arena.ArenaState.WAITING);
        plugin.getArenaManager().saveArena(arena);
        plugin.getSetupManager().refreshArenaSetupVisuals(arena);
        plugin.getNpcManager().refreshArenaShopNpcs(arena);
        plugin.getMessageManager().send(sender, "general.mode-updated", map(
            "arena", arena.getName(),
            "mode", mode.getDisplayName()
        ));
        return true;
    }

    private boolean handleSetup(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessageManager().send(sender, "errors.invalid-usage", Collections.singletonMap("usage", "/bw setup <arena>"));
            return true;
        }

        Arena arena = plugin.getArenaManager().getConfiguredArena(args[1]);
        if (arena == null) {
            plugin.getMessageManager().send(sender, "general.arena-not-found", Collections.singletonMap("arena", args[1]));
            return true;
        }

        plugin.getSetupManager().startSession((Player) sender, arena);
        return true;
    }

    private boolean handleSetLobby(CommandSender sender) {
        if (!hasAdmin(sender)) {
            return true;
        }
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }

        Player player = (Player) sender;
        plugin.getLobbyManager().setLobby(player.getLocation());
        plugin.getMessageManager().send(player, "lobby.set");
        return true;
    }

    private boolean handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessageManager().send(sender, "errors.invalid-usage", Collections.singletonMap("usage", "/bw join <arena>"));
            return true;
        }

        Arena arena = plugin.getArenaManager().getConfiguredArena(args[1]);
        if (arena == null) {
            plugin.getMessageManager().send(sender, "general.arena-not-found", Collections.singletonMap("arena", args[1]));
            return true;
        }

        plugin.getGameManager().joinArena((Player) sender, arena);
        return true;
    }

    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }

        plugin.getGameManager().leaveArena((Player) sender, true);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!hasAdmin(sender)) {
            return true;
        }

        plugin.reloadConfig();
        plugin.getMessageManager().reload();
        plugin.getArenaManager().loadArenas();
        plugin.getMessageManager().send(sender, "general.reloaded");
        return true;
    }

    private boolean handleStart(CommandSender sender) {
        if (!hasAdmin(sender)) {
            return true;
        }
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }

        Player player = (Player) sender;
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (arena == null) {
            arena = plugin.getArenaManager().getArenaByWorld(player.getWorld());
        }
        if (arena == null) {
            sender.sendMessage(plugin.getMessageManager().get("prefix") + "§cNenhuma arena BedWars foi encontrada neste mundo.");
            return true;
        }
        if (!plugin.getGameManager().speedUpStart(arena)) {
            plugin.getMessageManager().send(sender, "game.not-enough-players");
            return true;
        }

        sender.sendMessage(plugin.getMessageManager().get("prefix") + "§aA partida da arena §f" + arena.getDisplayName() + " §avai iniciar mais rapido.");
        return true;
    }

    private boolean handleNpc(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }

        if (!(sender instanceof Player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }

        if (args.length < 2) {
            plugin.getMessageManager().send(sender, "errors.invalid-usage", Collections.singletonMap("usage", "/bw npc <modo|skin|remove> ..."));
            return true;
        }

        Player player = (Player) sender;
        String npcCommand = args[1].toLowerCase();
        BedWarsMode mode = BedWarsMode.fromInput(npcCommand);
        if (mode != null) {
            String skin = args.length >= 3 ? args[2] : plugin.getConfig().getString("npc.default-skin", "Steve");
            NPC npc = plugin.getNpcManager().createQueueNpc(player, mode, skin);
            plugin.getMessageManager().send(player, "npc.created", map(
                "id", String.valueOf(npc.getId()),
                "skin", skin,
                "mode", mode.getDisplayName()
            ));
            return true;
        }

        if (npcCommand.equals("skin")) {
            if (args.length < 4) {
                plugin.getMessageManager().send(sender, "errors.invalid-usage", Collections.singletonMap("usage", "/bw npc skin <id> <skin>"));
                return true;
            }

            int id = parseId(args[2]);
            if (id == -1 || !plugin.getNpcManager().setSkin(id, args[3])) {
                plugin.getMessageManager().send(player, "npc.invalid-id");
                return true;
            }

            plugin.getMessageManager().send(player, "npc.skin-updated", map(
                "id", String.valueOf(id),
                "skin", args[3]
            ));
            return true;
        }

        if (npcCommand.equals("remove")) {
            if (args.length < 3) {
                plugin.getMessageManager().send(sender, "errors.invalid-usage", Collections.singletonMap("usage", "/bw npc remove <id>"));
                return true;
            }

            int id = parseId(args[2]);
            if (id == -1 || !plugin.getNpcManager().removeNpc(id)) {
                plugin.getMessageManager().send(player, "npc.invalid-id");
                return true;
            }

            plugin.getMessageManager().send(player, "npc.removed", Collections.singletonMap("id", String.valueOf(id)));
            return true;
        }

        plugin.getMessageManager().send(sender, "errors.invalid-usage", Collections.singletonMap("usage", "/bw npc <modo|skin|remove> ..."));
        return true;
    }

    private boolean hasAdmin(CommandSender sender) {
        if (sender.hasPermission("newbedwars.admin")) {
            return true;
        }
        plugin.getMessageManager().send(sender, "general.no-permission");
        return false;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8§m--------------------------------");
        sender.sendMessage("§b/bw create <arena> [world] [mode] §7- cria uma arena");
        sender.sendMessage("§b/bw delete <arena> §7- remove uma arena");
        sender.sendMessage("§b/bw list §7- lista as arenas");
        sender.sendMessage("§b/bw mode <arena> <mode> §7- altera o modo da arena");
        sender.sendMessage("§b/bw setup <arena> §7- entra no setup");
        sender.sendMessage("§b/bw setlobby §7- salva o lobby principal");
        sender.sendMessage("§b/bw join <arena> §7- entra em uma partida");
        sender.sendMessage("§b/bw leave §7- sai da partida");
        sender.sendMessage("§b/bw npc <mode> [skin] §7- cria um NPC de fila");
        sender.sendMessage("§b/bw npc skin <id> <skin> §7- altera a skin do NPC");
        sender.sendMessage("§b/bw npc remove <id> §7- remove um NPC");
        sender.sendMessage("§b/lobby §7- volta para o lobby");
        sender.sendMessage("§b/bw reload §7- recarrega os arquivos");
        sender.sendMessage("§8§m--------------------------------");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(args[0], Arrays.asList("create", "delete", "list", "mode", "setup", "setlobby", "join", "leave", "start", "npc", "reload"));
        }

        if (args.length == 2 && Arrays.asList("delete", "setup", "join", "mode").contains(args[0].toLowerCase())) {
            List<String> arenas = new ArrayList<String>();
            for (Arena arena : plugin.getArenaManager().getConfiguredArenas()) {
                arenas.add(arena.getName());
            }
            return partial(args[1], arenas);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            List<String> options = new ArrayList<String>(modeOptions());
            options.add("skin");
            options.add("remove");
            return partial(args[1], options);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            List<String> options = new ArrayList<String>();
            for (World world : Bukkit.getWorlds()) {
                options.add(world.getName());
            }
            options.addAll(modeOptions());
            return partial(args[2], options);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("mode")) {
            return partial(args[2], modeOptions());
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("create")) {
            return partial(args[3], modeOptions());
        }

        return Collections.emptyList();
    }

    private List<String> partial(String token, List<String> options) {
        List<String> values = new ArrayList<String>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(token.toLowerCase())) {
                values.add(option);
            }
        }
        return values;
    }

    private int parseId(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private BedWarsMode parseMode(CommandSender sender, String raw) {
        BedWarsMode mode = BedWarsMode.fromInput(raw);
        if (mode != null) {
            return mode;
        }

        plugin.getMessageManager().send(sender, "errors.invalid-mode", Collections.singletonMap("mode", raw));
        return null;
    }

    private List<String> modeOptions() {
        return Arrays.asList("1v1", "2v2", "3v3", "4v4", "solo", "dupla", "trio", "quarteto");
    }

    private Map<String, String> map(String... values) {
        Map<String, String> map = new HashMap<String, String>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(values[i], values[i + 1]);
        }
        return map;
    }
}

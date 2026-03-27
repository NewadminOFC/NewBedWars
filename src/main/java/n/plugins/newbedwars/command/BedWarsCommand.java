package n.plugins.newbedwars.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
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
            plugin.getMessageManager().send(sender, "errors.invalid-usage", Collections.singletonMap("usage", "/bw create <arena> [world]"));
            return true;
        }

        if (!(sender instanceof Player) && args.length < 3) {
            plugin.getMessageManager().send(sender, "errors.invalid-usage", Collections.singletonMap("usage", "/bw create <arena> <world>"));
            return true;
        }

        String name = args[1];
        if (plugin.getArenaManager().getConfiguredArena(name) != null) {
            plugin.getMessageManager().send(sender, "general.arena-exists");
            return true;
        }

        World world;
        if (args.length >= 3) {
            world = LocationUtil.ensureWorldLoaded(args[2]);
            if (world == null) {
                plugin.getMessageManager().send(sender, "general.invalid-world");
                return true;
            }
        } else {
            world = ((Player) sender).getWorld();
        }

        plugin.getArenaManager().createArena(name, world.getName());
        plugin.getMessageManager().send(sender, "general.arena-created", Collections.singletonMap("arena", name));
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
                "state", arena.getState().name(),
                "ready", arena.isReady() ? "§aPronta" : "§cPendente"
            )));
        }
        sender.sendMessage(plugin.getMessageManager().get("general.arena-list-header"));
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

    private boolean handleNpc(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }

        if (!(sender instanceof Player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }

        if (args.length < 2) {
            plugin.getMessageManager().send(sender, "errors.invalid-usage", Collections.singletonMap("usage", "/bw npc <1v1|solo|skin|remove> ..."));
            return true;
        }

        Player player = (Player) sender;
        String npcCommand = args[1].toLowerCase();
        if (npcCommand.equals("solo") || npcCommand.equals("1v1")) {
            String skin = args.length >= 3 ? args[2] : plugin.getConfig().getString("npc.default-skin", "Steve");
            NPC npc = plugin.getNpcManager().createSoloNpc(player, skin);
            plugin.getMessageManager().send(player, "npc.created", map(
                "id", String.valueOf(npc.getId()),
                "skin", skin
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

        plugin.getMessageManager().send(sender, "errors.invalid-usage", Collections.singletonMap("usage", "/bw npc <1v1|solo|skin|remove> ..."));
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
        sender.sendMessage("Â§8Â§m--------------------------------");
        sender.sendMessage("Â§b/bw create <arena> [world] Â§7- cria uma arena");
        sender.sendMessage("Â§b/bw delete <arena> Â§7- remove uma arena");
        sender.sendMessage("Â§b/bw list Â§7- lista as arenas");
        sender.sendMessage("Â§b/bw setup <arena> Â§7- entra no setup");
        sender.sendMessage("Â§b/bw setlobby Â§7- salva o lobby principal");
        sender.sendMessage("Â§b/bw join <arena> Â§7- entra em uma partida");
        sender.sendMessage("Â§b/bw leave Â§7- sai da partida");
        sender.sendMessage("Â§b/bw npc solo [skin] Â§7- cria um NPC SOLO");
        sender.sendMessage("Â§b/bw npc skin <id> <skin> Â§7- altera a skin do NPC");
        sender.sendMessage("Â§b/bw npc remove <id> Â§7- remove um NPC");
        sender.sendMessage("Â§b/lobby Â§7- volta para o lobby");
        sender.sendMessage("Â§b/bw reload Â§7- recarrega os arquivos");
        sender.sendMessage("Â§8Â§m--------------------------------");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(args[0], Arrays.asList("create", "delete", "list", "setup", "setlobby", "join", "leave", "npc", "reload"));
        }

        if (args.length == 2 && Arrays.asList("delete", "setup", "join").contains(args[0].toLowerCase())) {
            List<String> arenas = new ArrayList<String>();
            for (Arena arena : plugin.getArenaManager().getConfiguredArenas()) {
                arenas.add(arena.getName());
            }
            return partial(args[1], arenas);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            return partial(args[1], Arrays.asList("1v1", "solo", "skin", "remove"));
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            List<String> worlds = new ArrayList<String>();
            for (World world : Bukkit.getWorlds()) {
                worlds.add(world.getName());
            }
            return partial(args[2], worlds);
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

    private Map<String, String> map(String... values) {
        Map<String, String> map = new HashMap<String, String>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(values[i], values[i + 1]);
        }
        return map;
    }
}

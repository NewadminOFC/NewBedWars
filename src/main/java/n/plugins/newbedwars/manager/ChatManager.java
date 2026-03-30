package n.plugins.newbedwars.manager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaState;
import n.plugins.newbedwars.arena.TeamColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class ChatManager {

    private final NewBedWars plugin;

    public ChatManager(NewBedWars plugin) {
        this.plugin = plugin;
    }

    public boolean isArenaChat(Player player) {
        return resolveArena(player) != null;
    }

    public void handleArenaChat(Player player, String message, boolean forceGlobal) {
        if (player == null || message == null) {
            return;
        }

        Arena arena = resolveArena(player);
        if (arena == null) {
            return;
        }

        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        if (isSpectatorChat(arena, player.getUniqueId())) {
            sendFormatted(getSpectatorRecipients(arena), "chat.spectator-format", arena, player, trimmed);
            return;
        }

        if (forceGlobal) {
            sendFormatted(getArenaRecipients(arena), "chat.global-format", arena, player, trimmed);
            return;
        }

        if (arena.getState() == ArenaState.INGAME && shouldUseTeamChat(arena, player.getUniqueId())) {
            sendFormatted(getTeamRecipients(arena, player.getUniqueId()), "chat.team-format", arena, player, trimmed);
            return;
        }

        sendFormatted(getArenaRecipients(arena), "chat.waiting-format", arena, player, trimmed);
    }

    public boolean handleGlobalChatCommand(Player player, String message) {
        if (player == null) {
            return true;
        }

        Arena arena = resolveArena(player);
        if (arena == null) {
            plugin.getMessageManager().send(player, "chat.global-command-only-arena");
            return true;
        }

        if (arena.getState() != ArenaState.INGAME) {
            plugin.getMessageManager().send(player, "chat.global-command-only-ingame");
            return true;
        }

        if (arena.getSpectators().contains(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "chat.global-command-spectator-blocked");
            return true;
        }

        if (message == null || message.trim().isEmpty()) {
            plugin.getMessageManager().send(player, "chat.global-command-usage");
            return true;
        }

        handleArenaChat(player, message, true);
        return true;
    }

    private Arena resolveArena(Player player) {
        if (player == null) {
            return null;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (arena != null) {
            return arena;
        }

        return plugin.getArenaManager().getArenaByWorld(player.getWorld());
    }

    private boolean shouldUseTeamChat(Arena arena, UUID uniqueId) {
        return arena != null
            && uniqueId != null
            && !arena.getSpectators().contains(uniqueId)
            && plugin.getTeamManager().getTeamSize(arena) > 1
            && plugin.getTeamManager().getColor(arena, uniqueId) != null;
    }

    private boolean isSpectatorChat(Arena arena, UUID uniqueId) {
        return arena != null
            && uniqueId != null
            && arena.getState() == ArenaState.INGAME
            && arena.getSpectators().contains(uniqueId);
    }

    private Collection<Player> getArenaRecipients(Arena arena) {
        List<Player> recipients = new ArrayList<Player>();
        if (arena == null) {
            return recipients;
        }

        for (UUID uniqueId : arena.getPlayers()) {
            Player recipient = Bukkit.getPlayer(uniqueId);
            if (recipient != null && recipient.isOnline()) {
                recipients.add(recipient);
            }
        }
        return recipients;
    }

    private Collection<Player> getSpectatorRecipients(Arena arena) {
        List<Player> recipients = new ArrayList<Player>();
        if (arena == null) {
            return recipients;
        }

        for (UUID uniqueId : arena.getSpectators()) {
            Player recipient = Bukkit.getPlayer(uniqueId);
            if (recipient != null && recipient.isOnline()) {
                recipients.add(recipient);
            }
        }
        return recipients;
    }

    private Collection<Player> getTeamRecipients(Arena arena, UUID uniqueId) {
        List<Player> recipients = new ArrayList<Player>();
        if (arena == null || uniqueId == null) {
            return recipients;
        }

        TeamColor color = plugin.getTeamManager().getColor(arena, uniqueId);
        if (color == null) {
            return recipients;
        }

        for (UUID memberId : plugin.getTeamManager().getPlayersInTeam(arena, color)) {
            if (arena.getSpectators().contains(memberId)) {
                continue;
            }

            Player recipient = Bukkit.getPlayer(memberId);
            if (recipient != null && recipient.isOnline()) {
                recipients.add(recipient);
            }
        }
        return recipients;
    }

    private void sendFormatted(Collection<Player> recipients, String path, Arena arena, Player sender, String message) {
        if (sender == null || recipients == null || recipients.isEmpty()) {
            return;
        }

        Map<String, String> placeholders = new HashMap<String, String>();
        placeholders.put("arena", arena == null ? "" : arena.getDisplayName());
        placeholders.put("mode", arena == null ? "" : arena.getMode().getDisplayName());
        placeholders.put("player", sender.getName());
        placeholders.put("message", message);
        placeholders.put("team", arena == null ? "\u00A77Sem time" : plugin.getTeamManager().getDisplay(arena, sender.getUniqueId()));

        String formatted = plugin.getMessageManager().get(path, placeholders);
        for (Player recipient : recipients) {
            recipient.sendMessage(formatted);
        }
    }
}

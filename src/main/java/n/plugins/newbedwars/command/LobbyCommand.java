package n.plugins.newbedwars.command;

import java.util.Collections;
import n.plugins.newbedwars.NewBedWars;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LobbyCommand implements CommandExecutor {

    private final NewBedWars plugin;

    public LobbyCommand(NewBedWars plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }

        Player player = (Player) sender;
        if (plugin.getSetupManager().isInSetup(player)) {
            plugin.getSetupManager().stopSession(player, false);
        }

        if (plugin.getArenaManager().getArenaByPlayer(player.getUniqueId()) != null) {
            plugin.getGameManager().leaveArena(player, true);
        }

        if (!plugin.getLobbyManager().teleportToMainWorld(player)) {
            plugin.getMessageManager().send(player, "lobby.not-set");
            return true;
        }

        plugin.getMessageManager().send(player, "lobby.teleported");
        return true;
    }
}

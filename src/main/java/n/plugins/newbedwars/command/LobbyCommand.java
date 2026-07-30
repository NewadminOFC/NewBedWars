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

        boolean wasInArena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId()) != null;
        if (wasInArena) {
            plugin.getGameManager().leaveArena(player, true);
        }

        if (!plugin.getLobbyManager().teleportToMainWorld(player)) {
            plugin.getMessageManager().send(player, "lobby.not-set");
            return true;
        }

        if (!wasInArena) {
            plugin.getMessageManager().send(player, "lobby.teleported");
        }
        return true;
    }
}

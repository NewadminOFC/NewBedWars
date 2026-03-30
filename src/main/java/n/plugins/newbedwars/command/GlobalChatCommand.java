package n.plugins.newbedwars.command;

import n.plugins.newbedwars.NewBedWars;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GlobalChatCommand implements CommandExecutor {

    private final NewBedWars plugin;

    public GlobalChatCommand(NewBedWars plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }

        StringBuilder builder = new StringBuilder();
        for (String arg : args) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(arg);
        }

        return plugin.getChatManager().handleGlobalChatCommand((Player) sender, builder.toString());
    }
}

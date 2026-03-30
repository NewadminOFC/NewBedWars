package n.plugins.newbedwars.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import n.plugins.newbedwars.NewBedWars;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {

    private final NewBedWars plugin;

    public ChatListener(NewBedWars plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        event.setCancelled(true);
        event.getRecipients().clear();
        final UUID uniqueId = player.getUniqueId();
        final String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                Player online = Bukkit.getPlayer(uniqueId);
                if (online == null || !online.isOnline()) {
                    return;
                }

                if (plugin.getChatManager().isArenaChat(online)) {
                    plugin.getChatManager().handleArenaChat(online, message, false);
                    return;
                }

                Map<String, String> placeholders = new HashMap<String, String>();
                placeholders.put("player", online.getDisplayName());
                placeholders.put("message", message);
                placeholders.put("world", online.getWorld().getName());
                String formatted = plugin.getMessageManager().get("chat.world-format", placeholders);

                for (Player viewer : online.getWorld().getPlayers()) {
                    if (viewer == null || !viewer.isOnline()) {
                        continue;
                    }

                    viewer.sendMessage(formatted);
                }
            }
        });
    }
}

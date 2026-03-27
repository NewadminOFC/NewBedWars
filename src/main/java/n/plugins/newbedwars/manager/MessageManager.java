package n.plugins.newbedwars.manager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class MessageManager {

    private final NewBedWars plugin;
    private File file;
    private FileConfiguration configuration;

    public MessageManager(NewBedWars plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        this.configuration = YamlConfiguration.loadConfiguration(file);

        InputStream resource = plugin.getResource("messages.yml");
        if (resource != null) {
            try {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(resource));
                this.configuration.setDefaults(defaults);
            } finally {
                try {
                    resource.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public FileConfiguration getConfiguration() {
        return configuration;
    }

    public String get(String path) {
        String text = configuration.getString(path, path);
        return ChatUtil.color(text.replace("%prefix%", ChatUtil.color(configuration.getString("prefix", ""))));
    }

    public String get(String path, Map<String, String> placeholders) {
        String formatted = get(path);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                formatted = formatted.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return formatted;
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(get(path));
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(get(path, placeholders));
    }

    public void save() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }
}

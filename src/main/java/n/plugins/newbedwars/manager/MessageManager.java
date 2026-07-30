package n.plugins.newbedwars.manager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
    private String cachedPrefix;
    private final Map<String, String> messageCache = new HashMap<String, String>();
    private final Map<String, List<String>> listCache = new HashMap<String, List<String>>();

    public MessageManager(NewBedWars plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        this.configuration = YamlConfiguration.loadConfiguration(file);
        this.messageCache.clear();
        this.listCache.clear();

        InputStream resource = plugin.getResource("messages.yml");
        if (resource != null) {
            try {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(resource));
                int bundledVersion = Math.max(1, defaults.getInt("messages-version", 1));
                int installedVersion = this.configuration.getInt("messages-version", 0);
                this.configuration.setDefaults(defaults);
                if (installedVersion < bundledVersion) {
                    this.configuration.options().copyDefaults(true);
                    if (installedVersion < 3
                        && "&8[&b&lBED&f&lWARS&8] &r".equals(this.configuration.getString("prefix"))) {
                        this.configuration.set("prefix", defaults.getString("prefix"));
                    }
                    if (installedVersion < 3
                        && "&a&lPARTIDA INICIADA! &7Proteja sua cama e elimine os adversários."
                            .equals(this.configuration.getString("game.game-started"))) {
                        this.configuration.set("game.game-started", defaults.getString("game.game-started"));
                    }
                    if (installedVersion < 3
                        && "%prefix%&b⏳ Preparando uma instância limpa do mapa..."
                            .equals(this.configuration.getString("game.clone-preparing"))) {
                        this.configuration.set("game.clone-preparing", defaults.getString("game.clone-preparing"));
                    }
                    if (installedVersion < 3
                        && "&8BedWars - %mode%".equals(this.configuration.getString("menus.queue.title"))) {
                        this.configuration.set("menus.queue.title", defaults.getString("menus.queue.title"));
                    }
                    this.configuration.set("messages-version", bundledVersion);
                    save();
                    plugin.getLogger().info("messages.yml atualizado para a estrutura " + bundledVersion + ".");
                }
            } finally {
                try {
                    resource.close();
                } catch (IOException ignored) {
                }
            }
        }

        String prefix = configuration.getString("prefix");
        if (prefix == null && configuration.getDefaults() != null) {
            prefix = configuration.getDefaults().getString("prefix", "");
        }
        this.cachedPrefix = ChatUtil.color(prefix == null ? "" : prefix);
    }

    public FileConfiguration getConfiguration() {
        return configuration;
    }

    public String get(String path) {
        String cached = messageCache.get(path);
        if (cached != null) {
            return cached;
        }

        String text = configuration.getString(path);
        if (text == null && configuration.getDefaults() != null) {
            text = configuration.getDefaults().getString(path);
        }
        if (text == null) {
            text = path;
        }

        String formatted = ChatUtil.color(text.replace("%prefix%", cachedPrefix));
        messageCache.put(path, formatted);
        return formatted;
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

    public List<String> getList(String path) {
        List<String> cached = listCache.get(path);
        if (cached != null) {
            return new ArrayList<String>(cached);
        }

        List<String> lines = configuration.getStringList(path);
        if ((lines == null || lines.isEmpty()) && configuration.getDefaults() != null) {
            lines = configuration.getDefaults().getStringList(path);
        }

        List<String> formatted = new ArrayList<String>();
        if (lines != null && !lines.isEmpty()) {
            String prefix = configuration.getString("prefix");
            if (prefix == null && configuration.getDefaults() != null) {
                prefix = configuration.getDefaults().getString("prefix", "");
            }
            if (prefix == null) {
                prefix = "";
            }

            for (String line : lines) {
                if (line == null) {
                    formatted.add("");
                } else {
                    formatted.add(ChatUtil.color(line.replace("%prefix%", ChatUtil.color(prefix))));
                }
            }
            listCache.put(path, Collections.unmodifiableList(new ArrayList<String>(formatted)));
            return formatted;
        }

        String single = configuration.getString(path);
        if (single == null && configuration.getDefaults() != null) {
            single = configuration.getDefaults().getString(path);
        }
        if (single != null) {
            formatted.add(get(path));
        }
        listCache.put(path, Collections.unmodifiableList(new ArrayList<String>(formatted)));
        return formatted;
    }

    public List<String> getList(String path, Map<String, String> placeholders) {
        List<String> lines = getList(path);
        List<String> formatted = new ArrayList<String>(lines.size());
        for (String line : lines) {
            String resolved = line;
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    resolved = resolved.replace("%" + entry.getKey() + "%", entry.getValue());
                }
            }
            formatted.add(resolved);
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
            plugin.getLogger().warning("Nao foi possivel salvar messages.yml: " + exception.getMessage());
        }
    }
}

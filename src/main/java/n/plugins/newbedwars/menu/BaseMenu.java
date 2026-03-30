package n.plugins.newbedwars.menu;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import n.plugins.newbedwars.NewBedWars;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public abstract class BaseMenu implements InventoryHolder {

    protected final NewBedWars plugin;
    protected Inventory inventory;

    protected BaseMenu(NewBedWars plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        this.inventory = Bukkit.createInventory(this, getSize(), getTitle());
        draw(player);
        plugin.getMenuManager().track(player, this);
        player.setItemOnCursor(null);
        player.openInventory(inventory);
    }

    protected abstract String getTitle();

    protected abstract int getSize();

    protected abstract void draw(Player player);

    public abstract void handleClick(Player player, int slot, ClickType clickType);

    protected String text(String path) {
        return plugin.getMessageManager().get(path);
    }

    protected String text(String path, Map<String, String> placeholders) {
        return plugin.getMessageManager().get(path, placeholders);
    }

    protected List<String> textList(String path) {
        return plugin.getMessageManager().getList(path);
    }

    protected List<String> textList(String path, Map<String, String> placeholders) {
        return plugin.getMessageManager().getList(path, placeholders == null ? Collections.<String, String>emptyMap() : placeholders);
    }

    protected Map<String, String> placeholders(String... entries) {
        Map<String, String> values = new HashMap<String, String>();
        if (entries == null) {
            return values;
        }

        for (int index = 0; index + 1 < entries.length; index += 2) {
            values.put(entries[index], entries[index + 1]);
        }
        return values;
    }

    public boolean isInventory(Inventory other) {
        return inventory != null && inventory.equals(other);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

package n.plugins.newbedwars.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaTeam;
import n.plugins.newbedwars.util.ChatUtil;
import n.plugins.newbedwars.util.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public class UpgradeShopMenu extends BaseMenu {

    private final Map<Integer, String> upgrades;

    public UpgradeShopMenu(NewBedWars plugin) {
        super(plugin);
        this.upgrades = new HashMap<Integer, String>();
    }

    @Override
    protected String getTitle() {
        return plugin.getMessageManager().get("shops.upgrades-title");
    }

    @Override
    protected int getSize() {
        int configured = plugin.getConfig().getInt("upgrade-shop.size", 27);
        if (configured < 9) {
            return 9;
        }
        if (configured > 54) {
            return 54;
        }
        return configured - (configured % 9 == 0 ? 0 : configured % 9);
    }

    @Override
    protected void draw(Player player) {
        upgrades.clear();
        fillBackground();

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        ArenaTeam team = arena == null ? null : plugin.getTeamManager().getTeam(arena, player.getUniqueId());

        drawInfo(team);
        drawCloseButton();
        drawUpgrades(team);
    }

    @Override
    public void handleClick(Player player, int slot, ClickType clickType) {
        int closeSlot = plugin.getConfig().getInt("upgrade-shop.close.slot", 22);
        if (closeSlot >= 0 && slot == closeSlot) {
            player.closeInventory();
            return;
        }

        String upgradeKey = upgrades.get(slot);
        if (upgradeKey == null) {
            return;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        ArenaTeam team = arena == null ? null : plugin.getTeamManager().getTeam(arena, player.getUniqueId());
        if (arena == null || team == null) {
            player.closeInventory();
            return;
        }

        plugin.getShopManager().buyConfiguredUpgrade(player, arena, team, upgradeKey);
        open(player);
    }

    private void drawInfo(ArenaTeam team) {
        ConfigurationSection info = plugin.getConfig().getConfigurationSection("upgrade-shop.info");
        if (info == null) {
            return;
        }

        int slot = info.getInt("slot", 4);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        inventory.setItem(slot, buildItem(info, "EMERALD", 0, placeholders(
            "team", team == null ? "&7Sem time" : team.getColor().getColoredName()
        )));
    }

    private void drawCloseButton() {
        ConfigurationSection close = plugin.getConfig().getConfigurationSection("upgrade-shop.close");
        if (close == null) {
            inventory.setItem(22, new ItemBuilder(Material.BARRIER).name("&cFechar").build());
            return;
        }

        int slot = close.getInt("slot", 22);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, buildItem(close, "BARRIER", 0, null));
    }

    private void drawUpgrades(ArenaTeam team) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("upgrade-shop.upgrades");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection upgrade = section.getConfigurationSection(key);
            if (upgrade == null) {
                continue;
            }

            int slot = upgrade.getInt("slot", -1);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }

            int level = plugin.getShopManager().getUpgradeLevel(team, key);
            int maxLevel = plugin.getShopManager().getUpgradeMaxLevel(key);
            int nextCost = plugin.getShopManager().getNextUpgradeCost(team, key);
            Material currency = plugin.getShopManager().getUpgradeCurrency(key);

            String status;
            if (maxLevel <= 1 && level >= maxLevel) {
                status = plugin.getConfig().getString("upgrade-shop.texts.purchased", "&aComprado");
            } else if (level >= maxLevel) {
                status = plugin.getConfig().getString("upgrade-shop.texts.maxed", "&aMaximo");
            } else {
                status = plugin.getConfig().getString("upgrade-shop.texts.click", "&eClique para comprar");
            }

            String costDisplay = nextCost <= 0
                ? plugin.getConfig().getString("upgrade-shop.texts.maxed", "&aMaximo")
                : plugin.getShopManager().getCurrencyColor(currency) + nextCost + " " + plugin.getShopManager().getCurrencyName(currency, nextCost);

            ItemBuilder builder = buildItemBuilder(upgrade, "STONE", 0, placeholders(
                "level", String.valueOf(level),
                "max_level", String.valueOf(maxLevel),
                "cost_amount", String.valueOf(nextCost),
                "cost_name", plugin.getShopManager().getCurrencyName(currency, Math.max(1, nextCost)),
                "cost_color", plugin.getShopManager().getCurrencyColor(currency),
                "cost_display", costDisplay,
                "status", status,
                "team", team == null ? "&7Sem time" : team.getColor().getColoredName()
            ));

            if (upgrade.getBoolean("glow-when-purchased", true) && level >= maxLevel) {
                builder.glow();
            }

            inventory.setItem(slot, builder.build());
            upgrades.put(slot, key);
        }
    }

    private void fillBackground() {
        ConfigurationSection background = plugin.getConfig().getConfigurationSection("upgrade-shop.background");
        if (background != null && !background.getBoolean("enabled", true)) {
            return;
        }

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, background == null
                ? new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (short) 15).name("&8 ").build()
                : buildItem(background, "STAINED_GLASS_PANE", 15, null));
        }
    }

    private ItemStackWrapper buildConfiguredItem(ConfigurationSection section, String defaultMaterial, int defaultData, Map<String, String> placeholders) {
        Material material = parseMaterial(section.getString("material"), parseMaterial(defaultMaterial, Material.STONE));
        short data = (short) section.getInt("data", defaultData);
        int amount = Math.max(1, section.getInt("amount", 1));

        ItemBuilder builder = new ItemBuilder(material, amount, data).name(replace(section.getString("name", "&fItem"), placeholders));
        List<String> lore = section.getStringList("lore");
        if (!lore.isEmpty()) {
            List<String> renderedLore = new ArrayList<String>();
            for (String line : lore) {
                renderedLore.add(replace(line, placeholders));
            }
            builder.lore(renderedLore);
        }

        if (section.getBoolean("glow", false)) {
            builder.glow();
        }

        return new ItemStackWrapper(builder);
    }

    private org.bukkit.inventory.ItemStack buildItem(ConfigurationSection section, String defaultMaterial, int defaultData, Map<String, String> placeholders) {
        return buildConfiguredItem(section, defaultMaterial, defaultData, placeholders).builder.build();
    }

    private ItemBuilder buildItemBuilder(ConfigurationSection section, String defaultMaterial, int defaultData, Map<String, String> placeholders) {
        return buildConfiguredItem(section, defaultMaterial, defaultData, placeholders).builder;
    }

    private Material parseMaterial(String raw, Material fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }

        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private Map<String, String> placeholders(String... entries) {
        Map<String, String> map = new HashMap<String, String>();
        if (entries == null) {
            return map;
        }

        for (int index = 0; index + 1 < entries.length; index += 2) {
            map.put(entries[index], entries[index + 1]);
        }
        return map;
    }

    private String replace(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return result;
    }

    private String clean(String text) {
        return ChatColor.stripColor(ChatUtil.color(text == null ? "" : text));
    }

    private static final class ItemStackWrapper {

        private final ItemBuilder builder;

        private ItemStackWrapper(ItemBuilder builder) {
            this.builder = builder;
        }
    }
}

package n.plugins.newbedwars.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public class ItemShopMenu extends BaseMenu {

    private final Map<Integer, Offer> offers;
    private final Map<Integer, String> categorySlots;
    private final Map<Integer, Integer> quickAccessPanelSlots;
    private final String categoryKey;
    private final String quickAccessEditItemKey;

    private static final class Offer {
        private final String itemKey;
        private final ConfigurationSection section;

        private Offer(String itemKey, ConfigurationSection section) {
            this.itemKey = itemKey;
            this.section = section;
        }
    }

    public ItemShopMenu(NewBedWars plugin) {
        this(plugin, plugin.getConfig().getString("item-shop.default-category", "quick-buy"), null);
    }

    public ItemShopMenu(NewBedWars plugin, String categoryKey) {
        this(plugin, categoryKey, null);
    }

    public ItemShopMenu(NewBedWars plugin, String categoryKey, String quickAccessEditItemKey) {
        super(plugin);
        this.offers = new HashMap<Integer, Offer>();
        this.categorySlots = new HashMap<Integer, String>();
        this.quickAccessPanelSlots = new HashMap<Integer, Integer>();
        this.categoryKey = categoryKey == null || categoryKey.trim().isEmpty() ? "quick-buy" : categoryKey;
        this.quickAccessEditItemKey = quickAccessEditItemKey == null || quickAccessEditItemKey.trim().isEmpty()
            ? null
            : quickAccessEditItemKey;
    }

    @Override
    protected String getTitle() {
        return plugin.getMessageManager().get("shops.items-title");
    }

    @Override
    protected int getSize() {
        int configured = plugin.getConfig().getInt("item-shop.size", 54);
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
        offers.clear();
        categorySlots.clear();
        quickAccessPanelSlots.clear();
        fillBackground();

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        ArenaTeam team = arena == null ? null : plugin.getTeamManager().getTeam(arena, player.getUniqueId());

        drawInfo(team);
        drawCloseButton();
        drawCategories();
        drawCategoryPanels();
        drawOffers(player, arena, team);
    }

    @Override
    public void handleClick(Player player, int slot, ClickType clickType) {
        int closeSlot = plugin.getConfig().getInt("item-shop.close.slot", 49);
        if (closeSlot >= 0 && slot == closeSlot) {
            player.closeInventory();
            return;
        }

        Integer quickAccessIndex = quickAccessPanelSlots.get(slot);
        if (quickAccessIndex != null && quickAccessEditItemKey != null) {
            if (plugin.getShopManager().addQuickAccessItem(player.getUniqueId(), quickAccessEditItemKey, quickAccessIndex.intValue(), getQuickAccessSlots().size())) {
                plugin.getMessageManager().send(player, "shops.quick-access-added", placeholders(
                    "item", getOfferDisplayName(quickAccessEditItemKey)
                ));
            }
            new ItemShopMenu(plugin, "quick-buy").open(player);
            return;
        }

        String selectedCategory = categorySlots.get(slot);
        if (selectedCategory != null) {
            new ItemShopMenu(plugin, selectedCategory).open(player);
            return;
        }

        Offer offer = offers.get(slot);
        if (offer == null) {
            return;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        ArenaTeam team = arena == null ? null : plugin.getTeamManager().getTeam(arena, player.getUniqueId());
        if (arena == null || team == null) {
            player.closeInventory();
            return;
        }

        if (clickType.isShiftClick()) {
            if (isQuickBuyCategory() && clickType.isRightClick()) {
                if (plugin.getShopManager().removeQuickAccessItem(player.getUniqueId(), offer.itemKey, getQuickAccessSlots().size())) {
                    plugin.getMessageManager().send(player, "shops.quick-access-removed", placeholders(
                        "item", getOfferDisplayName(offer.itemKey)
                    ));
                }
                new ItemShopMenu(plugin, "quick-buy").open(player);
                return;
            }

            if (!isQuickBuyCategory()) {
                if (plugin.getShopManager().hasQuickAccessItem(player.getUniqueId(), offer.itemKey, getQuickAccessSlots().size())) {
                    plugin.getMessageManager().send(player, "shops.quick-access-already-added", placeholders(
                        "item", getOfferDisplayName(offer.itemKey)
                    ));
                    return;
                }

                if (!hasAvailableQuickAccessSlot(player)) {
                    plugin.getMessageManager().send(player, "shops.quick-access-full");
                    return;
                }

                plugin.getMessageManager().send(player, "shops.quick-access-pick-slot", placeholders(
                    "item", getOfferDisplayName(offer.itemKey)
                ));
                new ItemShopMenu(plugin, "quick-buy", offer.itemKey).open(player);
                return;
            }
        }

        buyConfiguredOffer(player, arena, team, offer.itemKey, offer.section);
        open(player);
    }

    private void drawInfo(ArenaTeam team) {
        ConfigurationSection info = plugin.getConfig().getConfigurationSection("item-shop.info");
        if (info == null) {
            return;
        }

        int slot = info.getInt("slot", 4);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        String categoryName = getCategoryDisplayName(categoryKey);
        inventory.setItem(slot, buildItem(info, "BOOK", 0, new String[][] {
            {"%category%", categoryName},
            {"%team%", team == null ? "&7Sem time" : team.getColor().getColoredName()}
        }));
    }

    private void drawCloseButton() {
        ConfigurationSection close = plugin.getConfig().getConfigurationSection("item-shop.close");
        if (close == null) {
            inventory.setItem(49, new ItemBuilder(Material.BARRIER).name("&cFechar").build());
            return;
        }

        int slot = close.getInt("slot", 49);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, buildItem(close, "BARRIER", 0, null));
    }

    private void drawCategories() {
        ConfigurationSection categories = plugin.getConfig().getConfigurationSection("item-shop.categories");
        if (categories == null) {
            return;
        }

        for (String key : categories.getKeys(false)) {
            ConfigurationSection section = categories.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            int slot = section.getInt("slot", -1);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }

            ItemBuilder builder = buildItemBuilder(section, "STONE", 0, null);
            if (key.equalsIgnoreCase(categoryKey)) {
                builder.glow();
            }

            inventory.setItem(slot, builder.build());
            categorySlots.put(slot, key);
        }
    }

    private void drawCategoryPanels() {
        ConfigurationSection panels = plugin.getConfig().getConfigurationSection("item-shop.category-panels");
        if (panels == null || !panels.getBoolean("enabled", true)) {
            return;
        }

        for (Map.Entry<Integer, String> entry : categorySlots.entrySet()) {
            int categorySlot = entry.getKey().intValue();
            String key = entry.getValue();
            ConfigurationSection category = plugin.getConfig().getConfigurationSection("item-shop.categories." + key);
            if (category == null) {
                continue;
            }

            int slot = category.getInt("panel-slot", categorySlot + panels.getInt("row-offset", 9));
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }

            boolean selected = key.equalsIgnoreCase(categoryKey);
            ConfigurationSection source = selected
                ? panels.getConfigurationSection("selected")
                : panels.getConfigurationSection("default");
            if (source == null) {
                continue;
            }

            ItemBuilder builder = buildItemBuilder(source, "STAINED_GLASS_PANE", selected ? 5 : 7, null);
            if (selected && panels.getBoolean("selected.glow", true)) {
                builder.glow();
            }
            inventory.setItem(slot, builder.build());
        }
    }

    private void drawOffers(Player player, Arena arena, ArenaTeam team) {
        if (isQuickBuyCategory()) {
            drawQuickAccessOffers(player, arena, team);
            return;
        }

        ConfigurationSection items = plugin.getConfig().getConfigurationSection("item-shop.items");
        if (items == null) {
            return;
        }

        List<OfferEntry> visibleOffers = new ArrayList<OfferEntry>();
        for (String key : items.getKeys(false)) {
            ConfigurationSection section = items.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            if (!shouldShowOffer(section)) {
                continue;
            }

            visibleOffers.add(new OfferEntry(key, section, section.getInt("slot", Integer.MAX_VALUE)));
        }

        Collections.sort(visibleOffers, new Comparator<OfferEntry>() {
            @Override
            public int compare(OfferEntry first, OfferEntry second) {
                if (first.order != second.order) {
                    return Integer.compare(first.order, second.order);
                }
                return first.itemKey.compareToIgnoreCase(second.itemKey);
            }
        });

        List<Integer> displaySlots = getDisplaySlots();
        if (!displaySlots.isEmpty()) {
            int limit = Math.min(displaySlots.size(), visibleOffers.size());
            for (int index = 0; index < limit; index++) {
                renderOffer(displaySlots.get(index).intValue(), visibleOffers.get(index), player, arena, team);
            }
            return;
        }

        for (OfferEntry offer : visibleOffers) {
            int slot = offer.section.getInt("slot", -1);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }

            renderOffer(slot, offer, player, arena, team);
        }
    }

    private void renderOffer(int slot, OfferEntry offer, Player player, Arena arena, ArenaTeam team) {
        if (offer == null || offer.section == null || slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        String action = offer.section.getString("action", "item").toLowerCase(Locale.ENGLISH);
        if ("pickaxe".equals(action)) {
            renderTieredToolOffer(slot, offer, player, arena, team, "pickaxe");
            return;
        }
        if ("axe".equals(action)) {
            renderTieredToolOffer(slot, offer, player, arena, team, "axe");
            return;
        }

        Material costType = parseMaterial(offer.section.getString("cost.material"), Material.IRON_INGOT);
        int costAmount = Math.max(1, offer.section.getInt("cost.amount", 1));
        String description = offer.section.getString("description", "");

        String[] placeholders = new String[] {
            "%cost_amount%", String.valueOf(costAmount),
            "%cost_name%", plugin.getShopManager().getCurrencyName(costType, costAmount),
            "%cost_color%", plugin.getShopManager().getCurrencyColor(costType),
            "%description%", description
        };

        ItemBuilder builder = buildOfferIcon(offer.section, team, placeholders);
        inventory.setItem(slot, builder.build());
        offers.put(slot, new Offer(offer.itemKey, offer.section));
    }

    private void drawQuickAccessOffers(Player player, Arena arena, ArenaTeam team) {
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("item-shop.items");
        if (items == null) {
            return;
        }

        List<Integer> quickSlots = getQuickAccessSlots();
        List<String> layout = plugin.getShopManager().getQuickAccessLayout(player.getUniqueId(), quickSlots.size());
        for (int index = 0; index < quickSlots.size(); index++) {
            int slot = quickSlots.get(index).intValue();
            String itemKey = index < layout.size() ? layout.get(index) : null;

            if (itemKey != null) {
                ConfigurationSection section = items.getConfigurationSection(itemKey);
                if (section != null) {
                    renderOffer(slot, new OfferEntry(itemKey, section, index), player, arena, team);
                    continue;
                }
            }

            if (quickAccessEditItemKey != null && canUseQuickAccessPanel(slot, itemKey)) {
                inventory.setItem(slot, buildQuickAccessPanelItem());
                quickAccessPanelSlots.put(Integer.valueOf(slot), Integer.valueOf(index));
            }
        }
    }

    private void renderTieredToolOffer(int slot, OfferEntry offer, Player player, Arena arena, ArenaTeam team, String toolType) {
        int currentTier = getCurrentToolTier(arena, player, toolType);
        int nextTier = currentTier + 1;
        ConfigurationSection nextTierSection = getToolTierSection(offer.section, nextTier);
        boolean maxed = nextTierSection == null;
        ConfigurationSection displaySection = maxed ? getToolTierSection(offer.section, currentTier) : nextTierSection;
        if (displaySection == null) {
            return;
        }

        Material costType = parseMaterial(displaySection.getString("cost.material"), Material.IRON_INGOT);
        int costAmount = maxed ? 0 : Math.max(1, displaySection.getInt("cost.amount", 1));
        String description = displaySection.getString("description", "");
        String status = maxed
            ? plugin.getConfig().getString("item-shop.texts.maxed", "&aMaximo")
            : plugin.getConfig().getString("item-shop.texts.buy", "&eClique para comprar");
        String costDisplay = maxed
            ? plugin.getConfig().getString("item-shop.texts.maxed", "&aMaximo")
            : plugin.getShopManager().getCurrencyColor(costType) + costAmount + " " + plugin.getShopManager().getCurrencyName(costType, costAmount);

        String[] placeholders = new String[] {
            "%cost_amount%", String.valueOf(costAmount),
            "%cost_name%", plugin.getShopManager().getCurrencyName(costType, Math.max(1, costAmount)),
            "%cost_color%", plugin.getShopManager().getCurrencyColor(costType),
            "%description%", description,
            "%current_tier%", String.valueOf(currentTier),
            "%next_tier%", String.valueOf(maxed ? currentTier : nextTier),
            "%status%", status,
            "%cost_display%", costDisplay
        };

        ItemBuilder builder = buildOfferIcon(displaySection, team, placeholders);
        if (maxed) {
            builder.glow();
        }
        inventory.setItem(slot, builder.build());
        offers.put(slot, new Offer(offer.itemKey, offer.section));
    }

    private boolean shouldShowOffer(ConfigurationSection section) {
        if (section == null) {
            return false;
        }

        if ("quick-buy".equalsIgnoreCase(categoryKey)) {
            return section.getBoolean("quick-buy", false);
        }

        String targetCategory = section.getString("category", "quick-buy");
        return categoryKey.equalsIgnoreCase(targetCategory);
    }

    private boolean isQuickBuyCategory() {
        return "quick-buy".equalsIgnoreCase(categoryKey);
    }

    private boolean hasAvailableQuickAccessSlot(Player player) {
        List<Integer> quickSlots = getQuickAccessSlots();
        List<String> layout = plugin.getShopManager().getQuickAccessLayout(player.getUniqueId(), quickSlots.size());
        for (int index = 0; index < quickSlots.size(); index++) {
            String itemKey = index < layout.size() ? layout.get(index) : null;
            if (canUseQuickAccessPanel(quickSlots.get(index).intValue(), itemKey)) {
                return true;
            }
        }
        return false;
    }

    private boolean canUseQuickAccessPanel(int slot, String currentItemKey) {
        if (currentItemKey != null && !currentItemKey.trim().isEmpty()) {
            return false;
        }

        int column = slot % 9;
        return column != 0 && column != 8;
    }

    private ItemStack buildQuickAccessPanelItem() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("item-shop.quick-access-editor.panel");
        String itemName = getOfferDisplayName(quickAccessEditItemKey);
        String[][] placeholders = new String[][] {
            {"%item%", itemName}
        };
        return section == null
            ? new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (short) 5).name("&aColocar aqui").lore(
                java.util.Arrays.asList("&7Item: " + itemName, "", "&eClique para adicionar ao acesso rapido")
            ).glow().build()
            : buildItem(section, "STAINED_GLASS_PANE", 5, placeholders);
    }

    private String getOfferDisplayName(String itemKey) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("item-shop.items." + itemKey);
        return strip(section == null ? itemKey : section.getString("name", itemKey));
    }

    private ItemBuilder buildOfferIcon(ConfigurationSection section, ArenaTeam team, String[] placeholders) {
        ConfigurationSection icon = section.getConfigurationSection("icon");
        Material material = parseMaterial(icon == null ? null : icon.getString("material"), Material.STONE);
        int amount = icon == null ? 1 : Math.max(1, icon.getInt("amount", 1));
        short data = resolveData(icon, team);
        String displayName = icon != null && icon.isString("name") ? icon.getString("name") : section.getString("name", "&fItem");
        ItemBuilder builder = new ItemBuilder(material, amount, data).name(replace(displayName, placeholders));
        if (icon != null && material == Material.SKULL_ITEM && icon.isString("skull-owner")) {
            builder.skullOwner(icon.getString("skull-owner"));
        }

        java.util.List<String> lore = section.getStringList("lore");
        if (lore == null || lore.isEmpty()) {
            lore = java.util.Arrays.asList(
                "&7" + section.getString("description", ""),
                "",
                "&7Custo: %cost_color%%cost_amount% %cost_name%",
                "",
                plugin.getConfig().getString("item-shop.texts.buy", "&eClique para comprar")
            );
        }

        java.util.List<String> renderedLore = new java.util.ArrayList<String>();
        for (String line : lore) {
            renderedLore.add(replace(line, placeholders));
        }

        builder.lore(renderedLore);
        if ((icon != null && icon.getBoolean("glow", false)) || section.getBoolean("glow", false)) {
            builder.glow();
        }
        return builder;
    }

    private void buyConfiguredOffer(Player player, Arena arena, ArenaTeam team, String itemKey, ConfigurationSection section) {
        String action = section.getString("action", "item").toLowerCase(java.util.Locale.ENGLISH);
        Material costType = parseMaterial(section.getString("cost.material"), Material.IRON_INGOT);
        int costAmount = Math.max(1, section.getInt("cost.amount", 1));
        String itemName = strip(section.getString("name", itemKey));

        if ("sword".equals(action)) {
            Material swordMaterial = parseMaterial(section.getString("reward.material"), Material.STONE_SWORD);
            plugin.getShopManager().buySword(player, team, swordMaterial, costType, costAmount, itemName);
            return;
        }

        if ("armor".equals(action)) {
            int armorTier = Math.max(1, section.getInt("reward.armor-tier", 1));
            plugin.getShopManager().buyArmor(player, arena, team, armorTier, costType, costAmount, itemName);
            return;
        }

        if ("pickaxe".equals(action)) {
            plugin.getShopManager().buyPickaxeUpgrade(player, arena, team, section, itemName);
            return;
        }

        if ("axe".equals(action)) {
            plugin.getShopManager().buyAxeUpgrade(player, arena, team, section, itemName);
            return;
        }

        plugin.getShopManager().tryBuy(player, itemName, costType, costAmount, createRewards(section, team));
    }

    private ItemStack[] createRewards(ConfigurationSection section, ArenaTeam team) {
        List<ItemStack> rewards = new ArrayList<ItemStack>();

        ConfigurationSection reward = section.getConfigurationSection("reward");
        if (reward != null) {
            rewards.add(createConfiguredItem(reward, team, Material.STONE, 1, (short) 0, null, null));
        }

        ConfigurationSection rewardsSection = section.getConfigurationSection("rewards");
        if (rewardsSection != null) {
            for (String key : rewardsSection.getKeys(false)) {
                ConfigurationSection rewardEntry = rewardsSection.getConfigurationSection(key);
                if (rewardEntry != null) {
                    rewards.add(createConfiguredItem(rewardEntry, team, Material.STONE, 1, (short) 0, null, null));
                }
            }
        }

        return rewards.toArray(new ItemStack[rewards.size()]);
    }

    private void fillBackground() {
        ConfigurationSection background = plugin.getConfig().getConfigurationSection("item-shop.background");
        if (background != null && !background.getBoolean("enabled", true)) {
            return;
        }

        ItemStack item = background == null
            ? new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (short) 15).name("&8 ").build()
            : buildItem(background, "STAINED_GLASS_PANE", 15, null);

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, item);
        }
    }

    private ItemStack buildItem(ConfigurationSection section, String defaultMaterial, int defaultData, String[][] placeholders) {
        return buildItemBuilder(section, defaultMaterial, defaultData, placeholders).build();
    }

    private ItemBuilder buildItemBuilder(ConfigurationSection section, String defaultMaterial, int defaultData, String[][] placeholders) {
        Material material = parseMaterial(section.getString("material"), parseMaterial(defaultMaterial, Material.STONE));
        short data = (short) section.getInt("data", defaultData);
        int amount = Math.max(1, section.getInt("amount", 1));
        ItemBuilder builder = new ItemBuilder(material, amount, data)
            .name(replace(section.getString("name", "&fItem"), placeholders));

        java.util.List<String> lore = section.getStringList("lore");
        if (lore != null && !lore.isEmpty()) {
            java.util.List<String> rendered = new java.util.ArrayList<String>();
            for (String line : lore) {
                rendered.add(replace(line, placeholders));
            }
            builder.lore(rendered);
        }

        if (section.getBoolean("glow", false)) {
            builder.glow();
        }
        if (material == Material.SKULL_ITEM && section.isString("skull-owner")) {
            builder.skullOwner(section.getString("skull-owner"));
        }
        return builder;
    }

    private String getCategoryDisplayName(String key) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("item-shop.categories." + key);
        return section == null ? key : section.getString("name", key);
    }

    private ConfigurationSection getToolTierSection(ConfigurationSection section, int tier) {
        if (section == null || tier <= 0) {
            return null;
        }

        ConfigurationSection tiers = section.getConfigurationSection("tiers");
        if (tiers == null) {
            return null;
        }

        ConfigurationSection direct = tiers.getConfigurationSection(String.valueOf(tier));
        if (direct != null) {
            return direct;
        }

        for (String key : tiers.getKeys(false)) {
            try {
                if (Integer.parseInt(key) == tier) {
                    return tiers.getConfigurationSection(key);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private int getCurrentToolTier(Arena arena, Player player, String toolType) {
        if (arena == null || player == null) {
            return 0;
        }

        return "axe".equals(toolType)
            ? arena.getAxeTier(player.getUniqueId())
            : arena.getPickaxeTier(player.getUniqueId());
    }

    private List<Integer> getDisplaySlots() {
        List<Integer> configured = plugin.getConfig().getIntegerList("item-shop.categories." + categoryKey + ".display-slots");
        if (configured == null || configured.isEmpty()) {
            configured = plugin.getConfig().getIntegerList("item-shop.offer-display-slots");
        }
        List<Integer> valid = new ArrayList<Integer>();
        for (Integer slot : configured) {
            if (slot == null || slot.intValue() < 0 || slot.intValue() >= inventory.getSize()) {
                continue;
            }
            valid.add(slot);
        }
        return valid;
    }

    private List<Integer> getQuickAccessSlots() {
        List<Integer> configured = plugin.getConfig().getIntegerList("item-shop.categories.quick-buy.display-slots");
        if (configured == null || configured.isEmpty()) {
            configured = plugin.getConfig().getIntegerList("item-shop.offer-display-slots");
        }

        List<Integer> valid = new ArrayList<Integer>();
        for (Integer slot : configured) {
            if (slot == null || slot.intValue() < 0 || slot.intValue() >= inventory.getSize()) {
                continue;
            }
            valid.add(slot);
        }
        return valid;
    }

    protected Map<String, String> placeholders(String... values) {
        Map<String, String> map = new HashMap<String, String>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(values[i], values[i + 1]);
        }
        return map;
    }

    private short resolveData(ConfigurationSection section, ArenaTeam team) {
        if (section == null) {
            return 0;
        }

        if (section.getBoolean("team-color-data", false) && team != null) {
            return team.getColor().getWoolData();
        }

        return (short) section.getInt("data", 0);
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null || name.trim().isEmpty()) {
            return fallback;
        }

        try {
            return Material.valueOf(name.trim().toUpperCase(java.util.Locale.ENGLISH));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private String replace(String text, String[][] placeholders) {
        if (text == null) {
            return "";
        }

        String result = text;
        if (placeholders != null) {
            for (String[] entry : placeholders) {
                if (entry != null && entry.length >= 2) {
                    result = result.replace(entry[0], entry[1]);
                }
            }
        }
        return result;
    }

    private String replace(String text, String[] placeholders) {
        if (text == null) {
            return "";
        }

        String result = text;
        if (placeholders != null) {
            for (int index = 0; index + 1 < placeholders.length; index += 2) {
                result = result.replace(placeholders[index], placeholders[index + 1]);
            }
        }
        return result;
    }

    private ItemStack createConfiguredItem(ConfigurationSection section, ArenaTeam team, Material fallbackMaterial, int fallbackAmount, short fallbackData, String fallbackName, String[] placeholders) {
        if (section == null) {
            ItemBuilder fallback = new ItemBuilder(fallbackMaterial, fallbackAmount, fallbackData);
            if (fallbackName != null) {
                fallback.name(replace(fallbackName, placeholders));
            }
            return fallback.build();
        }

        Material material = parseMaterial(section.getString("material"), fallbackMaterial);
        int amount = Math.max(1, section.getInt("amount", fallbackAmount));
        short data = resolveData(section, team);
        ItemBuilder builder = new ItemBuilder(material, amount, data);
        if (section.isString("name") || fallbackName != null) {
            builder.name(replace(section.getString("name", fallbackName == null ? "" : fallbackName), placeholders));
        }

        List<String> lore = section.getStringList("lore");
        if (!lore.isEmpty()) {
            List<String> renderedLore = new ArrayList<String>();
            for (String line : lore) {
                renderedLore.add(replace(line, placeholders));
            }
            builder.lore(renderedLore);
        }

        if (material == Material.SKULL_ITEM && section.isString("skull-owner")) {
            builder.skullOwner(section.getString("skull-owner"));
        }
        if (section.getBoolean("glow", false)) {
            builder.glow();
        }

        ItemStack item = builder.build();
        ConfigurationSection enchants = section.getConfigurationSection("enchantments");
        if (enchants != null) {
            for (String enchantName : enchants.getKeys(false)) {
                Enchantment enchantment = Enchantment.getByName(enchantName.toUpperCase(Locale.ENGLISH));
                if (enchantment != null) {
                    item.addUnsafeEnchantment(enchantment, Math.max(1, enchants.getInt(enchantName, 1)));
                }
            }
        }
        return item;
    }

    private String strip(String text) {
        return ChatColor.stripColor(ChatUtil.color(text == null ? "" : text));
    }

    private static final class OfferEntry {

        private final String itemKey;
        private final ConfigurationSection section;
        private final int order;

        private OfferEntry(String itemKey, ConfigurationSection section, int order) {
            this.itemKey = itemKey;
            this.section = section;
            this.order = order;
        }
    }
}

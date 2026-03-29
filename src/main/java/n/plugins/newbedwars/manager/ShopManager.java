package n.plugins.newbedwars.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaTeam;
import n.plugins.newbedwars.arena.TeamColor;
import n.plugins.newbedwars.util.ChatUtil;
import n.plugins.newbedwars.util.CuboidRegion;
import n.plugins.newbedwars.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class ShopManager {

    private final NewBedWars plugin;

    public ShopManager(NewBedWars plugin) {
        this.plugin = plugin;
    }

    public boolean tryBuy(Player player, String itemName, Material currency, int amount, ItemStack... rewards) {
        if (!hasEnough(player, currency, amount)) {
            sendMessage(player, "shops.need-currency", placeholders(
                "amount", String.valueOf(amount),
                "currency", getCurrencyName(currency, amount)
            ));
            SoundUtil.playConfigured(plugin, player, "sound-effects.shop-buy-fail", "VILLAGER_NO", 1.0F, 1.0F);
            return false;
        }

        removeCurrency(player, currency, amount);
        for (ItemStack reward : rewards) {
            giveOrDrop(player, reward);
        }
        sendMessage(player, "shops.buy-success", placeholders(
            "item", cleanName(itemName)
        ));
        SoundUtil.playConfigured(plugin, player, "sound-effects.shop-buy-success", "ORB_PICKUP", 1.0F, 1.2F);
        return true;
    }

    public boolean hasEnough(Player player, Material currency, int amount) {
        return countCurrency(player, currency) >= amount;
    }

    public int countCurrency(Player player, Material currency) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != currency) {
                continue;
            }
            total += item.getAmount();
        }
        return total;
    }

    public void removeCurrency(Player player, Material currency, int amount) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() != currency) {
                continue;
            }

            int remove = Math.min(amount, item.getAmount());
            amount -= remove;
            item.setAmount(item.getAmount() - remove);
            if (item.getAmount() <= 0) {
                inventory.setItem(slot, null);
            } else {
                inventory.setItem(slot, item);
            }

            if (amount <= 0) {
                break;
            }
        }
        player.updateInventory();
    }

    public void giveOrDrop(Player player, ItemStack itemStack) {
        if (itemStack == null) {
            return;
        }

        java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack);
        for (ItemStack remaining : leftover.values()) {
            player.getWorld().dropItem(player.getLocation(), remaining);
        }
        player.updateInventory();
    }

    public void applyRespawnLoadout(Player player, ArenaTeam team) {
        if (player == null || team == null) {
            return;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        applyArmorLoadout(player, arena, team);
        giveStarterSword(player, team);
        applyPermanentTools(player, arena, team);
        applyUpgradeEffects(player, team);
        player.updateInventory();
    }

    public void refreshTeamUpgrades(Arena arena, ArenaTeam team) {
        if (arena == null || team == null) {
            return;
        }

        for (UUID uniqueId : new ArrayList<UUID>(arena.getPlayers())) {
            if (arena.getSpectators().contains(uniqueId)) {
                continue;
            }

            if (plugin.getTeamManager().getColor(arena, uniqueId) != team.getColor()) {
                continue;
            }

            Player member = Bukkit.getPlayer(uniqueId);
            if (member == null || !member.isOnline()) {
                continue;
            }

            refreshInventoryUpgrades(member, team);
        }
    }

    public void tickArenaEffects(Arena arena) {
        if (arena == null) {
            return;
        }

        for (UUID uniqueId : arena.getPlayers()) {
            if (arena.getSpectators().contains(uniqueId)) {
                continue;
            }

            Player player = Bukkit.getPlayer(uniqueId);
            if (player == null || !player.isOnline()) {
                continue;
            }

            ArenaTeam team = plugin.getTeamManager().getTeam(arena, uniqueId);
            if (team == null) {
                continue;
            }

            CuboidRegion islandRegion = arena.getMatchRegion(team.getIslandRegion());
            if (team.hasHealPool() && islandRegion != null && islandRegion.contains(player.getLocation())) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false), true);
            }
        }
    }

    public boolean buySharpness(Player player, Arena arena, ArenaTeam team) {
        return buyConfiguredUpgrade(player, arena, team, "sharpness");
    }

    public boolean buyProtection(Player player, Arena arena, ArenaTeam team) {
        return buyConfiguredUpgrade(player, arena, team, "protection");
    }

    public boolean buyManiacMiner(Player player, Arena arena, ArenaTeam team) {
        return buyConfiguredUpgrade(player, arena, team, "maniac-miner");
    }

    public boolean buyHealPool(Player player, ArenaTeam team) {
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        return buyConfiguredUpgrade(player, arena, team, "heal-pool");
    }

    public boolean buySword(Player player, ArenaTeam team, Material material, Material currency, int amount, String itemName) {
        if (player == null || team == null || material == null) {
            return false;
        }

        Material currentSword = getBestSwordMaterial(player);
        if (getSwordTier(currentSword) >= getSwordTier(material)) {
            sendMessage(player, "shops.sword-already-better");
            SoundUtil.playConfigured(plugin, player, "sound-effects.shop-buy-fail", "VILLAGER_NO", 1.0F, 1.0F);
            return false;
        }

        if (!hasEnough(player, currency, amount)) {
            sendMessage(player, "shops.need-currency", placeholders(
                "amount", String.valueOf(amount),
                "currency", getCurrencyName(currency, amount)
            ));
            SoundUtil.playConfigured(plugin, player, "sound-effects.shop-buy-fail", "VILLAGER_NO", 1.0F, 1.0F);
            return false;
        }

        removeCurrency(player, currency, amount);
        replaceSword(player, material, team.hasSharpenedSwords());
        sendMessage(player, "shops.buy-success", placeholders(
            "item", cleanName(itemName)
        ));
        SoundUtil.playConfigured(plugin, player, "sound-effects.shop-buy-success", "ORB_PICKUP", 1.0F, 1.2F);
        return true;
    }

    public boolean buyArmor(Player player, Arena arena, ArenaTeam team, int tier, Material currency, int amount, String itemName) {
        if (player == null || arena == null || team == null) {
            return false;
        }

        if (arena.getArmorTier(player.getUniqueId()) >= tier) {
            sendMessage(player, "shops.armor-already-better");
            SoundUtil.playConfigured(plugin, player, "sound-effects.shop-buy-fail", "VILLAGER_NO", 1.0F, 1.0F);
            return false;
        }

        if (!hasEnough(player, currency, amount)) {
            sendMessage(player, "shops.need-currency", placeholders(
                "amount", String.valueOf(amount),
                "currency", getCurrencyName(currency, amount)
            ));
            SoundUtil.playConfigured(plugin, player, "sound-effects.shop-buy-fail", "VILLAGER_NO", 1.0F, 1.0F);
            return false;
        }

        removeCurrency(player, currency, amount);
        arena.setArmorTier(player.getUniqueId(), tier);
        applyArmorLoadout(player, arena, team);
        sendMessage(player, "shops.buy-success", placeholders(
            "item", cleanName(itemName)
        ));
        SoundUtil.playConfigured(plugin, player, "sound-effects.shop-buy-success", "ORB_PICKUP", 1.0F, 1.2F);
        return true;
    }

    public boolean buyPickaxeUpgrade(Player player, Arena arena, ArenaTeam team, ConfigurationSection offerSection, String fallbackName) {
        return buyTieredToolUpgrade(player, arena, team, offerSection, fallbackName, "pickaxe");
    }

    public boolean buyAxeUpgrade(Player player, Arena arena, ArenaTeam team, ConfigurationSection offerSection, String fallbackName) {
        return buyTieredToolUpgrade(player, arena, team, offerSection, fallbackName, "axe");
    }

    private boolean buyTieredToolUpgrade(Player player, Arena arena, ArenaTeam team, ConfigurationSection offerSection, String fallbackName, String toolType) {
        if (player == null || arena == null || team == null || offerSection == null) {
            return false;
        }

        int currentTier = getToolTier(arena, player.getUniqueId(), toolType);
        int nextTier = currentTier + 1;
        ConfigurationSection nextTierSection = getToolTierSection(offerSection, nextTier);
        if (nextTierSection == null) {
            sendMessage(player, "axe".equals(toolType) ? "shops.axe-already-maxed" : "shops.pickaxe-already-maxed");
            SoundUtil.playConfigured(plugin, player, "sound-effects.shop-buy-fail", "VILLAGER_NO", 1.0F, 1.0F);
            return false;
        }

        Material currency = parseMaterial(nextTierSection.getString("cost.material"), Material.IRON_INGOT);
        int amount = Math.max(1, nextTierSection.getInt("cost.amount", 1));
        if (!hasEnough(player, currency, amount)) {
            sendMessage(player, "shops.need-currency", placeholders(
                "amount", String.valueOf(amount),
                "currency", getCurrencyName(currency, amount)
            ));
            SoundUtil.playConfigured(plugin, player, "sound-effects.shop-buy-fail", "VILLAGER_NO", 1.0F, 1.0F);
            return false;
        }

        removeCurrency(player, currency, amount);
        setToolTier(arena, player.getUniqueId(), toolType, nextTier);

        ItemStack toolItem = createConfiguredTool(nextTierSection,
            "axe".equals(toolType) ? Material.WOOD_AXE : Material.WOOD_PICKAXE,
            team);
        if ("axe".equals(toolType)) {
            replaceAxe(player, toolItem);
        } else {
            replacePickaxe(player, toolItem);
        }

        sendMessage(player, "shops.buy-success", placeholders(
            "item", cleanName(nextTierSection.getString("name", fallbackName))
        ));
        SoundUtil.playConfigured(plugin, player, "sound-effects.shop-buy-success", "ORB_PICKUP", 1.0F, 1.2F);
        return true;
    }

    public boolean buyConfiguredUpgrade(Player player, Arena arena, ArenaTeam team, String upgradeKey) {
        if (player == null || team == null || upgradeKey == null || upgradeKey.trim().isEmpty()) {
            return false;
        }

        ConfigurationSection section = getUpgradeSection(upgradeKey);
        String normalized = normalizeUpgradeAction(section == null ? upgradeKey : section.getString("action", upgradeKey));
        String upgradeName = cleanName(section == null ? upgradeKey : section.getString("name", upgradeKey));
        int currentLevel = getUpgradeLevel(team, upgradeKey);
        int maxLevel = getUpgradeMaxLevel(upgradeKey);

        if (maxLevel > 0 && currentLevel >= maxLevel) {
            sendMessage(player, maxLevel <= 1 ? "shops.upgrade-already-owned" : "shops.upgrade-maxed", placeholders(
                "upgrade", upgradeName,
                "level", String.valueOf(currentLevel)
            ));
            SoundUtil.playConfigured(plugin, player, "sound-effects.shop-buy-fail", "VILLAGER_NO", 1.0F, 1.0F);
            return false;
        }

        Material currency = getUpgradeCurrency(upgradeKey);
        int cost = getNextUpgradeCost(team, upgradeKey);
        if (cost <= 0) {
            sendMessage(player, "shops.upgrade-maxed", placeholders(
                "upgrade", upgradeName,
                "level", String.valueOf(currentLevel)
            ));
            SoundUtil.playConfigured(plugin, player, "sound-effects.shop-buy-fail", "VILLAGER_NO", 1.0F, 1.0F);
            return false;
        }

        if (!hasEnough(player, currency, cost)) {
            sendMessage(player, "shops.need-currency", placeholders(
                "amount", String.valueOf(cost),
                "currency", getCurrencyName(currency, cost)
            ));
            SoundUtil.playConfigured(plugin, player, "sound-effects.shop-buy-fail", "VILLAGER_NO", 1.0F, 1.0F);
            return false;
        }

        removeCurrency(player, currency, cost);

        if ("sharpness".equals(normalized)) {
            team.setSharpenedSwords(true);
            refreshTeamUpgrades(arena, team);
            sendMessage(player, "shops.upgrade-purchased", placeholders(
                "upgrade", upgradeName,
                "level", "1"
            ));
        } else if ("protection".equals(normalized)) {
            team.setProtectionTier(Math.min(maxLevel, currentLevel + 1));
            refreshTeamUpgrades(arena, team);
            sendMessage(player, "shops.upgrade-tier-purchased", placeholders(
                "upgrade", upgradeName,
                "level", String.valueOf(team.getProtectionTier())
            ));
        } else if ("maniac-miner".equals(normalized)) {
            team.setManiacMinerTier(Math.min(maxLevel, currentLevel + 1));
            refreshTeamUpgrades(arena, team);
            sendMessage(player, "shops.upgrade-tier-purchased", placeholders(
                "upgrade", upgradeName,
                "level", String.valueOf(team.getManiacMinerTier())
            ));
        } else if ("tool-enchant".equals(normalized)) {
            team.setToolEnchantTier(Math.min(maxLevel, currentLevel + 1));
            refreshTeamUpgrades(arena, team);
            sendMessage(player, "shops.upgrade-tier-purchased", placeholders(
                "upgrade", upgradeName,
                "level", String.valueOf(team.getToolEnchantTier())
            ));
        } else if ("heal-pool".equals(normalized)) {
            team.setHealPool(true);
            sendMessage(player, "shops.upgrade-purchased", placeholders(
                "upgrade", upgradeName,
                "level", "1"
            ));
        } else {
            sendMessage(player, "shops.upgrade-maxed", placeholders(
                "upgrade", upgradeName,
                "level", String.valueOf(currentLevel)
            ));
            SoundUtil.playConfigured(plugin, player, "sound-effects.shop-buy-fail", "VILLAGER_NO", 1.0F, 1.0F);
            return false;
        }

        SoundUtil.playConfigured(plugin, player, "sound-effects.shop-buy-success", "ORB_PICKUP", 1.0F, 1.2F);
        return true;
    }

    public int getUpgradeLevel(ArenaTeam team, String upgradeKey) {
        if (team == null) {
            return 0;
        }

        String normalized = resolveUpgradeAction(upgradeKey);
        if ("sharpness".equals(normalized)) {
            return team.hasSharpenedSwords() ? 1 : 0;
        }
        if ("protection".equals(normalized)) {
            return team.getProtectionTier();
        }
        if ("maniac-miner".equals(normalized)) {
            return team.getManiacMinerTier();
        }
        if ("tool-enchant".equals(normalized)) {
            return team.getToolEnchantTier();
        }
        if ("heal-pool".equals(normalized)) {
            return team.hasHealPool() ? 1 : 0;
        }
        return 0;
    }

    public int getUpgradeMaxLevel(String upgradeKey) {
        ConfigurationSection section = getUpgradeSection(upgradeKey);
        if (section == null) {
            return defaultUpgradeMaxLevel(upgradeKey);
        }

        int configured = section.getInt("cost.max-level", -1);
        if (configured > 0) {
            return configured;
        }

        List<Integer> amounts = section.getIntegerList("cost.amounts");
        if (!amounts.isEmpty()) {
            return amounts.size();
        }

        return defaultUpgradeMaxLevel(upgradeKey);
    }

    public Material getUpgradeCurrency(String upgradeKey) {
        ConfigurationSection section = getUpgradeSection(upgradeKey);
        if (section == null) {
            return Material.DIAMOND;
        }

        return parseMaterial(section.getString("cost.material"), Material.DIAMOND);
    }

    public int getNextUpgradeCost(ArenaTeam team, String upgradeKey) {
        ConfigurationSection section = getUpgradeSection(upgradeKey);
        if (section == null) {
            return defaultUpgradeCost(team, upgradeKey);
        }

        int currentLevel = getUpgradeLevel(team, upgradeKey);
        List<Integer> amounts = section.getIntegerList("cost.amounts");
        if (!amounts.isEmpty()) {
            return currentLevel >= amounts.size() ? 0 : Math.max(0, amounts.get(currentLevel));
        }

        int single = section.getInt("cost.amount", -1);
        if (single > 0) {
            return currentLevel >= getUpgradeMaxLevel(upgradeKey) ? 0 : single;
        }

        return defaultUpgradeCost(team, upgradeKey);
    }

    public String getCurrencyName(Material material, int amount) {
        String key = material == null ? "unknown" : material.name().toLowerCase(Locale.ENGLISH);
        String path = "currencies." + key + ".";
        String singular = plugin.getConfig().getString(path + "singular");
        String plural = plugin.getConfig().getString(path + "plural");

        if (amount == 1 && singular != null && !singular.trim().isEmpty()) {
            return singular;
        }
        if (amount != 1 && plural != null && !plural.trim().isEmpty()) {
            return plural;
        }
        if (singular != null && !singular.trim().isEmpty()) {
            return singular;
        }

        if (material == Material.IRON_INGOT) {
            return amount == 1 ? "ferro" : "ferros";
        }
        if (material == Material.GOLD_INGOT) {
            return amount == 1 ? "ouro" : "ouros";
        }
        if (material == Material.DIAMOND) {
            return amount == 1 ? "diamante" : "diamantes";
        }
        if (material == Material.EMERALD) {
            return amount == 1 ? "esmeralda" : "esmeraldas";
        }
        return material == null ? "item" : material.name().toLowerCase(Locale.ENGLISH);
    }

    public String getCurrencyColor(Material material) {
        if (material == null) {
            return "&7";
        }

        String configured = plugin.getConfig().getString("currencies." + material.name().toLowerCase(Locale.ENGLISH) + ".color");
        if (configured != null && !configured.trim().isEmpty()) {
            return configured;
        }

        if (material == Material.IRON_INGOT) {
            return "&f";
        }
        if (material == Material.GOLD_INGOT) {
            return "&6";
        }
        if (material == Material.DIAMOND) {
            return "&b";
        }
        if (material == Material.EMERALD) {
            return "&a";
        }
        return "&7";
    }

    public ItemStack createSword(Material material, boolean sharpness) {
        ItemStack sword = new ItemStack(material, 1);
        if (sharpness) {
            sword.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 1);
        }
        return sword;
    }

    public void send(Player player, String message) {
        player.sendMessage(plugin.getMessageManager().get("prefix") + ChatUtil.color(message));
    }

    public void sendMessage(Player player, String path) {
        player.sendMessage(plugin.getMessageManager().get(path));
    }

    public void sendMessage(Player player, String path, Map<String, String> placeholders) {
        player.sendMessage(plugin.getMessageManager().get(path, placeholders));
    }

    private void refreshInventoryUpgrades(Player player, ArenaTeam team) {
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        applyArmorLoadout(player, arena, team);
        applyProtectionToArmor(player.getInventory().getArmorContents(), team.getProtectionTier());
        ensureSwordPresent(player, team);
        reapplySwordSharpness(player, team.hasSharpenedSwords());
        applyPermanentTools(player, arena, team);
        applyUpgradeEffects(player, team);
        player.updateInventory();
    }

    private void reapplySwordSharpness(Player player, boolean sharpened) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !isSword(item.getType())) {
                continue;
            }

            item.removeEnchantment(Enchantment.DAMAGE_ALL);
            if (sharpened) {
                item.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 1);
            }
        }
    }

    private void applyUpgradeEffects(Player player, ArenaTeam team) {
        player.removePotionEffect(PotionEffectType.FAST_DIGGING);
        if (team.getManiacMinerTier() > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, Integer.MAX_VALUE, team.getManiacMinerTier() - 1, true, false), true);
        }
    }

    private void giveStarterSword(Player player, ArenaTeam team) {
        replaceSword(player, Material.WOOD_SWORD, team.hasSharpenedSwords());
        player.getInventory().setHeldItemSlot(0);
    }

    private void ensureSwordPresent(Player player, ArenaTeam team) {
        if (findSwordSlot(player) != -1) {
            return;
        }

        giveStarterSword(player, team);
    }

    private void replaceSword(Player player, Material material, boolean sharpness) {
        PlayerInventory inventory = player.getInventory();
        int preferredSlot = findSwordSlot(player);
        if (preferredSlot == -1) {
            preferredSlot = 0;
        }

        clearSwords(inventory);
        inventory.setItem(preferredSlot, createSword(material, sharpness));
        player.updateInventory();
    }

    private void applyPermanentTools(Player player, Arena arena, ArenaTeam team) {
        if (player == null || arena == null || team == null) {
            return;
        }

        ConfigurationSection pickaxeOffer = plugin.getConfig().getConfigurationSection("item-shop.items.pickaxe");
        int pickaxeTier = arena.getPickaxeTier(player.getUniqueId());
        if (pickaxeOffer != null && pickaxeTier > 0) {
            ConfigurationSection tierSection = getToolTierSection(pickaxeOffer, pickaxeTier);
            if (tierSection != null) {
                replacePickaxe(player, createConfiguredTool(tierSection, Material.WOOD_PICKAXE, team));
            }
        }

        ConfigurationSection axeOffer = plugin.getConfig().getConfigurationSection("item-shop.items.axe");
        int axeTier = arena.getAxeTier(player.getUniqueId());
        if (axeOffer != null && axeTier > 0) {
            ConfigurationSection tierSection = getToolTierSection(axeOffer, axeTier);
            if (tierSection != null) {
                replaceAxe(player, createConfiguredTool(tierSection, Material.WOOD_AXE, team));
            }
        }

        reapplyToolEnchantments(player, team);
    }

    private void reapplyToolEnchantments(Player player, ArenaTeam team) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || (!isPickaxe(item.getType()) && !isAxe(item.getType()))) {
                continue;
            }

            clearTeamToolEnchantments(item);
            applyTeamToolEnchantments(item, team);
        }
    }

    private void replacePickaxe(Player player, ItemStack pickaxe) {
        if (player == null || pickaxe == null) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        int preferredSlot = findPickaxeSlot(player);
        clearPickaxes(inventory);

        if (preferredSlot != -1) {
            inventory.setItem(preferredSlot, pickaxe);
        } else {
            int freeSlot = findFirstFreeToolSlot(inventory);
            if (freeSlot != -1) {
                inventory.setItem(freeSlot, pickaxe);
            } else {
                giveOrDrop(player, pickaxe);
                return;
            }
        }

        player.updateInventory();
    }

    private void replaceAxe(Player player, ItemStack axe) {
        if (player == null || axe == null) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        int preferredSlot = findAxeSlot(player);
        clearAxes(inventory);

        if (preferredSlot != -1) {
            inventory.setItem(preferredSlot, axe);
        } else {
            int freeSlot = findFirstFreeToolSlot(inventory);
            if (freeSlot != -1) {
                inventory.setItem(freeSlot, axe);
            } else {
                giveOrDrop(player, axe);
                return;
            }
        }

        player.updateInventory();
    }

    private void clearPickaxes(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || !isPickaxe(item.getType())) {
                continue;
            }
            inventory.setItem(slot, null);
        }
    }

    private void clearAxes(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || !isAxe(item.getType())) {
                continue;
            }
            inventory.setItem(slot, null);
        }
    }

    private int findPickaxeSlot(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && isPickaxe(item.getType())) {
                return slot;
            }
        }
        return -1;
    }

    private int findAxeSlot(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && isAxe(item.getType())) {
                return slot;
            }
        }
        return -1;
    }

    private int findFirstFreeToolSlot(PlayerInventory inventory) {
        for (int slot = 2; slot <= 8; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                return slot;
            }
        }

        for (int slot = 9; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                return slot;
            }
        }
        return -1;
    }

    private ItemStack createConfiguredTool(ConfigurationSection tierSection, Material fallbackMaterial, ArenaTeam team) {
        ConfigurationSection reward = tierSection == null ? null : tierSection.getConfigurationSection("reward");
        ConfigurationSection source = reward == null ? tierSection : reward;
        if (source == null) {
            return new ItemStack(fallbackMaterial, 1);
        }

        Material material = parseMaterial(source.getString("material"), fallbackMaterial);
        int amount = Math.max(1, source.getInt("amount", 1));
        short data = (short) source.getInt("data", 0);
        ItemStack item = new ItemStack(material, amount, data);
        applyConfiguredEnchantments(item, source.getConfigurationSection("enchantments"));
        applyTeamToolEnchantments(item, team);
        return item;
    }

    private void applyConfiguredEnchantments(ItemStack item, ConfigurationSection section) {
        if (item == null || section == null) {
            return;
        }

        for (String enchantName : section.getKeys(false)) {
            Enchantment enchantment = Enchantment.getByName(enchantName.toUpperCase(Locale.ENGLISH));
            if (enchantment == null) {
                continue;
            }

            item.addUnsafeEnchantment(enchantment, Math.max(1, section.getInt(enchantName, 1)));
        }
    }

    private void clearTeamToolEnchantments(ItemStack item) {
        if (item == null) {
            return;
        }

        ConfigurationSection section = getUpgradeSection("tool-enchant");
        ConfigurationSection enchantments = section == null ? null : section.getConfigurationSection("enchantments");
        if (enchantments != null && !enchantments.getKeys(false).isEmpty()) {
            for (String enchantName : enchantments.getKeys(false)) {
                Enchantment enchantment = Enchantment.getByName(enchantName.toUpperCase(Locale.ENGLISH));
                if (enchantment != null) {
                    item.removeEnchantment(enchantment);
                }
            }
            return;
        }

        item.removeEnchantment(Enchantment.DIG_SPEED);
    }

    private void applyTeamToolEnchantments(ItemStack item, ArenaTeam team) {
        if (item == null || team == null || (!isPickaxe(item.getType()) && !isAxe(item.getType()))) {
            return;
        }

        int tier = team.getToolEnchantTier();
        if (tier <= 0) {
            return;
        }

        ConfigurationSection section = getUpgradeSection("tool-enchant");
        ConfigurationSection enchantments = section == null ? null : section.getConfigurationSection("enchantments");
        if (enchantments != null && !enchantments.getKeys(false).isEmpty()) {
            for (String enchantName : enchantments.getKeys(false)) {
                Enchantment enchantment = Enchantment.getByName(enchantName.toUpperCase(Locale.ENGLISH));
                if (enchantment == null) {
                    continue;
                }

                List<Integer> levels = enchantments.getIntegerList(enchantName);
                int configuredLevel = levels.isEmpty()
                    ? enchantments.getInt(enchantName, tier)
                    : levels.get(Math.min(levels.size() - 1, Math.max(0, tier - 1)));
                if (configuredLevel > 0) {
                    item.addUnsafeEnchantment(enchantment, configuredLevel);
                }
            }
            return;
        }

        item.addUnsafeEnchantment(Enchantment.DIG_SPEED, tier);
    }

    private void clearSwords(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || !isSword(item.getType())) {
                continue;
            }
            inventory.setItem(slot, null);
        }
    }

    private int findSwordSlot(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && isSword(item.getType())) {
                return slot;
            }
        }
        return -1;
    }

    private Material getBestSwordMaterial(Player player) {
        Material best = null;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !isSword(item.getType())) {
                continue;
            }

            if (best == null || getSwordTier(item.getType()) > getSwordTier(best)) {
                best = item.getType();
            }
        }
        return best == null ? Material.WOOD_SWORD : best;
    }

    private int getSwordTier(Material material) {
        if (material == Material.DIAMOND_SWORD) {
            return 4;
        }
        if (material == Material.IRON_SWORD) {
            return 3;
        }
        if (material == Material.STONE_SWORD) {
            return 2;
        }
        if (material == Material.WOOD_SWORD || material == Material.GOLD_SWORD) {
            return 1;
        }
        return 0;
    }

    private void applyArmorLoadout(Player player, Arena arena, ArenaTeam team) {
        int armorTier = arena == null ? 0 : arena.getArmorTier(player.getUniqueId());
        ItemStack[] armor = createTeamArmor(team, armorTier);
        applyProtectionToArmor(armor, team.getProtectionTier());
        player.getInventory().setArmorContents(armor);
    }

    private ItemStack[] createTeamArmor(ArenaTeam team, int armorTier) {
        ItemStack helmet = colorArmor(new ItemStack(Material.LEATHER_HELMET), team.getColor());
        ItemStack chest = colorArmor(new ItemStack(Material.LEATHER_CHESTPLATE), team.getColor());
        ItemStack legs = createArmorLeggings(team, armorTier);
        ItemStack boots = createArmorBoots(team, armorTier);

        return new ItemStack[] {boots, legs, chest, helmet};
    }

    private ItemStack createArmorLeggings(ArenaTeam team, int armorTier) {
        if (armorTier >= 3) {
            return new ItemStack(Material.DIAMOND_LEGGINGS);
        }
        if (armorTier >= 2) {
            return new ItemStack(Material.IRON_LEGGINGS);
        }
        if (armorTier >= 1) {
            return new ItemStack(Material.CHAINMAIL_LEGGINGS);
        }
        return colorArmor(new ItemStack(Material.LEATHER_LEGGINGS), team.getColor());
    }

    private ItemStack createArmorBoots(ArenaTeam team, int armorTier) {
        if (armorTier >= 3) {
            return new ItemStack(Material.DIAMOND_BOOTS);
        }
        if (armorTier >= 2) {
            return new ItemStack(Material.IRON_BOOTS);
        }
        if (armorTier >= 1) {
            return new ItemStack(Material.CHAINMAIL_BOOTS);
        }
        return colorArmor(new ItemStack(Material.LEATHER_BOOTS), team.getColor());
    }

    private void applyProtectionToArmor(ItemStack[] armor, int level) {
        if (armor == null) {
            return;
        }

        for (ItemStack piece : armor) {
            if (piece == null) {
                continue;
            }

            piece.removeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL);
            if (level > 0) {
                piece.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, level);
            }
        }
    }

    private ItemStack colorArmor(ItemStack item, TeamColor color) {
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(toBukkitColor(color));
        item.setItemMeta(meta);
        return item;
    }

    private Color toBukkitColor(TeamColor color) {
        if (color == TeamColor.RED) {
            return Color.RED;
        }
        if (color == TeamColor.BLUE) {
            return Color.BLUE;
        }
        if (color == TeamColor.GREEN) {
            return Color.GREEN;
        }
        if (color == TeamColor.YELLOW) {
            return Color.YELLOW;
        }
        if (color == TeamColor.CYAN) {
            return Color.AQUA;
        }
        if (color == TeamColor.PINK) {
            return Color.FUCHSIA;
        }
        if (color == TeamColor.GRAY) {
            return Color.GRAY;
        }
        return Color.WHITE;
    }

    private boolean isSword(Material material) {
        return material == Material.WOOD_SWORD
            || material == Material.STONE_SWORD
            || material == Material.IRON_SWORD
            || material == Material.DIAMOND_SWORD
            || material == Material.GOLD_SWORD;
    }

    private boolean isPickaxe(Material material) {
        return material == Material.WOOD_PICKAXE
            || material == Material.STONE_PICKAXE
            || material == Material.GOLD_PICKAXE
            || material == Material.IRON_PICKAXE
            || material == Material.DIAMOND_PICKAXE;
    }

    private boolean isAxe(Material material) {
        return material == Material.WOOD_AXE
            || material == Material.STONE_AXE
            || material == Material.GOLD_AXE
            || material == Material.IRON_AXE
            || material == Material.DIAMOND_AXE;
    }

    private int getToolTier(Arena arena, UUID uniqueId, String toolType) {
        if (arena == null || uniqueId == null) {
            return 0;
        }

        return "axe".equals(toolType) ? arena.getAxeTier(uniqueId) : arena.getPickaxeTier(uniqueId);
    }

    private void setToolTier(Arena arena, UUID uniqueId, String toolType, int tier) {
        if (arena == null || uniqueId == null) {
            return;
        }

        if ("axe".equals(toolType)) {
            arena.setAxeTier(uniqueId, tier);
            return;
        }

        arena.setPickaxeTier(uniqueId, tier);
    }

    private ConfigurationSection getToolTierSection(ConfigurationSection offerSection, int tier) {
        if (offerSection == null || tier <= 0) {
            return null;
        }

        ConfigurationSection tiers = offerSection.getConfigurationSection("tiers");
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

    private ConfigurationSection getUpgradeSection(String upgradeKey) {
        if (upgradeKey == null || upgradeKey.trim().isEmpty()) {
            return null;
        }

        ConfigurationSection upgrades = plugin.getConfig().getConfigurationSection("upgrade-shop.upgrades");
        if (upgrades == null) {
            return null;
        }

        ConfigurationSection direct = upgrades.getConfigurationSection(upgradeKey);
        if (direct != null) {
            return direct;
        }

        String normalized = normalizeUpgradeAction(upgradeKey);
        for (String key : upgrades.getKeys(false)) {
            ConfigurationSection section = upgrades.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            if (normalized.equals(normalizeUpgradeAction(key)) || normalized.equals(normalizeUpgradeAction(section.getString("action", key)))) {
                return section;
            }
        }
        return null;
    }

    private String normalizeUpgradeAction(String action) {
        if (action == null) {
            return "";
        }

        String normalized = action.trim().toLowerCase(Locale.ENGLISH).replace('_', '-').replace(' ', '-');
        if ("maniacminer".equals(normalized)) {
            return "maniac-miner";
        }
        if ("healpool".equals(normalized)) {
            return "heal-pool";
        }
        return normalized;
    }

    private String resolveUpgradeAction(String upgradeKey) {
        ConfigurationSection section = getUpgradeSection(upgradeKey);
        return normalizeUpgradeAction(section == null ? upgradeKey : section.getString("action", upgradeKey));
    }

    private int defaultUpgradeMaxLevel(String upgradeKey) {
        String normalized = resolveUpgradeAction(upgradeKey);
        if ("sharpness".equals(normalized) || "heal-pool".equals(normalized)) {
            return 1;
        }
        if ("protection".equals(normalized)) {
            return 4;
        }
        if ("maniac-miner".equals(normalized)) {
            return 2;
        }
        if ("tool-enchant".equals(normalized)) {
            return 3;
        }
        return 1;
    }

    private int defaultUpgradeCost(ArenaTeam team, String upgradeKey) {
        String normalized = resolveUpgradeAction(upgradeKey);
        int currentLevel = getUpgradeLevel(team, upgradeKey);

        if ("sharpness".equals(normalized)) {
            return currentLevel >= 1 ? 0 : 4;
        }
        if ("protection".equals(normalized)) {
            int[] costs = new int[] {2, 4, 8, 16};
            return currentLevel >= costs.length ? 0 : costs[currentLevel];
        }
        if ("maniac-miner".equals(normalized)) {
            int[] costs = new int[] {2, 4};
            return currentLevel >= costs.length ? 0 : costs[currentLevel];
        }
        if ("tool-enchant".equals(normalized)) {
            int[] costs = new int[] {2, 4, 6};
            return currentLevel >= costs.length ? 0 : costs[currentLevel];
        }
        if ("heal-pool".equals(normalized)) {
            return currentLevel >= 1 ? 0 : 1;
        }
        return 0;
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

    private String cleanName(String text) {
        return org.bukkit.ChatColor.stripColor(ChatUtil.color(text == null ? "" : text));
    }

    private Map<String, String> placeholders(String... values) {
        Map<String, String> placeholders = new HashMap<String, String>();
        if (values == null) {
            return placeholders;
        }

        for (int index = 0; index + 1 < values.length; index += 2) {
            placeholders.put(values[index], values[index + 1]);
        }
        return placeholders;
    }
}

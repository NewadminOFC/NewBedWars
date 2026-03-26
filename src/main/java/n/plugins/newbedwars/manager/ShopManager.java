package n.plugins.newbedwars.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaTeam;
import n.plugins.newbedwars.arena.TeamColor;
import n.plugins.newbedwars.util.ChatUtil;
import n.plugins.newbedwars.util.CuboidRegion;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
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
            send(player, "&cVoce precisa de &f" + amount + " " + getCurrencyName(currency) + "&c.");
            return false;
        }

        removeCurrency(player, currency, amount);
        for (ItemStack reward : rewards) {
            giveOrDrop(player, reward);
        }
        send(player, "&aVoce comprou &f" + itemName + "&a.");
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

        giveStarterSword(player, team);
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
        if (team.hasSharpenedSwords()) {
            send(player, "&cSeu time ja possui Espadas Afiadas.");
            return false;
        }
        if (!hasEnough(player, Material.DIAMOND, 4)) {
            send(player, "&cVoce precisa de &f4 diamantes&c.");
            return false;
        }

        removeCurrency(player, Material.DIAMOND, 4);
        team.setSharpenedSwords(true);
        refreshTeamUpgrades(arena, team);
        send(player, "&aSeu time comprou &fEspadas Afiadas&a.");
        return true;
    }

    public boolean buyProtection(Player player, Arena arena, ArenaTeam team) {
        int current = team.getProtectionTier();
        if (current >= 4) {
            send(player, "&cSua armadura ja esta no maximo.");
            return false;
        }

        int[] costs = new int[] {2, 4, 8, 16};
        int cost = costs[current];
        if (!hasEnough(player, Material.DIAMOND, cost)) {
            send(player, "&cVoce precisa de &f" + cost + " diamantes&c.");
            return false;
        }

        removeCurrency(player, Material.DIAMOND, cost);
        team.setProtectionTier(current + 1);
        refreshTeamUpgrades(arena, team);
        send(player, "&aSeu time melhorou &fProtecao &apara nivel &f" + team.getProtectionTier() + "&a.");
        return true;
    }

    public boolean buyManiacMiner(Player player, Arena arena, ArenaTeam team) {
        int current = team.getManiacMinerTier();
        if (current >= 2) {
            send(player, "&cSeu time ja esta no maximo de Minerador Maniaco.");
            return false;
        }

        int[] costs = new int[] {2, 4};
        int cost = costs[current];
        if (!hasEnough(player, Material.DIAMOND, cost)) {
            send(player, "&cVoce precisa de &f" + cost + " diamantes&c.");
            return false;
        }

        removeCurrency(player, Material.DIAMOND, cost);
        team.setManiacMinerTier(current + 1);
        refreshTeamUpgrades(arena, team);
        send(player, "&aSeu time comprou &fMinerador Maniaco " + team.getManiacMinerTier() + "&a.");
        return true;
    }

    public boolean buyHealPool(Player player, ArenaTeam team) {
        if (team.hasHealPool()) {
            send(player, "&cSeu time ja possui Piscina de Cura.");
            return false;
        }
        if (!hasEnough(player, Material.DIAMOND, 1)) {
            send(player, "&cVoce precisa de &f1 diamante&c.");
            return false;
        }

        removeCurrency(player, Material.DIAMOND, 1);
        team.setHealPool(true);
        send(player, "&aSeu time comprou &fPiscina de Cura&a.");
        return true;
    }

    public boolean buySword(Player player, ArenaTeam team, Material material, Material currency, int amount, String itemName) {
        if (player == null || team == null || material == null) {
            return false;
        }

        Material currentSword = getBestSwordMaterial(player);
        if (getSwordTier(currentSword) >= getSwordTier(material)) {
            send(player, "&cVoce ja possui essa espada ou uma melhor.");
            return false;
        }

        if (!hasEnough(player, currency, amount)) {
            send(player, "&cVoce precisa de &f" + amount + " " + getCurrencyName(currency) + "&c.");
            return false;
        }

        removeCurrency(player, currency, amount);
        replaceSword(player, material, team.hasSharpenedSwords());
        send(player, "&aVoce comprou &f" + itemName + "&a.");
        return true;
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

    private void refreshInventoryUpgrades(Player player, ArenaTeam team) {
        applyProtectionToArmor(player.getInventory().getArmorContents(), team.getProtectionTier());
        ensureSwordPresent(player, team);
        reapplySwordSharpness(player, team.hasSharpenedSwords());
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

    private ItemStack[] createTeamArmor(ArenaTeam team) {
        ItemStack helmet = colorArmor(new ItemStack(Material.LEATHER_HELMET), team.getColor());
        ItemStack chest = colorArmor(new ItemStack(Material.LEATHER_CHESTPLATE), team.getColor());
        ItemStack legs = colorArmor(new ItemStack(Material.LEATHER_LEGGINGS), team.getColor());
        ItemStack boots = colorArmor(new ItemStack(Material.LEATHER_BOOTS), team.getColor());

        ItemStack[] armor = new ItemStack[] {boots, legs, chest, helmet};
        applyProtectionToArmor(armor, team.getProtectionTier());
        return armor;
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

    private String getCurrencyName(Material material) {
        if (material == Material.IRON_INGOT) {
            return "ferros";
        }
        if (material == Material.GOLD_INGOT) {
            return "ouros";
        }
        if (material == Material.DIAMOND) {
            return "diamantes";
        }
        if (material == Material.EMERALD) {
            return "esmeraldas";
        }
        return material.name().toLowerCase();
    }
}

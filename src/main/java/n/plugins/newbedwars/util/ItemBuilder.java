package n.plugins.newbedwars.util;

import java.util.Arrays;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public class ItemBuilder {

    private final ItemStack itemStack;

    public ItemBuilder(Material material) {
        this(material, 1, (short) 0);
    }

    public ItemBuilder(Material material, int amount, short data) {
        this.itemStack = new ItemStack(material, amount, data);
    }

    public ItemBuilder name(String name) {
        ItemMeta meta = itemStack.getItemMeta();
        meta.setDisplayName(ChatUtil.color(name));
        itemStack.setItemMeta(meta);
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(Arrays.asList(lines));
    }

    public ItemBuilder lore(List<String> lines) {
        ItemMeta meta = itemStack.getItemMeta();
        meta.setLore(ChatUtil.color(lines));
        itemStack.setItemMeta(meta);
        return this;
    }

    public ItemBuilder glow() {
        ItemMeta meta = itemStack.getItemMeta();
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        itemStack.setItemMeta(meta);
        return this;
    }

    public ItemBuilder skullOwner(String owner) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta instanceof SkullMeta && owner != null && !owner.trim().isEmpty()) {
            ((SkullMeta) meta).setOwner(owner);
            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemStack build() {
        return itemStack.clone();
    }
}

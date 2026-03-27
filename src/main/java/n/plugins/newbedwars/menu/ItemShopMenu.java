package n.plugins.newbedwars.menu;

import java.util.HashMap;
import java.util.Map;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaTeam;
import n.plugins.newbedwars.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public class ItemShopMenu extends BaseMenu {

    private final Map<Integer, Offer> offers;

    public ItemShopMenu(NewBedWars plugin) {
        super(plugin);
        this.offers = new HashMap<Integer, Offer>();
    }

    @Override
    protected String getTitle() {
        return plugin.getMessageManager().get("shops.items-title");
    }

    @Override
    protected int getSize() {
        return 54;
    }

    @Override
    protected void draw(Player player) {
        offers.clear();
        fillBackground();

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        ArenaTeam team = arena == null ? null : plugin.getTeamManager().getTeam(arena, player.getUniqueId());

        addOffer(player, team, 10, new Offer(Material.WOOL, team == null ? (short) 0 : team.getColor().getWoolData(), "&fLa", 4, Material.IRON_INGOT, "16 blocos", new RewardFactory() {
            @Override
            public ItemStack[] create(Player target, ArenaTeam targetTeam) {
                short data = targetTeam == null ? 0 : targetTeam.getColor().getWoolData();
                return new ItemStack[] {new ItemStack(Material.WOOL, 16, data)};
            }
        }));
        addOffer(player, team, 11, new Offer(Material.STAINED_CLAY, team == null ? (short) 0 : team.getColor().getWoolData(), "&6Argila Endurecida", 12, Material.IRON_INGOT, "16 blocos", new RewardFactory() {
            @Override
            public ItemStack[] create(Player target, ArenaTeam targetTeam) {
                short data = targetTeam == null ? 0 : targetTeam.getColor().getWoolData();
                return new ItemStack[] {new ItemStack(Material.STAINED_CLAY, 16, data)};
            }
        }));
        addOffer(player, team, 12, new Offer(Material.STAINED_GLASS, team == null ? (short) 0 : team.getColor().getWoolData(), "&bVidro Antiexplosao", 12, Material.IRON_INGOT, "8 blocos", new RewardFactory() {
            @Override
            public ItemStack[] create(Player target, ArenaTeam targetTeam) {
                short data = targetTeam == null ? 0 : targetTeam.getColor().getWoolData();
                return new ItemStack[] {new ItemStack(Material.STAINED_GLASS, 8, data)};
            }
        }));
        addOffer(player, team, 13, new Offer(Material.ENDER_STONE, (short) 0, "&eEnd Stone", 24, Material.IRON_INGOT, "12 blocos", simpleReward(new ItemStack(Material.ENDER_STONE, 12))));
        addOffer(player, team, 14, new Offer(Material.WOOD, (short) 0, "&6Madeira", 4, Material.GOLD_INGOT, "16 blocos", simpleReward(new ItemStack(Material.WOOD, 16))));
        addOffer(player, team, 15, new Offer(Material.LADDER, (short) 0, "&eEscada", 4, Material.IRON_INGOT, "8 escadas", simpleReward(new ItemStack(Material.LADDER, 8))));
        addOffer(player, team, 16, new Offer(Material.OBSIDIAN, (short) 0, "&5Obsidiana", 4, Material.EMERALD, "4 blocos", simpleReward(new ItemStack(Material.OBSIDIAN, 4))));

        addOffer(player, team, 19, new Offer(Material.STONE_SWORD, (short) 0, "&7Espada de Pedra", 10, Material.IRON_INGOT, "Dano melhorado", swordReward(Material.STONE_SWORD)));
        addOffer(player, team, 20, new Offer(Material.IRON_SWORD, (short) 0, "&fEspada de Ferro", 7, Material.GOLD_INGOT, "Espada forte", swordReward(Material.IRON_SWORD)));
        addOffer(player, team, 21, new Offer(Material.DIAMOND_SWORD, (short) 0, "&bEspada de Diamante", 4, Material.EMERALD, "Espada muito forte", swordReward(Material.DIAMOND_SWORD)));
        addOffer(player, team, 22, new Offer(Material.BOW, (short) 0, "&6Arco", 12, Material.GOLD_INGOT, "Ataque a distancia", simpleReward(new ItemStack(Material.BOW, 1))));
        addOffer(player, team, 23, new Offer(Material.ARROW, (short) 0, "&fFlechas", 2, Material.GOLD_INGOT, "8 flechas", simpleReward(new ItemStack(Material.ARROW, 8))));
        addOffer(player, team, 24, new Offer(Material.GOLDEN_APPLE, (short) 0, "&6Maca Dourada", 3, Material.GOLD_INGOT, "Cura rapida", simpleReward(new ItemStack(Material.GOLDEN_APPLE, 1))));
        addOffer(player, team, 25, new Offer(Material.TNT, (short) 0, "&cTNT", 4, Material.GOLD_INGOT, "Explosivo", simpleReward(new ItemStack(Material.TNT, 1))));

        addOffer(player, team, 28, new Offer(Material.FIREBALL, (short) 0, "&cBola de Fogo", 40, Material.IRON_INGOT, "Empurra inimigos", simpleReward(new ItemStack(Material.FIREBALL, 1))));
        addOffer(player, team, 29, new Offer(Material.ENDER_PEARL, (short) 0, "&aPerola do Fim", 4, Material.EMERALD, "Teleporte rapido", simpleReward(new ItemStack(Material.ENDER_PEARL, 1))));
        addOffer(player, team, 30, new Offer(Material.SHEARS, (short) 0, "&fTesoura", 20, Material.IRON_INGOT, "Boa para la", simpleReward(new ItemStack(Material.SHEARS, 1))));
        addOffer(player, team, 31, new Offer(Material.STONE_PICKAXE, (short) 0, "&7Picareta de Pedra", 10, Material.IRON_INGOT, "Ferramenta", simpleReward(new ItemStack(Material.STONE_PICKAXE, 1))));
        addOffer(player, team, 32, new Offer(Material.IRON_PICKAXE, (short) 0, "&fPicareta de Ferro", 4, Material.GOLD_INGOT, "Ferramenta melhor", simpleReward(new ItemStack(Material.IRON_PICKAXE, 1))));
        addOffer(player, team, 33, new Offer(Material.STONE_AXE, (short) 0, "&7Machado de Pedra", 10, Material.IRON_INGOT, "Ferramenta", simpleReward(new ItemStack(Material.STONE_AXE, 1))));
        addOffer(player, team, 34, new Offer(Material.IRON_AXE, (short) 0, "&fMachado de Ferro", 4, Material.GOLD_INGOT, "Ferramenta melhor", simpleReward(new ItemStack(Material.IRON_AXE, 1))));
        addOffer(player, team, 37, new Offer(Material.CHAINMAIL_BOOTS, (short) 0, "&fArmadura de Malha", 40, Material.IRON_INGOT, "Melhora bota e calca", simpleReward(new ItemStack(Material.CHAINMAIL_BOOTS, 1))));
        addOffer(player, team, 38, new Offer(Material.IRON_BOOTS, (short) 0, "&7Armadura de Ferro", 12, Material.GOLD_INGOT, "Melhora bota e calca", simpleReward(new ItemStack(Material.IRON_BOOTS, 1))));
        addOffer(player, team, 39, new Offer(Material.DIAMOND_BOOTS, (short) 0, "&bArmadura de Diamante", 6, Material.EMERALD, "Melhora bota e calca", simpleReward(new ItemStack(Material.DIAMOND_BOOTS, 1))));

        inventory.setItem(49, new ItemBuilder(Material.BARRIER).name("&cFechar").build());
    }

    @Override
    public void handleClick(Player player, int slot, ClickType clickType) {
        if (slot == 49) {
            player.closeInventory();
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

        if (slot == 19) {
            plugin.getShopManager().buySword(player, team, Material.STONE_SWORD, Material.IRON_INGOT, 10, ChatColorUtil.strip(offer.name));
            return;
        }
        if (slot == 20) {
            plugin.getShopManager().buySword(player, team, Material.IRON_SWORD, Material.GOLD_INGOT, 7, ChatColorUtil.strip(offer.name));
            return;
        }
        if (slot == 21) {
            plugin.getShopManager().buySword(player, team, Material.DIAMOND_SWORD, Material.EMERALD, 4, ChatColorUtil.strip(offer.name));
            return;
        }
        if (slot == 37) {
            plugin.getShopManager().buyArmor(player, arena, team, 1, Material.IRON_INGOT, 40, ChatColorUtil.strip(offer.name));
            return;
        }
        if (slot == 38) {
            plugin.getShopManager().buyArmor(player, arena, team, 2, Material.GOLD_INGOT, 12, ChatColorUtil.strip(offer.name));
            return;
        }
        if (slot == 39) {
            plugin.getShopManager().buyArmor(player, arena, team, 3, Material.EMERALD, 6, ChatColorUtil.strip(offer.name));
            return;
        }

        ItemStack[] rewards = offer.rewardFactory.create(player, team);
        plugin.getShopManager().tryBuy(player, ChatColorUtil.strip(offer.name), offer.costType, offer.costAmount, rewards);
    }

    private void fillBackground() {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (short) 15).name("&8 ").build());
        }
    }

    private void addOffer(Player player, ArenaTeam team, int slot, Offer offer) {
        offers.put(slot, offer);
        inventory.setItem(slot, new ItemBuilder(offer.icon, 1, offer.data)
            .name(offer.name)
            .lore(
                "&7" + offer.description,
                "",
                "&7Custo: " + currencyColor(offer.costType) + offer.costAmount + " " + currencyName(offer.costType),
                "",
                "&eClique para comprar"
            ).build());
    }

    private String currencyColor(Material material) {
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

    private String currencyName(Material material) {
        if (material == Material.IRON_INGOT) {
            return "ferro";
        }
        if (material == Material.GOLD_INGOT) {
            return "ouro";
        }
        if (material == Material.DIAMOND) {
            return "diamante";
        }
        if (material == Material.EMERALD) {
            return "esmeralda";
        }
        return material.name().toLowerCase();
    }

    private RewardFactory simpleReward(final ItemStack item) {
        return new RewardFactory() {
            @Override
            public ItemStack[] create(Player target, ArenaTeam targetTeam) {
                return new ItemStack[] {item.clone()};
            }
        };
    }

    private RewardFactory swordReward(final Material material) {
        return new RewardFactory() {
            @Override
            public ItemStack[] create(Player target, ArenaTeam targetTeam) {
                return new ItemStack[] {plugin.getShopManager().createSword(material, targetTeam.hasSharpenedSwords())};
            }
        };
    }

    private interface RewardFactory {
        ItemStack[] create(Player target, ArenaTeam targetTeam);
    }

    private static final class Offer {
        private final Material icon;
        private final short data;
        private final String name;
        private final int costAmount;
        private final Material costType;
        private final String description;
        private final RewardFactory rewardFactory;

        private Offer(Material icon, short data, String name, int costAmount, Material costType, String description, RewardFactory rewardFactory) {
            this.icon = icon;
            this.data = data;
            this.name = name;
            this.costAmount = costAmount;
            this.costType = costType;
            this.description = description;
            this.rewardFactory = rewardFactory;
        }
    }

    private static final class ChatColorUtil {
        private static String strip(String text) {
            return text == null ? "" : text.replaceAll("(?i)\u00A7[0-9A-FK-OR]", "");
        }
    }
}

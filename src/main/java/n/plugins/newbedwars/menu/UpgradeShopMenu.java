package n.plugins.newbedwars.menu;

import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaTeam;
import n.plugins.newbedwars.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public class UpgradeShopMenu extends BaseMenu {

    public UpgradeShopMenu(NewBedWars plugin) {
        super(plugin);
    }

    @Override
    protected String getTitle() {
        return plugin.getMessageManager().get("shops.upgrades-title");
    }

    @Override
    protected int getSize() {
        return 27;
    }

    @Override
    protected void draw(Player player) {
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        ArenaTeam team = arena == null ? null : plugin.getTeamManager().getTeam(arena, player.getUniqueId());

        fillBackground();
        inventory.setItem(10, new ItemBuilder(Material.IRON_SWORD)
            .name("&cEspadas Afiadas")
            .lore(
                "&74 diamantes",
                "&7Dano +1 para espadas",
                "",
                team != null && team.hasSharpenedSwords() ? "&aComprado" : "&eClique para comprar"
            ).build());
        inventory.setItem(12, new ItemBuilder(Material.IRON_CHESTPLATE)
            .name("&bProtecao")
            .lore(
                "&7Nivel atual: &f" + (team == null ? 0 : team.getProtectionTier()),
                "&7Proximo custo: &b" + nextProtectionCost(team) + " diamantes",
                "",
                team != null && team.getProtectionTier() >= 4 ? "&aMaximo" : "&eClique para melhorar"
            ).build());
        inventory.setItem(14, new ItemBuilder(Material.DIAMOND_PICKAXE)
            .name("&eMinerador Maniaco")
            .lore(
                "&7Nivel atual: &f" + (team == null ? 0 : team.getManiacMinerTier()),
                "&7Proximo custo: &b" + nextMinerCost(team) + " diamantes",
                "",
                team != null && team.getManiacMinerTier() >= 2 ? "&aMaximo" : "&eClique para melhorar"
            ).build());
        inventory.setItem(16, new ItemBuilder(Material.BEACON)
            .name("&aPiscina de Cura")
            .lore(
                "&71 diamante",
                "&7Regeneracao na ilha do time",
                "",
                team != null && team.hasHealPool() ? "&aComprado" : "&eClique para comprar"
            ).build());

        inventory.setItem(22, new ItemBuilder(Material.BARRIER).name("&cFechar").build());
    }

    @Override
    public void handleClick(Player player, int slot, ClickType clickType) {
        if (slot == 22) {
            player.closeInventory();
            return;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        ArenaTeam team = arena == null ? null : plugin.getTeamManager().getTeam(arena, player.getUniqueId());
        if (arena == null || team == null) {
            player.closeInventory();
            return;
        }

        if (slot == 10) {
            plugin.getShopManager().buySharpness(player, arena, team);
            open(player);
            return;
        }
        if (slot == 12) {
            plugin.getShopManager().buyProtection(player, arena, team);
            open(player);
            return;
        }
        if (slot == 14) {
            plugin.getShopManager().buyManiacMiner(player, arena, team);
            open(player);
            return;
        }
        if (slot == 16) {
            plugin.getShopManager().buyHealPool(player, team);
            open(player);
        }
    }

    private void fillBackground() {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (short) 15).name("&8 ").build());
        }
    }

    private int nextProtectionCost(ArenaTeam team) {
        if (team == null || team.getProtectionTier() >= 4) {
            return 0;
        }

        int[] costs = new int[] {2, 4, 8, 16};
        return costs[team.getProtectionTier()];
    }

    private int nextMinerCost(ArenaTeam team) {
        if (team == null || team.getManiacMinerTier() >= 2) {
            return 0;
        }

        int[] costs = new int[] {2, 4};
        return costs[team.getManiacMinerTier()];
    }
}

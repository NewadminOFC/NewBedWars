package n.plugins.newbedwars.model;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PlayerSnapshot {

    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack[] enderChestContents;
    private final double health;
    private final int food;
    private final float experience;
    private final int level;
    private final Location location;
    private final GameMode gameMode;
    private final boolean allowFlight;
    private final boolean flying;

    private PlayerSnapshot(ItemStack[] contents, ItemStack[] armor, ItemStack[] enderChestContents, double health, int food, float experience, int level,
                           Location location, GameMode gameMode, boolean allowFlight, boolean flying) {
        this.contents = contents;
        this.armor = armor;
        this.enderChestContents = enderChestContents;
        this.health = health;
        this.food = food;
        this.experience = experience;
        this.level = level;
        this.location = location;
        this.gameMode = gameMode;
        this.allowFlight = allowFlight;
        this.flying = flying;
    }

    public static PlayerSnapshot capture(Player player) {
        return new PlayerSnapshot(
            player.getInventory().getContents().clone(),
            player.getInventory().getArmorContents().clone(),
            player.getEnderChest().getContents().clone(),
            player.getHealth(),
            player.getFoodLevel(),
            player.getExp(),
            player.getLevel(),
            player.getLocation().clone(),
            player.getGameMode(),
            player.getAllowFlight(),
            player.isFlying()
        );
    }

    public void restore(Player player) {
        restore(player, location);
    }

    public void restore(Player player, Location targetLocation) {
        player.getInventory().setContents(contents);
        player.getInventory().setArmorContents(armor);
        player.getEnderChest().setContents(enderChestContents);
        player.setHealth(Math.min(player.getMaxHealth(), health));
        player.setFoodLevel(food);
        player.setExp(experience);
        player.setLevel(level);
        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(flying);
        if (targetLocation != null) {
            player.teleport(targetLocation);
        }
        player.updateInventory();
    }
}

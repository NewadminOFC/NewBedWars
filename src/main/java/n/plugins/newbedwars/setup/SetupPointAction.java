package n.plugins.newbedwars.setup;

public enum SetupPointAction {
    ARENA_WAITING_SPAWN("Spawn de espera", true),
    ARENA_ANTI_VOID("Anti-void", false),
    TEAM_SPAWN("Spawn do time", true),
    TEAM_BED("Cama", true),
    TEAM_CHEST("Bau do time", true),
    TEAM_ENDER_CHEST("Ender chest", true),
    TEAM_IRON_GENERATOR("Gerador de ferro", true),
    TEAM_GOLD_GENERATOR("Gerador de ouro", true),
    TEAM_ITEM_SHOP("Loja de itens", true),
    TEAM_UPGRADE_SHOP("Loja de upgrades", true),
    ARENA_DIAMOND_GENERATOR("Gerador de diamante", true),
    ARENA_EMERALD_GENERATOR("Gerador de esmeralda", true);

    private final String displayName;
    private final boolean blockRequired;

    SetupPointAction(String displayName, boolean blockRequired) {
        this.displayName = displayName;
        this.blockRequired = blockRequired;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isBlockRequired() {
        return blockRequired;
    }
}

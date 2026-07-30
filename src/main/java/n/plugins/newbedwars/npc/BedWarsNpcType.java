package n.plugins.newbedwars.npc;

public enum BedWarsNpcType {
    SOLO("1v1"),
    ITEM_SHOP("Loja"),
    UPGRADE_SHOP("Melhorias");

    private final String displayName;

    BedWarsNpcType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

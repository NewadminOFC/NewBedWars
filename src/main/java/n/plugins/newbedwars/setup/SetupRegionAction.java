package n.plugins.newbedwars.setup;

public enum SetupRegionAction {
    WAITING_AREA("Sala de espera"),
    TEAM_ISLAND("Ilha do time"),
    TEAM_PROTECTION("Protecao inicial");

    private final String displayName;

    SetupRegionAction(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

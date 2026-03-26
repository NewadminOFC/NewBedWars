package n.plugins.newbedwars.arena;

public enum ArenaState {
    WAITING("Aguardando"),
    STARTING("Iniciando"),
    INGAME("Em jogo"),
    ENDING("Finalizando"),
    RESETTING("Resetando");

    private final String displayName;

    ArenaState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

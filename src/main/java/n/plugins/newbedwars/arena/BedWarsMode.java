package n.plugins.newbedwars.arena;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum BedWarsMode {
    ONE_VS_ONE("1v1", "one_vs_one", "1v1", 1, Arrays.asList(TeamColor.RED, TeamColor.BLUE)),
    TWO_VS_TWO("2v2", "two_vs_two", "2v2", 2, Arrays.asList(TeamColor.RED, TeamColor.BLUE)),
    THREE_VS_THREE("3v3", "three_vs_three", "3v3", 3, Arrays.asList(TeamColor.RED, TeamColor.BLUE)),
    FOUR_VS_FOUR("4v4", "four_vs_four", "4v4", 4, Arrays.asList(TeamColor.RED, TeamColor.BLUE)),
    SOLO("solo", "solo", "Solo", 1, Arrays.asList(
        TeamColor.RED, TeamColor.BLUE, TeamColor.GREEN, TeamColor.YELLOW,
        TeamColor.CYAN, TeamColor.PINK, TeamColor.GRAY, TeamColor.WHITE
    )),
    DUPLA("dupla", "dupla", "Dupla", 2, Arrays.asList(
        TeamColor.RED, TeamColor.BLUE, TeamColor.GREEN, TeamColor.YELLOW,
        TeamColor.CYAN, TeamColor.PINK, TeamColor.GRAY, TeamColor.WHITE
    )),
    TRIO("trio", "trio", "Trio", 3, Arrays.asList(
        TeamColor.RED, TeamColor.BLUE, TeamColor.GREEN, TeamColor.YELLOW
    )),
    QUARTETO("quarteto", "quarteto", "Quarteto", 4, Arrays.asList(
        TeamColor.RED, TeamColor.BLUE, TeamColor.GREEN, TeamColor.YELLOW
    ));

    private final String id;
    private final String configKey;
    private final String displayName;
    private final int teamSize;
    private final List<TeamColor> activeColors;

    BedWarsMode(String id, String configKey, String displayName, int teamSize, List<TeamColor> activeColors) {
        this.id = id;
        this.configKey = configKey;
        this.displayName = displayName;
        this.teamSize = teamSize;
        this.activeColors = Collections.unmodifiableList(activeColors);
    }

    public String getId() {
        return id;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getTeamSize() {
        return teamSize;
    }

    public List<TeamColor> getActiveColors() {
        return activeColors;
    }

    public int getMaxPlayers() {
        return teamSize * activeColors.size();
    }

    public String getNpcDefaultName() {
        return "BedWars" + displayName.replace(" ", "");
    }

    public static BedWarsMode fromInput(String raw) {
        if (raw == null) {
            return null;
        }

        String normalized = raw.trim().toLowerCase();
        for (BedWarsMode mode : values()) {
            if (mode.id.equalsIgnoreCase(normalized)
                || mode.configKey.equalsIgnoreCase(normalized)
                || mode.name().equalsIgnoreCase(normalized)) {
                return mode;
            }
        }

        if ("solo1v1".equals(normalized) || "duelo".equals(normalized)) {
            return ONE_VS_ONE;
        }
        if ("quartet".equals(normalized) || "4x4".equals(normalized)) {
            return QUARTETO;
        }
        return null;
    }
}

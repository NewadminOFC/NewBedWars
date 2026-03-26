package n.plugins.newbedwars.arena;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.ChatColor;

public enum TeamColor {
    RED("Vermelho", ChatColor.RED, (short) 14),
    BLUE("Azul", ChatColor.BLUE, (short) 11),
    GREEN("Verde", ChatColor.GREEN, (short) 5),
    YELLOW("Amarelo", ChatColor.YELLOW, (short) 4),
    CYAN("Ciano", ChatColor.AQUA, (short) 9),
    PINK("Rosa", ChatColor.LIGHT_PURPLE, (short) 6),
    GRAY("Cinza", ChatColor.GRAY, (short) 7),
    WHITE("Branco", ChatColor.WHITE, (short) 0);

    private final String displayName;
    private final ChatColor chatColor;
    private final short woolData;

    TeamColor(String displayName, ChatColor chatColor, short woolData) {
        this.displayName = displayName;
        this.chatColor = chatColor;
        this.woolData = woolData;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ChatColor getChatColor() {
        return chatColor;
    }

    public short getWoolData() {
        return woolData;
    }

    public String getColoredName() {
        return chatColor + displayName;
    }

    public static List<TeamColor> getOneVsOneColors() {
        return Collections.unmodifiableList(Arrays.asList(RED, BLUE));
    }
}

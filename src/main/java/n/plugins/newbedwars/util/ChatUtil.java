package n.plugins.newbedwars.util;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;

public final class ChatUtil {

    private ChatUtil() {
    }

    public static String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }

    public static List<String> color(List<String> lines) {
        List<String> colored = new ArrayList<String>();
        if (lines == null) {
            return colored;
        }

        for (String line : lines) {
            colored.add(color(line));
        }
        return colored;
    }

    public static String bool(boolean value, String trueText, String falseText) {
        return value ? trueText : falseText;
    }
}

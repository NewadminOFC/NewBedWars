package n.plugins.newbedwars.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class TitleUtil {

    private TitleUtil() {
    }

    @SuppressWarnings("unchecked")
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null || !player.isOnline()) {
            return;
        }

        String safeTitle = ChatUtil.color(title);
        String safeSubtitle = ChatUtil.color(subtitle);
        if (isBlank(safeTitle) && isBlank(safeSubtitle)) {
            return;
        }

        try {
            Method modernMethod = player.getClass().getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class);
            modernMethod.invoke(player, safeTitle, safeSubtitle, Integer.valueOf(fadeIn), Integer.valueOf(stay), Integer.valueOf(fadeOut));
            return;
        } catch (Exception ignored) {
        }

        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            String version = packageName.substring(packageName.lastIndexOf('.') + 1);

            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
            Object handle = craftPlayerClass.getMethod("getHandle").invoke(craftPlayerClass.cast(player));
            Object connection = handle.getClass().getField("playerConnection").get(handle);

            Class<?> serializerClass = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent$ChatSerializer");
            Method serializerMethod = serializerClass.getMethod("a", String.class);
            Class<?> iChatBaseComponentClass = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent");
            Class<?> packetClass = Class.forName("net.minecraft.server." + version + ".Packet");
            Class<?> packetTitleClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutTitle");
            Class<?> enumTitleActionClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutTitle$EnumTitleAction");
            Method sendPacket = connection.getClass().getMethod("sendPacket", packetClass);

            Object timesAction = Enum.valueOf((Class<Enum>) enumTitleActionClass.asSubclass(Enum.class), "TIMES");
            Constructor<?> timingsConstructor = packetTitleClass.getConstructor(enumTitleActionClass, iChatBaseComponentClass, int.class, int.class, int.class);
            sendPacket.invoke(connection, timingsConstructor.newInstance(timesAction, null, Integer.valueOf(fadeIn), Integer.valueOf(stay), Integer.valueOf(fadeOut)));

            Constructor<?> textConstructor = packetTitleClass.getConstructor(enumTitleActionClass, iChatBaseComponentClass);
            if (!isBlank(safeTitle)) {
                Object titleAction = Enum.valueOf((Class<Enum>) enumTitleActionClass.asSubclass(Enum.class), "TITLE");
                Object titleComponent = serializerMethod.invoke(null, jsonText(safeTitle));
                sendPacket.invoke(connection, textConstructor.newInstance(titleAction, titleComponent));
            }

            if (!isBlank(safeSubtitle)) {
                Object subtitleAction = Enum.valueOf((Class<Enum>) enumTitleActionClass.asSubclass(Enum.class), "SUBTITLE");
                Object subtitleComponent = serializerMethod.invoke(null, jsonText(safeSubtitle));
                sendPacket.invoke(connection, textConstructor.newInstance(subtitleAction, subtitleComponent));
            }
        } catch (Exception exception) {
            if (!isBlank(safeTitle)) {
                player.sendMessage(safeTitle);
            }
            if (!isBlank(safeSubtitle)) {
                player.sendMessage(safeSubtitle);
            }
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    private static String jsonText(String text) {
        String escaped = text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n");
        return "{\"text\":\"" + escaped + "\"}";
    }
}

package n.plugins.newbedwars.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class TitleUtil {

    private TitleUtil() {
    }

    private static boolean modernApi;
    private static Method modernSendTitleMethod;
    private static boolean reflectionInitialized;
    private static boolean reflectionFailed;

    private static String cachedServerVersion;
    private static Class<?> cachedCraftPlayerClass;
    private static Method cachedGetHandleMethod;
    private static Field cachedPlayerConnectionField;
    private static Class<?> cachedSerializerClass;
    private static Method cachedSerializerMethod;
    private static Class<?> cachedPacketClass;
    private static Constructor<?> cachedTimingsConstructor;
    private static Constructor<?> cachedTextConstructor;
    private static Class<?> cachedEnumTitleActionClass;
    private static Object cachedTimesAction;
    private static Object cachedTitleAction;
    private static Object cachedSubtitleAction;
    private static Method cachedSendPacketMethod;

    private static void initReflection() {
        if (reflectionInitialized) {
            return;
        }
        reflectionInitialized = true;

        try {
            Method m = Player.class.getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class);
            modernSendTitleMethod = m;
            modernApi = true;
            return;
        } catch (Exception ignored) {
        }

        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            cachedServerVersion = packageName.substring(packageName.lastIndexOf('.') + 1);

            cachedCraftPlayerClass = Class.forName("org.bukkit.craftbukkit." + cachedServerVersion + ".entity.CraftPlayer");
            cachedGetHandleMethod = cachedCraftPlayerClass.getMethod("getHandle");
            Class<?> handleClass = Class.forName("net.minecraft.server." + cachedServerVersion + ".EntityPlayer");
            cachedPlayerConnectionField = handleClass.getField("playerConnection");

            cachedSerializerClass = Class.forName("net.minecraft.server." + cachedServerVersion + ".IChatBaseComponent$ChatSerializer");
            cachedSerializerMethod = cachedSerializerClass.getMethod("a", String.class);
            Class<?> iChatBaseComponentClass = Class.forName("net.minecraft.server." + cachedServerVersion + ".IChatBaseComponent");
            Class<?> packetBaseClass = Class.forName("net.minecraft.server." + cachedServerVersion + ".Packet");

            cachedPacketClass = Class.forName("net.minecraft.server." + cachedServerVersion + ".PacketPlayOutTitle");
            cachedEnumTitleActionClass = Class.forName("net.minecraft.server." + cachedServerVersion + ".PacketPlayOutTitle$EnumTitleAction");

            cachedTimingsConstructor = cachedPacketClass.getConstructor(cachedEnumTitleActionClass, iChatBaseComponentClass, int.class, int.class, int.class);
            cachedTextConstructor = cachedPacketClass.getConstructor(cachedEnumTitleActionClass, iChatBaseComponentClass);

            @SuppressWarnings("unchecked")
            Class<Enum> enumClass = (Class<Enum>) cachedEnumTitleActionClass.asSubclass(Enum.class);
            cachedTimesAction = Enum.valueOf(enumClass, "TIMES");
            cachedTitleAction = Enum.valueOf(enumClass, "TITLE");
            cachedSubtitleAction = Enum.valueOf(enumClass, "SUBTITLE");

            Class<?> connectionClass = Class.forName("net.minecraft.server." + cachedServerVersion + ".PlayerConnection");
            cachedSendPacketMethod = connectionClass.getMethod("sendPacket", packetBaseClass);
        } catch (Exception exception) {
            reflectionFailed = true;
        }
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

        initReflection();

        if (modernApi) {
            try {
                modernSendTitleMethod.invoke(player, safeTitle, safeSubtitle, Integer.valueOf(fadeIn), Integer.valueOf(stay), Integer.valueOf(fadeOut));
                return;
            } catch (Exception ignored) {
            }
        }

        if (reflectionFailed) {
            fallbackSendMessage(player, safeTitle, safeSubtitle);
            return;
        }

        try {
            Object craftPlayer = cachedCraftPlayerClass.cast(player);
            Object handle = cachedGetHandleMethod.invoke(craftPlayer);
            Object connection = cachedPlayerConnectionField.get(handle);

            Object timingsPacket = cachedTimingsConstructor.newInstance(cachedTimesAction, null, Integer.valueOf(fadeIn), Integer.valueOf(stay), Integer.valueOf(fadeOut));
            cachedSendPacketMethod.invoke(connection, timingsPacket);

            if (!isBlank(safeTitle)) {
                Object titleComponent = cachedSerializerMethod.invoke(null, jsonText(safeTitle));
                Object titlePacket = cachedTextConstructor.newInstance(cachedTitleAction, titleComponent);
                cachedSendPacketMethod.invoke(connection, titlePacket);
            }

            if (!isBlank(safeSubtitle)) {
                Object subtitleComponent = cachedSerializerMethod.invoke(null, jsonText(safeSubtitle));
                Object subtitlePacket = cachedTextConstructor.newInstance(cachedSubtitleAction, subtitleComponent);
                cachedSendPacketMethod.invoke(connection, subtitlePacket);
            }
        } catch (Exception exception) {
            fallbackSendMessage(player, safeTitle, safeSubtitle);
        }
    }

    private static void fallbackSendMessage(Player player, String title, String subtitle) {
        if (!isBlank(title)) {
            player.sendMessage(title);
        }
        if (!isBlank(subtitle)) {
            player.sendMessage(subtitle);
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

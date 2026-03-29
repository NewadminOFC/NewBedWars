package n.plugins.newbedwars.util;

import java.util.Locale;
import n.plugins.newbedwars.NewBedWars;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class SoundUtil {

    private SoundUtil() {
    }

    public static void playConfigured(NewBedWars plugin, Player player, String path, String fallbackSound, float fallbackVolume, float fallbackPitch) {
        if (plugin == null || player == null || !player.isOnline() || !plugin.getConfig().getBoolean("settings.sounds", true)) {
            return;
        }

        String soundName = plugin.getConfig().getString(path + ".sound", fallbackSound);
        float volume = (float) plugin.getConfig().getDouble(path + ".volume", fallbackVolume);
        float pitch = (float) plugin.getConfig().getDouble(path + ".pitch", fallbackPitch);
        Sound sound = parseSound(soundName, fallbackSound);
        if (sound == null) {
            return;
        }

        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private static Sound parseSound(String primary, String fallback) {
        Sound parsed = parse(primary);
        return parsed != null ? parsed : parse(fallback);
    }

    private static Sound parse(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        try {
            return Sound.valueOf(name.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}

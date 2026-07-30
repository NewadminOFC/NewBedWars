package n.plugins.newbedwars.util;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import n.plugins.newbedwars.NewBedWars;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class SoundUtil {

    private SoundUtil() {
    }

    private static final ConcurrentHashMap<String, SoundCacheEntry> cache = new ConcurrentHashMap<String, SoundCacheEntry>();
    private static long cacheExpiry;

    private static final class SoundCacheEntry {
        final boolean enabled;
        final Sound sound;
        final float volume;
        final float pitch;

        SoundCacheEntry(boolean enabled, Sound sound, float volume, float pitch) {
            this.enabled = enabled;
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    public static void playConfigured(NewBedWars plugin, Player player, String path, String fallbackSound, float fallbackVolume, float fallbackPitch) {
        if (plugin == null || player == null || !player.isOnline()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now > cacheExpiry) {
            cache.clear();
            cacheExpiry = now + 3000L;
        }

        SoundCacheEntry entry = cache.get(path);
        if (entry == null) {
            if (!plugin.getConfig().getBoolean("settings.sounds", true)) {
                cache.put(path, new SoundCacheEntry(false, null, 0, 0));
                return;
            }
            String soundName = plugin.getConfig().getString(path + ".sound", fallbackSound);
            float volume = (float) plugin.getConfig().getDouble(path + ".volume", fallbackVolume);
            float pitch = (float) plugin.getConfig().getDouble(path + ".pitch", fallbackPitch);
            Sound sound = parseSound(soundName, fallbackSound);
            entry = new SoundCacheEntry(true, sound, volume, pitch);
            cache.put(path, entry);
        }

        if (!entry.enabled || entry.sound == null) {
            return;
        }

        player.playSound(player.getLocation(), entry.sound, entry.volume, entry.pitch);
    }

    public static void clearCache() {
        cache.clear();
        cacheExpiry = 0;
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

package n.plugins.newbedwars.util;

public final class TimeUtil {

    private TimeUtil() {
    }

    public static String formatSeconds(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}

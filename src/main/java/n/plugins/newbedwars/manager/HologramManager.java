package n.plugins.newbedwars.manager;

import n.plugins.newbedwars.NewBedWars;

public class HologramManager {

    private final NewBedWars plugin;

    public HologramManager(NewBedWars plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("settings.holograms", false);
    }
}

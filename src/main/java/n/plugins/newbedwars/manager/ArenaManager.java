package n.plugins.newbedwars.manager;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.arena.ArenaState;
import n.plugins.newbedwars.arena.ArenaTeam;
import n.plugins.newbedwars.arena.BedData;
import n.plugins.newbedwars.arena.GeneratorPoint;
import n.plugins.newbedwars.arena.GeneratorType;
import n.plugins.newbedwars.arena.TeamColor;
import n.plugins.newbedwars.util.CuboidRegion;
import n.plugins.newbedwars.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class ArenaManager {

    private final NewBedWars plugin;
    private final Map<String, Arena> arenas;
    private final Map<UUID, String> playerArenas;

    public ArenaManager(NewBedWars plugin) {
        this.plugin = plugin;
        this.arenas = new LinkedHashMap<String, Arena>();
        this.playerArenas = new LinkedHashMap<UUID, String>();
    }

    public Arena createArena(String name, String worldName) {
        Arena arena = new Arena(name, worldName);
        arenas.put(name.toLowerCase(), arena);
        saveArena(arena);
        return arena;
    }

    public void deleteArena(String name) {
        arenas.remove(name.toLowerCase());
        File file = new File(plugin.getDataFolder(), "arenas/" + name + ".yml");
        if (file.exists()) {
            file.delete();
        }
    }

    public Arena getArena(String name) {
        return name == null ? null : arenas.get(name.toLowerCase());
    }

    public Collection<Arena> getArenas() {
        return arenas.values();
    }

    public Arena getArenaByPlayer(UUID uniqueId) {
        String arenaName = playerArenas.get(uniqueId);
        return arenaName == null ? null : getArena(arenaName);
    }

    public void setPlayerArena(UUID uniqueId, Arena arena) {
        playerArenas.put(uniqueId, arena.getName());
    }

    public void clearPlayerArena(UUID uniqueId) {
        playerArenas.remove(uniqueId);
    }

    public void loadArenas() {
        // Cada arena e carregada de um YML proprio para manter suporte a multiplos mapas.
        arenas.clear();
        File folder = new File(plugin.getDataFolder(), "arenas");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!file.getName().endsWith(".yml")) {
                continue;
            }

            FileConfiguration configuration = YamlConfiguration.loadConfiguration(file);
            String name = configuration.getString("name", file.getName().replace(".yml", ""));
            String worldName = configuration.getString("world");
            Arena arena = new Arena(name, worldName);
            arena.setState(ArenaState.WAITING);
            arena.setWaitingSpawn(LocationUtil.loadLocation(configuration, "waiting-spawn"));

            Location waitingPos1 = LocationUtil.loadLocation(configuration, "waiting-region.pos1");
            Location waitingPos2 = LocationUtil.loadLocation(configuration, "waiting-region.pos2");
            if (waitingPos1 != null && waitingPos2 != null) {
                arena.setWaitingRegion(new CuboidRegion(waitingPos1, waitingPos2));
            }

            ConfigurationSection teamsSection = configuration.getConfigurationSection("teams");
            if (teamsSection != null) {
                for (TeamColor color : TeamColor.values()) {
                    ConfigurationSection teamSection = teamsSection.getConfigurationSection(color.name());
                    if (teamSection == null) {
                        continue;
                    }

                    ArenaTeam team = arena.getTeam(color);
                    team.setSpawnLocation(LocationUtil.loadLocation(teamSection, "spawn"));

                    Location bedHead = LocationUtil.loadLocation(teamSection, "bed.head");
                    Location bedFoot = LocationUtil.loadLocation(teamSection, "bed.foot");
                    if (bedHead != null && bedFoot != null) {
                        team.setBedData(new BedData(bedHead, bedFoot));
                    }

                    team.setItemShopLocation(LocationUtil.loadLocation(teamSection, "item-shop"));
                    team.setUpgradeShopLocation(LocationUtil.loadLocation(teamSection, "upgrade-shop"));

                    Location islandPos1 = LocationUtil.loadLocation(teamSection, "island-region.pos1");
                    Location islandPos2 = LocationUtil.loadLocation(teamSection, "island-region.pos2");
                    if (islandPos1 != null && islandPos2 != null) {
                        team.setIslandRegion(new CuboidRegion(islandPos1, islandPos2));
                    }

                    Location protectionPos1 = LocationUtil.loadLocation(teamSection, "protection-region.pos1");
                    Location protectionPos2 = LocationUtil.loadLocation(teamSection, "protection-region.pos2");
                    if (protectionPos1 != null && protectionPos2 != null) {
                        team.setProtectionRegion(new CuboidRegion(protectionPos1, protectionPos2));
                    }

                    ConfigurationSection generatorsSection = teamSection.getConfigurationSection("generators");
                    if (generatorsSection != null) {
                        for (GeneratorType type : GeneratorType.values()) {
                            team.getGenerators(type).clear();
                            ConfigurationSection typeSection = generatorsSection.getConfigurationSection(type.name());
                            if (typeSection == null) {
                                continue;
                            }

                            for (String key : typeSection.getKeys(false)) {
                                Location location = LocationUtil.loadLocation(typeSection, key);
                                if (location != null) {
                                    team.getGenerators(type).add(new GeneratorPoint(type, location, color));
                                }
                            }
                        }
                    }

                    team.setConfirmed(teamSection.getBoolean("confirmed", false));
                }
            }

            ConfigurationSection globalGeneratorsSection = configuration.getConfigurationSection("global-generators");
            if (globalGeneratorsSection != null) {
                for (GeneratorType type : new GeneratorType[] {GeneratorType.DIAMOND, GeneratorType.EMERALD}) {
                    arena.clearGlobalGenerators(type);
                    ConfigurationSection typeSection = globalGeneratorsSection.getConfigurationSection(type.name());
                    if (typeSection == null) {
                        continue;
                    }

                    for (String key : typeSection.getKeys(false)) {
                        Location location = LocationUtil.loadLocation(typeSection, key);
                        if (location != null) {
                            arena.addGlobalGenerator(type, location);
                        }
                    }
                }
            }

            arena.setReady(configuration.getBoolean("ready", false));
            arenas.put(name.toLowerCase(), arena);
        }
    }

    public void saveAllArenas() {
        for (Arena arena : arenas.values()) {
            saveArena(arena);
        }
    }

    public void saveArena(Arena arena) {
        // O arquivo da arena guarda apenas dados persistentes de setup, nunca runtime da partida.
        File file = new File(plugin.getDataFolder(), "arenas/" + arena.getName() + ".yml");
        FileConfiguration configuration = new YamlConfiguration();

        configuration.set("name", arena.getName());
        configuration.set("world", arena.getWorldName());
        configuration.set("ready", arena.isReady());
        configuration.set("state", arena.getState().name());

        LocationUtil.saveLocation(configuration, "waiting-spawn", arena.getWaitingSpawn());
        if (arena.getWaitingRegion() != null) {
            LocationUtil.saveLocation(configuration, "waiting-region.pos1", arena.getWaitingRegion().getPos1());
            LocationUtil.saveLocation(configuration, "waiting-region.pos2", arena.getWaitingRegion().getPos2());
        }

        ConfigurationSection teamsSection = configuration.createSection("teams");
        for (TeamColor color : TeamColor.values()) {
            ArenaTeam team = arena.getTeam(color);
            ConfigurationSection teamSection = teamsSection.createSection(color.name());
            teamSection.set("confirmed", team.isConfirmed());

            LocationUtil.saveLocation(teamSection, "spawn", team.getSpawnLocation());
            if (team.getBedData() != null) {
                LocationUtil.saveLocation(teamSection, "bed.head", team.getBedData().getHead());
                LocationUtil.saveLocation(teamSection, "bed.foot", team.getBedData().getFoot());
            }

            LocationUtil.saveLocation(teamSection, "item-shop", team.getItemShopLocation());
            LocationUtil.saveLocation(teamSection, "upgrade-shop", team.getUpgradeShopLocation());

            if (team.getIslandRegion() != null) {
                LocationUtil.saveLocation(teamSection, "island-region.pos1", team.getIslandRegion().getPos1());
                LocationUtil.saveLocation(teamSection, "island-region.pos2", team.getIslandRegion().getPos2());
            }

            if (team.getProtectionRegion() != null) {
                LocationUtil.saveLocation(teamSection, "protection-region.pos1", team.getProtectionRegion().getPos1());
                LocationUtil.saveLocation(teamSection, "protection-region.pos2", team.getProtectionRegion().getPos2());
            }

            ConfigurationSection generatorsSection = teamSection.createSection("generators");
            for (GeneratorType type : GeneratorType.values()) {
                ConfigurationSection typeSection = generatorsSection.createSection(type.name());
                int index = 0;
                for (GeneratorPoint point : team.getGenerators(type)) {
                    LocationUtil.saveLocation(typeSection, String.valueOf(index), point.getLocation());
                    index++;
                }
            }
        }

        ConfigurationSection globalGeneratorsSection = configuration.createSection("global-generators");
        for (GeneratorType type : new GeneratorType[] {GeneratorType.DIAMOND, GeneratorType.EMERALD}) {
            ConfigurationSection typeSection = globalGeneratorsSection.createSection(type.name());
            int index = 0;
            for (GeneratorPoint point : arena.getGlobalGenerators(type)) {
                LocationUtil.saveLocation(typeSection, String.valueOf(index), point.getLocation());
                index++;
            }
        }

        try {
            configuration.save(file);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }
}

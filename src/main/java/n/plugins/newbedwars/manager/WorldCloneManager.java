package n.plugins.newbedwars.manager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

public class WorldCloneManager {

    private static final String CLONE_PREFIX = "bw_clone_";
    private final NewBedWars plugin;

    public WorldCloneManager(NewBedWars plugin) {
        this.plugin = plugin;
    }

    public synchronized void startupCleanup() {
        File serverRoot = getServerRoot();
        if (serverRoot == null || !serverRoot.exists()) {
            return;
        }

        File[] files = serverRoot.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!file.isDirectory() || !file.getName().startsWith(CLONE_PREFIX)) {
                continue;
            }

            World world = Bukkit.getWorld(file.getName());
            if (world != null) {
                Bukkit.unloadWorld(world, false);
            }
            deleteDirectory(file);
        }
    }

    public synchronized boolean ensureClone(Arena arena) {
        if (arena == null) {
            return false;
        }

        World activeWorld = arena.getActiveWorld();
        if (activeWorld != null) {
            return true;
        }

        World baseWorld = arena.getWorld();
        if (baseWorld == null || baseWorld.getWorldFolder() == null || !baseWorld.getWorldFolder().exists()) {
            return false;
        }

        String cloneName = buildCloneName(arena);
        File cloneFolder = new File(getServerRoot(), cloneName);
        if (cloneFolder.exists() && !deleteDirectory(cloneFolder)) {
            return false;
        }

        try {
            copyWorldFolder(baseWorld.getWorldFolder(), cloneFolder);
        } catch (IOException exception) {
            exception.printStackTrace();
            deleteDirectory(cloneFolder);
            return false;
        }

        World cloneWorld;
        try {
            cloneWorld = Bukkit.createWorld(new WorldCreator(cloneName));
        } catch (Exception exception) {
            exception.printStackTrace();
            deleteDirectory(cloneFolder);
            return false;
        }

        if (cloneWorld == null) {
            deleteDirectory(cloneFolder);
            return false;
        }

        cloneWorld.setAutoSave(false);
        try {
            cloneWorld.setKeepSpawnInMemory(false);
        } catch (NoSuchMethodError ignored) {
        }

        arena.setActiveWorldName(cloneName);
        return true;
    }

    public synchronized void destroyClone(Arena arena) {
        if (arena == null || !arena.hasActiveWorld()) {
            return;
        }

        String cloneName = arena.getActiveWorldName();
        World cloneWorld = Bukkit.getWorld(cloneName);
        if (cloneWorld != null) {
            Bukkit.unloadWorld(cloneWorld, false);
        }

        deleteDirectory(new File(getServerRoot(), cloneName));
        arena.clearActiveWorld();
    }

    private File getServerRoot() {
        try {
            File worldContainer = plugin.getServer().getWorldContainer();
            if (worldContainer != null) {
                return worldContainer.getAbsoluteFile();
            }
        } catch (Exception ignored) {
        }

        File dataFolder = plugin.getDataFolder() == null ? null : plugin.getDataFolder().getAbsoluteFile();
        if (dataFolder == null) {
            return null;
        }

        File pluginsFolder = dataFolder.getParentFile();
        if (pluginsFolder != null && pluginsFolder.getParentFile() != null) {
            return pluginsFolder.getParentFile();
        }

        return pluginsFolder != null ? pluginsFolder : dataFolder;
    }

    private String buildCloneName(Arena arena) {
        String arenaPart = arena.getName().toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
        return CLONE_PREFIX + arenaPart + "_" + System.currentTimeMillis();
    }

    private void copyWorldFolder(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) {
                throw new IOException("Nao foi possivel criar " + target.getAbsolutePath());
            }

            File[] children = source.listFiles();
            if (children == null) {
                return;
            }

            for (File child : children) {
                if (shouldIgnore(child.getName())) {
                    continue;
                }

                copyWorldFolder(child, new File(target, child.getName()));
            }
            return;
        }

        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private boolean shouldIgnore(String name) {
        return "uid.dat".equalsIgnoreCase(name)
            || "session.lock".equalsIgnoreCase(name)
            || "session.dat".equalsIgnoreCase(name);
    }

    private boolean deleteDirectory(File file) {
        if (file == null || !file.exists()) {
            return true;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteDirectory(child)) {
                        return false;
                    }
                }
            }
        }

        try {
            Files.deleteIfExists(file.toPath());
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}

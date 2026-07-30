package n.plugins.newbedwars.manager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import n.plugins.newbedwars.util.WorldEntitySanitizer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

public class WorldCloneManager {

    public interface CloneCallback {
        void complete(boolean success);
    }

    private static final String CLONE_PREFIX = "bw_clone_";

    private final NewBedWars plugin;
    private final Set<String> cloningArenas = new HashSet<String>();
    private final Set<String> cancelledArenas = new HashSet<String>();
    private final Map<String, List<CloneCallback>> callbacks = new HashMap<String, List<CloneCallback>>();
    private final Map<String, File> pendingCloneFolders = new HashMap<String, File>();
    private final ExecutorService fileExecutor;
    private boolean shuttingDown;

    public WorldCloneManager(NewBedWars plugin) {
        this.plugin = plugin;
        this.fileExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            private final AtomicInteger sequence = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "NewBedWars-WorldIO-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    /**
     * Removes clone folders left by a crash without blocking the server thread.
     */
    public void startupCleanup() {
        final List<File> staleFolders = new ArrayList<File>();
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
            staleFolders.add(file);
        }

        if (staleFolders.isEmpty()) {
            return;
        }

        submitFileTask(new Runnable() {
            @Override
            public void run() {
                for (File folder : staleFolders) {
                    deleteDirectory(folder);
                }
            }
        });
    }

    public boolean isCloning(Arena arena) {
        return arena != null && cloningArenas.contains(key(arena));
    }

    /**
     * Prepares a match world asynchronously. Template worlds that are currently
     * unloaded are copied directly from disk and are not started just to create
     * a match.
     */
    public void ensureClone(final Arena arena, CloneCallback callback) {
        if (arena == null || shuttingDown) {
            invoke(callback, false);
            return;
        }

        World activeWorld = arena.getActiveWorld();
        if (activeWorld != null) {
            invoke(callback, true);
            return;
        }
        if (arena.hasActiveWorld()) {
            arena.clearActiveWorld();
        }

        final String arenaKey = key(arena);
        List<CloneCallback> waiting = callbacks.get(arenaKey);
        if (waiting == null) {
            waiting = new ArrayList<CloneCallback>();
            callbacks.put(arenaKey, waiting);
        }
        if (callback != null) {
            waiting.add(callback);
        }

        if (cloningArenas.contains(arenaKey)) {
            return;
        }

        final File sourceFolder = resolveTemplateFolder(arena);
        if (sourceFolder == null || !sourceFolder.isDirectory()) {
            finishClone(arena, arenaKey, false);
            return;
        }

        World loadedTemplate = Bukkit.getWorld(arena.getWorldName());
        if (loadedTemplate != null && plugin.getConfig().getBoolean("performance.world-cloning.save-template-before-copy", false)) {
            try {
                loadedTemplate.save();
            } catch (Exception exception) {
                plugin.getLogger().warning("Nao foi possivel salvar o mapa-base " + arena.getWorldName() + " antes do clone.");
            }
        }

        plugin.getSetupManager().clearArenaSetupVisuals(arena);
        final String cloneName = buildCloneName(arena);
        final File cloneFolder = new File(getServerRoot(), cloneName);
        final boolean stripArmorStands = plugin.getConfig().getBoolean(
            "performance.world-cloning.strip-armorstands-before-load", true);
        final boolean failOnSanitizeFailure = plugin.getConfig().getBoolean(
            "performance.world-cloning.fail-if-entity-sanitize-fails", true);

        cloningArenas.add(arenaKey);
        cancelledArenas.remove(arenaKey);
        pendingCloneFolders.put(arenaKey, cloneFolder);

        submitFileTask(new Runnable() {
            @Override
            public void run() {
                boolean copied = false;
                try {
                    copyWorldFolder(sourceFolder, cloneFolder);
                    if (stripArmorStands) {
                        WorldEntitySanitizer.Result result = WorldEntitySanitizer.sanitizeArmorStands(cloneFolder);
                        if (result.hasFailures() && failOnSanitizeFailure) {
                            throw new IOException("sanitizacao encontrou "
                                + (result.getFailedChunks() + result.getFailedRegions()) + " arquivo(s) invalido(s)");
                        }
                        if (result.getArmorStandsRemoved() > 0) {
                            plugin.getLogger().info("Clone " + cloneName + " limpo antes do carregamento: "
                                + result.getArmorStandsRemoved() + " ArmorStand(s) removido(s) de "
                                + result.getChunksScanned() + " chunk(s).");
                        }
                    }
                    copied = true;
                } catch (IOException exception) {
                    plugin.getLogger().warning("Falha ao preparar o mapa-base " + arena.getWorldName() + ": " + exception.getMessage());
                    deleteDirectory(cloneFolder);
                }

                final boolean copySuccess = copied;
                try {
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            finishWorldCreation(arena, arenaKey, cloneName, cloneFolder, copySuccess);
                        }
                    });
                } catch (RuntimeException exception) {
                    deleteDirectory(cloneFolder);
                }
            }
        });
    }

    private void finishWorldCreation(Arena arena, String arenaKey, String cloneName, File cloneFolder, boolean copySuccess) {
        pendingCloneFolders.remove(arenaKey);
        if (!copySuccess || shuttingDown || cancelledArenas.remove(arenaKey)) {
            scheduleDelete(cloneFolder);
            finishClone(arena, arenaKey, false);
            return;
        }

        World cloneWorld;
        try {
            cloneWorld = Bukkit.createWorld(new WorldCreator(cloneName));
        } catch (Exception exception) {
            plugin.getLogger().warning("Falha ao carregar o clone " + cloneName + ": " + exception.getMessage());
            scheduleDelete(cloneFolder);
            finishClone(arena, arenaKey, false);
            return;
        }

        if (cloneWorld == null) {
            scheduleDelete(cloneFolder);
            finishClone(arena, arenaKey, false);
            return;
        }

        configureCloneWorld(cloneWorld);
        arena.setActiveWorldName(cloneName);
        plugin.getArenaManager().invalidateWorldIndex();
        plugin.getEntityCleanupManager().cleanupArenaWorld(arena, new EntityCleanupManager.Completion() {
            @Override
            public void complete() {
                if (shuttingDown
                    || cancelledArenas.remove(arenaKey)
                    || !cloneName.equalsIgnoreCase(arena.getActiveWorldName())) {
                    destroyClone(arena);
                    finishClone(arena, arenaKey, false);
                    return;
                }

                finishClone(arena, arenaKey, true);
                scheduleTemplateUnload(arena);
            }
        });
    }

    private void finishClone(Arena arena, String arenaKey, boolean success) {
        cloningArenas.remove(arenaKey);
        cancelledArenas.remove(arenaKey);
        pendingCloneFolders.remove(arenaKey);
        List<CloneCallback> waiting = callbacks.remove(arenaKey);
        if (waiting == null) {
            return;
        }

        for (CloneCallback callback : waiting) {
            invoke(callback, success);
        }
    }

    private void invoke(CloneCallback callback, boolean success) {
        if (callback == null) {
            return;
        }
        try {
            callback.complete(success);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Falha em callback de clone: " + exception.getMessage());
        }
    }

    public void destroyClone(Arena arena) {
        if (arena == null) {
            return;
        }

        String arenaKey = key(arena);
        boolean cleanupCancelled = plugin.getEntityCleanupManager().cancelCleanup(arena);
        if (cloningArenas.contains(arenaKey)) {
            cancelledArenas.add(arenaKey);
            List<CloneCallback> waiting = callbacks.remove(arenaKey);
            if (waiting != null) {
                for (CloneCallback callback : waiting) {
                    invoke(callback, false);
                }
            }
            if (cleanupCancelled) {
                cloningArenas.remove(arenaKey);
                pendingCloneFolders.remove(arenaKey);
                cancelledArenas.remove(arenaKey);
            }
        }

        if (!arena.hasActiveWorld()) {
            return;
        }

        String cloneName = arena.getActiveWorldName();
        arena.clearActiveWorld();
        plugin.getArenaManager().invalidateWorldIndex();

        World cloneWorld = Bukkit.getWorld(cloneName);
        if (cloneWorld != null) {
            evacuatePlayers(cloneWorld);
            Bukkit.unloadWorld(cloneWorld, false);
        }

        scheduleDelete(new File(getServerRoot(), cloneName));
    }

    private void scheduleTemplateUnload(final Arena arena) {
        if (!plugin.getConfig().getBoolean("performance.world-cloning.unload-template-after-clone", true)) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                unloadUnusedTemplateWorld(arena);
            }
        }, 40L);
    }

    private void unloadUnusedTemplateWorld(Arena arena) {
        if (arena == null || arena.getWorldName() == null) {
            return;
        }

        if (plugin.getEntityCleanupManager().isCleanupRunning(arena.getWorldName())) {
            scheduleTemplateUnload(arena);
            return;
        }

        World templateWorld = Bukkit.getWorld(arena.getWorldName());
        if (templateWorld == null || !templateWorld.getPlayers().isEmpty()) {
            return;
        }

        List<World> loadedWorlds = Bukkit.getWorlds();
        if (!loadedWorlds.isEmpty() && loadedWorlds.get(0).equals(templateWorld)) {
            return;
        }

        Location lobby = plugin.getLobbyManager().getMainWorldSpawn();
        if (lobby != null && templateWorld.equals(lobby.getWorld())) {
            return;
        }

        // Salva a limpeza única dos ArmorStands no mapa-base antes de
        // descarregá-lo, para que partidas futuras não precisem removê-los de
        // novo após a cópia.
        Bukkit.unloadWorld(templateWorld, true);
    }

    public void shutdown() {
        shuttingDown = true;
        List<CloneCallback> pendingCallbacks = new ArrayList<CloneCallback>();
        for (List<CloneCallback> waiting : callbacks.values()) {
            pendingCallbacks.addAll(waiting);
        }
        callbacks.clear();
        cloningArenas.clear();
        cancelledArenas.clear();
        pendingCloneFolders.clear();
        for (CloneCallback callback : pendingCallbacks) {
            invoke(callback, false);
        }
        fileExecutor.shutdownNow();
    }

    private void configureCloneWorld(World cloneWorld) {
        cloneWorld.setAutoSave(false);
        try {
            cloneWorld.setKeepSpawnInMemory(false);
        } catch (NoSuchMethodError ignored) {
        }

        if (plugin.getConfig().getBoolean("performance.world-cloning.disable-natural-mob-spawning", true)) {
            try {
                cloneWorld.setGameRuleValue("doMobSpawning", "false");
            } catch (Exception ignored) {
            }
        }
        if (plugin.getConfig().getBoolean("settings.arena-always-day", true)) {
            try {
                cloneWorld.setGameRuleValue("doDaylightCycle", "false");
            } catch (Exception ignored) {
            }
            cloneWorld.setTime(1000L);
        }
        if (plugin.getConfig().getBoolean("settings.arena-clear-weather", true)) {
            try {
                cloneWorld.setGameRuleValue("doWeatherCycle", "false");
            } catch (Exception ignored) {
            }
            cloneWorld.setStorm(false);
            cloneWorld.setThundering(false);
            cloneWorld.setWeatherDuration(Integer.MAX_VALUE);
            cloneWorld.setThunderDuration(Integer.MAX_VALUE);
        }
    }

    private void evacuatePlayers(World cloneWorld) {
        Location safeLocation = plugin.getLobbyManager().getMainWorldSpawn();
        if (safeLocation == null) {
            for (World world : Bukkit.getWorlds()) {
                if (world != null && !world.getName().equalsIgnoreCase(cloneWorld.getName())) {
                    safeLocation = world.getSpawnLocation();
                    break;
                }
            }
        }
        if (safeLocation == null) {
            return;
        }

        for (Player player : new ArrayList<Player>(cloneWorld.getPlayers())) {
            if (player != null && player.isOnline()) {
                player.closeInventory();
                player.teleport(safeLocation);
            }
        }
    }

    private File resolveTemplateFolder(Arena arena) {
        File serverRoot = getServerRoot();
        if (serverRoot == null || arena.getWorldName() == null) {
            return null;
        }

        World loaded = Bukkit.getWorld(arena.getWorldName());
        File candidate = loaded == null ? new File(serverRoot, arena.getWorldName()) : loaded.getWorldFolder();
        try {
            File canonicalRoot = serverRoot.getCanonicalFile();
            File canonicalCandidate = candidate.getCanonicalFile();
            if (!canonicalCandidate.toPath().startsWith(canonicalRoot.toPath())) {
                return null;
            }
            return canonicalCandidate;
        } catch (IOException exception) {
            return null;
        }
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

    private String key(Arena arena) {
        return arena.getName().toLowerCase();
    }

    private void submitFileTask(Runnable runnable) {
        if (runnable == null || shuttingDown || fileExecutor.isShutdown()) {
            return;
        }
        try {
            fileExecutor.execute(runnable);
        } catch (RuntimeException ignored) {
        }
    }

    private void scheduleDelete(final File folder) {
        if (folder == null) {
            return;
        }
        submitFileTask(new Runnable() {
            @Override
            public void run() {
                deleteDirectory(folder);
            }
        });
    }

    private void copyWorldFolder(File source, File target) throws IOException {
        if (Files.isSymbolicLink(source.toPath())) {
            return;
        }
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) {
                throw new IOException("Nao foi possivel criar " + target.getAbsolutePath());
            }
            File[] children = source.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                if (!shouldIgnore(child.getName())) {
                    copyWorldFolder(child, new File(target, child.getName()));
                }
            }
            return;
        }

        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private boolean shouldIgnore(String name) {
        return "uid.dat".equalsIgnoreCase(name)
            || "session.lock".equalsIgnoreCase(name)
            || "session.dat".equalsIgnoreCase(name)
            || "playerdata".equalsIgnoreCase(name)
            || "players".equalsIgnoreCase(name)
            || "stats".equalsIgnoreCase(name)
            || "advancements".equalsIgnoreCase(name);
    }

    private boolean deleteDirectory(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        if (Files.isSymbolicLink(file.toPath())) {
            try {
                return Files.deleteIfExists(file.toPath());
            } catch (IOException exception) {
                return false;
            }
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

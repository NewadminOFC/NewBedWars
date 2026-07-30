package n.plugins.newbedwars.manager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import n.plugins.newbedwars.NewBedWars;
import n.plugins.newbedwars.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Removes persisted ArmorStand holograms without processing thousands of
 * entities in a single server tick.
 */
public class EntityCleanupManager implements Listener {

    public interface Completion {
        void complete();
    }

    private static final class CleanupJob {
        private final String worldName;
        private final Queue<Entity> entities = new ArrayDeque<Entity>();
        private final List<Completion> completions = new ArrayList<Completion>();
        private int taskId = -1;
        private int removed;

        private CleanupJob(String worldName) {
            this.worldName = worldName;
        }
    }

    private final NewBedWars plugin;
    private final Map<String, CleanupJob> jobs = new HashMap<String, CleanupJob>();

    public EntityCleanupManager(NewBedWars plugin) {
        this.plugin = plugin;
    }

    public void cleanupArenaWorld(Arena arena, Completion completion) {
        if (arena == null || !isCleanupEnabled()) {
            invoke(completion);
            return;
        }

        World world = arena.getActiveWorld();
        if (world == null) {
            invoke(completion);
            return;
        }

        queueCleanup(world, world.getEntities().toArray(new Entity[0]), completion);
    }

    public void cleanupTemplateWorld(Arena arena) {
        if (arena == null
            || !plugin.getConfig().getBoolean("performance.entity-cleanup.clean-template-holograms", true)) {
            return;
        }

        World world = Bukkit.getWorld(arena.getWorldName());
        if (world != null) {
            queueCleanup(world, world.getEntities().toArray(new Entity[0]), null);
        }
    }

    public boolean cancelCleanup(Arena arena) {
        if (arena == null) {
            return false;
        }
        String worldName = arena.getActiveWorldName();
        return worldName != null && cancelCleanup(worldName);
    }

    public boolean isCleanupRunning(String worldName) {
        return worldName != null && jobs.containsKey(worldName.toLowerCase());
    }

    public void shutdown() {
        for (CleanupJob job : new ArrayList<CleanupJob>(jobs.values())) {
            if (job.taskId != -1) {
                Bukkit.getScheduler().cancelTask(job.taskId);
            }
        }
        jobs.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!isCleanupEnabled()) {
            return;
        }

        Arena arena = plugin.getArenaManager().getArenaByWorld(event.getWorld());
        if (arena == null || !arena.isRuntimeInstance()) {
            return;
        }

        queueCleanup(event.getWorld(), event.getChunk().getEntities(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(final EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof ArmorStand) || !isCleanupEnabled()) {
            return;
        }

        final ArmorStand stand = (ArmorStand) event.getEntity();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!stand.isValid() || !isHologram(stand)) {
                    return;
                }
                Arena arena = plugin.getArenaManager().getArenaByWorld(stand.getWorld());
                if (arena != null && arena.isRuntimeInstance()) {
                    stand.remove();
                }
            }
        });
    }

    private void queueCleanup(World world, Entity[] entities, Completion completion) {
        if (world == null) {
            invoke(completion);
            return;
        }

        final String key = world.getName().toLowerCase();
        CleanupJob existing = jobs.get(key);
        if (existing != null) {
            addEntities(existing, entities);
            if (completion != null) {
                existing.completions.add(completion);
            }
            return;
        }

        final CleanupJob job = new CleanupJob(world.getName());
        addEntities(job, entities);
        if (completion != null) {
            job.completions.add(completion);
        }

        if (job.entities.isEmpty()) {
            finish(job);
            return;
        }

        jobs.put(key, job);
        job.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                process(job);
            }
        }, 1L, 1L);
    }

    private void process(CleanupJob job) {
        int batchSize = Math.max(25, Math.min(1000,
            plugin.getConfig().getInt("performance.entity-cleanup.entities-per-tick", 250)));
        int processed = 0;

        while (processed < batchSize && !job.entities.isEmpty()) {
            Entity entity = job.entities.poll();
            if (entity instanceof ArmorStand && entity.isValid() && isHologram((ArmorStand) entity)) {
                entity.remove();
                job.removed++;
            }
            processed++;
        }

        if (job.entities.isEmpty()) {
            finish(job);
        }
    }

    private void finish(CleanupJob job) {
        CleanupJob active = jobs.remove(job.worldName.toLowerCase());
        if (active != null && active.taskId != -1) {
            Bukkit.getScheduler().cancelTask(active.taskId);
        }

        if (job.removed > 0 && plugin.getConfig().getBoolean("performance.entity-cleanup.log-removals", true)) {
            plugin.getLogger().info("Removidos " + job.removed + " hologramas persistidos do mundo " + job.worldName + ".");
        }

        for (Completion completion : new ArrayList<Completion>(job.completions)) {
            invoke(completion);
        }
        job.completions.clear();
    }

    private boolean cancelCleanup(String worldName) {
        CleanupJob job = jobs.remove(worldName.toLowerCase());
        if (job == null) {
            return false;
        }
        if (job.taskId != -1) {
            Bukkit.getScheduler().cancelTask(job.taskId);
        }
        job.completions.clear();
        job.entities.clear();
        return true;
    }

    private void addEntities(CleanupJob job, Entity[] entities) {
        if (entities == null) {
            return;
        }
        for (Entity entity : entities) {
            if (entity instanceof ArmorStand) {
                job.entities.offer(entity);
            }
        }
    }

    private boolean isHologram(ArmorStand stand) {
        if (stand == null) {
            return false;
        }
        String customName = stand.getCustomName();
        return !stand.isVisible()
            && (stand.isCustomNameVisible()
                || stand.isSmall()
                || (customName != null && !customName.trim().isEmpty()));
    }

    private boolean isCleanupEnabled() {
        return plugin.getConfig().getBoolean("performance.entity-cleanup.remove-match-holograms", true);
    }

    private void invoke(Completion completion) {
        if (completion == null) {
            return;
        }
        try {
            completion.complete();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Falha ao concluir a limpeza de entidades: " + exception.getMessage());
        }
    }
}

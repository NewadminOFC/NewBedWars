package n.plugins.newbedwars.npc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;

public class NpcHologram {

    private final List<UUID> lineIds;

    public NpcHologram() {
        this.lineIds = new ArrayList<UUID>();
    }

    public List<ArmorStand> getLines() {
        List<ArmorStand> stands = new ArrayList<ArmorStand>();
        Iterator<UUID> iterator = lineIds.iterator();
        while (iterator.hasNext()) {
            UUID uniqueId = iterator.next();
            Entity entity = findEntity(uniqueId);
            if (!(entity instanceof ArmorStand)) {
                iterator.remove();
                continue;
            }
            stands.add((ArmorStand) entity);
        }
        return stands;
    }

    public void addLine(ArmorStand stand) {
        lineIds.add(stand.getUniqueId());
    }

    public void clear() {
        for (ArmorStand stand : getLines()) {
            stand.remove();
        }
        lineIds.clear();
    }

    public void teleportLine(int index, Location location) {
        List<ArmorStand> stands = getLines();
        if (index < 0 || index >= stands.size()) {
            return;
        }
        stands.get(index).teleport(location);
    }

    public void setLineName(int index, String text) {
        List<ArmorStand> stands = getLines();
        if (index < 0 || index >= stands.size()) {
            return;
        }
        stands.get(index).setCustomName(text);
    }

    public int size() {
        return getLines().size();
    }

    private Entity findEntity(UUID uniqueId) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getUniqueId().equals(uniqueId)) {
                    return entity;
                }
            }
        }
        return null;
    }
}

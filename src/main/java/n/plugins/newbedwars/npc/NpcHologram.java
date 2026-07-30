package n.plugins.newbedwars.npc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;

public class NpcHologram {

    private final List<ArmorStand> lines;

    public NpcHologram() {
        this.lines = new ArrayList<ArmorStand>();
    }

    public List<ArmorStand> getLines() {
        Iterator<ArmorStand> iterator = lines.iterator();
        while (iterator.hasNext()) {
            ArmorStand stand = iterator.next();
            if (stand == null || stand.isDead()) {
                iterator.remove();
            }
        }
        return lines;
    }

    public void addLine(ArmorStand stand) {
        lines.add(stand);
    }

    public void clear() {
        for (ArmorStand stand : lines) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
        lines.clear();
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
}

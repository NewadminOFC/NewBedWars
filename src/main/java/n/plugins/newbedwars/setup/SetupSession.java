package n.plugins.newbedwars.setup;

import java.util.UUID;
import n.plugins.newbedwars.arena.TeamColor;
import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;

public class SetupSession {

    private final UUID playerId;
    private final String arenaName;
    private final ItemStack[] originalContents;
    private final ItemStack[] originalArmor;
    private final GameMode originalGameMode;
    private boolean unlockedMainMenu;
    private boolean buildModeEnabled;
    private TeamColor selectedTeam;
    private SetupPointAction pendingPointAction;
    private SetupRegionAction pendingRegionAction;
    private TeamColor pendingRegionTeam;
    private Location selectionPos1;
    private Location selectionPos2;

    public SetupSession(UUID playerId, String arenaName, ItemStack[] originalContents, ItemStack[] originalArmor, GameMode originalGameMode) {
        this.playerId = playerId;
        this.arenaName = arenaName;
        this.originalContents = originalContents;
        this.originalArmor = originalArmor;
        this.originalGameMode = originalGameMode;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getArenaName() {
        return arenaName;
    }

    public ItemStack[] getOriginalContents() {
        return originalContents;
    }

    public ItemStack[] getOriginalArmor() {
        return originalArmor;
    }

    public GameMode getOriginalGameMode() {
        return originalGameMode;
    }

    public boolean isUnlockedMainMenu() {
        return unlockedMainMenu;
    }

    public void setUnlockedMainMenu(boolean unlockedMainMenu) {
        this.unlockedMainMenu = unlockedMainMenu;
    }

    public boolean isBuildModeEnabled() {
        return buildModeEnabled;
    }

    public void setBuildModeEnabled(boolean buildModeEnabled) {
        this.buildModeEnabled = buildModeEnabled;
    }

    public TeamColor getSelectedTeam() {
        return selectedTeam;
    }

    public void setSelectedTeam(TeamColor selectedTeam) {
        this.selectedTeam = selectedTeam;
    }

    public SetupPointAction getPendingPointAction() {
        return pendingPointAction;
    }

    public void setPendingPointAction(SetupPointAction pendingPointAction) {
        this.pendingPointAction = pendingPointAction;
    }

    public SetupRegionAction getPendingRegionAction() {
        return pendingRegionAction;
    }

    public void setPendingRegionAction(SetupRegionAction pendingRegionAction) {
        this.pendingRegionAction = pendingRegionAction;
    }

    public TeamColor getPendingRegionTeam() {
        return pendingRegionTeam;
    }

    public void setPendingRegionTeam(TeamColor pendingRegionTeam) {
        this.pendingRegionTeam = pendingRegionTeam;
    }

    public Location getSelectionPos1() {
        return selectionPos1 == null ? null : selectionPos1.clone();
    }

    public void setSelectionPos1(Location selectionPos1) {
        this.selectionPos1 = selectionPos1 == null ? null : selectionPos1.clone();
    }

    public Location getSelectionPos2() {
        return selectionPos2 == null ? null : selectionPos2.clone();
    }

    public void setSelectionPos2(Location selectionPos2) {
        this.selectionPos2 = selectionPos2 == null ? null : selectionPos2.clone();
    }

    public void clearSelection() {
        this.selectionPos1 = null;
        this.selectionPos2 = null;
    }

    public void clearPendingActions() {
        this.pendingPointAction = null;
        this.pendingRegionAction = null;
        this.pendingRegionTeam = null;
        clearSelection();
    }
}

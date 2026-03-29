package n.plugins.newbedwars;

import n.plugins.newbedwars.command.BedWarsCommand;
import n.plugins.newbedwars.command.LobbyCommand;
import n.plugins.newbedwars.listener.CombatListener;
import n.plugins.newbedwars.listener.GameBlockListener;
import n.plugins.newbedwars.listener.GameItemListener;
import n.plugins.newbedwars.listener.GamePlayerListener;
import n.plugins.newbedwars.listener.InventoryListener;
import n.plugins.newbedwars.listener.NpcListener;
import n.plugins.newbedwars.listener.SetupInteractListener;
import n.plugins.newbedwars.manager.ArenaManager;
import n.plugins.newbedwars.manager.GameManager;
import n.plugins.newbedwars.manager.GeneratorManager;
import n.plugins.newbedwars.manager.HologramManager;
import n.plugins.newbedwars.manager.LobbyManager;
import n.plugins.newbedwars.manager.MenuManager;
import n.plugins.newbedwars.manager.MessageManager;
import n.plugins.newbedwars.manager.NpcManager;
import n.plugins.newbedwars.manager.ScoreboardManager;
import n.plugins.newbedwars.manager.ShopManager;
import n.plugins.newbedwars.manager.SetupManager;
import n.plugins.newbedwars.manager.TeamManager;
import n.plugins.newbedwars.manager.WorldCloneManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class NewBedWars extends JavaPlugin {

    private MessageManager messageManager;
    private ArenaManager arenaManager;
    private TeamManager teamManager;
    private SetupManager setupManager;
    private MenuManager menuManager;
    private GameManager gameManager;
    private GeneratorManager generatorManager;
    private ScoreboardManager scoreboardManager;
    private ShopManager shopManager;
    private HologramManager hologramManager;
    private NpcManager npcManager;
    private LobbyManager lobbyManager;
    private WorldCloneManager worldCloneManager;

    @Override
    public void onEnable() {
        // Carrega os arquivos base antes de inicializar os managers do plugin.
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        saveResourceIfAbsent("messages.yml");
        ensureArenaFolder();

        this.messageManager = new MessageManager(this);
        this.teamManager = new TeamManager(this);
        this.arenaManager = new ArenaManager(this);
        this.setupManager = new SetupManager(this);
        this.menuManager = new MenuManager(this);
        this.gameManager = new GameManager(this);
        this.generatorManager = new GeneratorManager(this);
        this.scoreboardManager = new ScoreboardManager(this);
        this.shopManager = new ShopManager(this);
        this.hologramManager = new HologramManager(this);
        this.npcManager = new NpcManager(this);
        this.lobbyManager = new LobbyManager(this);
        this.worldCloneManager = new WorldCloneManager(this);

        this.messageManager.reload();
        this.lobbyManager.applyConfiguredLobbyWorldRules();
        this.worldCloneManager.startupCleanup();
        this.arenaManager.loadArenas();
        registerCommands();
        registerListeners();

        this.gameManager.start();
        this.generatorManager.start();
        this.scoreboardManager.start();
        this.npcManager.start();

        printBanner();
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }

        if (scoreboardManager != null) {
            scoreboardManager.shutdown();
        }

        if (generatorManager != null) {
            generatorManager.shutdown();
        }

        if (npcManager != null) {
            npcManager.shutdown();
        }

        if (hologramManager != null) {
            hologramManager.shutdown();
        }

        if (setupManager != null) {
            setupManager.shutdown();
        }

        if (worldCloneManager != null) {
            worldCloneManager.startupCleanup();
        }

        if (arenaManager != null) {
            arenaManager.saveAllArenas();
        }
    }

    private void registerCommands() {
        BedWarsCommand bedWarsCommand = new BedWarsCommand(this);
        getCommand("bw").setExecutor(bedWarsCommand);
        getCommand("bw").setTabCompleter(bedWarsCommand);
        getCommand("lobby").setExecutor(new LobbyCommand(this));
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new InventoryListener(this), this);
        Bukkit.getPluginManager().registerEvents(new SetupInteractListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GameBlockListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GameItemListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GamePlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new NpcListener(this), this);
    }

    private void saveResourceIfAbsent(String path) {
        if (!new java.io.File(getDataFolder(), path).exists()) {
            saveResource(path, false);
        }
    }

    private void ensureArenaFolder() {
        java.io.File folder = new java.io.File(getDataFolder(), "arenas");
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    private void printBanner() {
        Bukkit.getConsoleSender().sendMessage("§b _   _                 ____          _ __        __             ");
        Bukkit.getConsoleSender().sendMessage("§b| \\ | | _____      __ | __ )  ___  __| |\\ \\      / /_ _ _ __ ___ ");
        Bukkit.getConsoleSender().sendMessage("§b|  \\| |/ _ \\ \\ /\\ / / |  _ \\ / _ \\/ _` | \\ \\ /\\ / / _` | '__/ __|");
        Bukkit.getConsoleSender().sendMessage("§b| |\\  |  __/\\ V  V /  | |_) |  __/ (_| |  \\ V  V / (_| | |  \\__ \\");
        Bukkit.getConsoleSender().sendMessage("§b|_| \\_|\\___| \\_/\\_/   |____/ \\___|\\__,_|   \\_/\\_/ \\__,_|_|  |___/");
        Bukkit.getConsoleSender().sendMessage("§aNewBedWars 1.8.8 carregado com sucesso.");
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public SetupManager getSetupManager() {
        return setupManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public GeneratorManager getGeneratorManager() {
        return generatorManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public NpcManager getNpcManager() {
        return npcManager;
    }

    public LobbyManager getLobbyManager() {
        return lobbyManager;
    }

    public WorldCloneManager getWorldCloneManager() {
        return worldCloneManager;
    }
}

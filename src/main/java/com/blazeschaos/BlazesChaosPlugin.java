package com.blazeschaos;

import com.blazeschaos.arena.ArenaManager;
import com.blazeschaos.command.BlazeChaosCommand;
import com.blazeschaos.config.ConfigManager;
import com.blazeschaos.database.DatabaseManager;
import com.blazeschaos.event.ChaosEventManager;
import com.blazeschaos.game.GameManager;
import com.blazeschaos.lang.LanguageManager;
import com.blazeschaos.listener.GameListener;
import com.blazeschaos.lobby.LobbyManager;
import com.blazeschaos.placeholder.BlazeChaosExpansion;
import com.blazeschaos.scoreboard.ScoreboardManager;
import com.blazeschaos.setup.SetupModeManager;
import com.blazeschaos.tablist.TablistManager;
import com.blazeschaos.vault.VaultHook;
import com.blazeschaos.world.WorldResetManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class BlazesChaosPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private ArenaManager arenaManager;
    private GameManager gameManager;
    private ChaosEventManager eventManager;
    private WorldResetManager worldResetManager;
    private LobbyManager lobbyManager;
    private ScoreboardManager scoreboardManager;
    private TablistManager tablistManager;
    private SetupModeManager setupModeManager;
    private VaultHook vaultHook;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        configManager.loadAll();

        this.databaseManager = new DatabaseManager(this);
        databaseManager.connect();

        this.vaultHook = new VaultHook(this);
        vaultHook.hook();

        this.arenaManager = new ArenaManager(this);
        this.eventManager = new ChaosEventManager(this);
        this.worldResetManager = new WorldResetManager(this);
        this.lobbyManager = new LobbyManager(this);
        this.scoreboardManager = new ScoreboardManager(this);
        this.tablistManager = new TablistManager(this);
        this.gameManager = new GameManager(this);
        this.setupModeManager = new SetupModeManager(this);

        BlazeChaosCommand command = new BlazeChaosCommand(this);
        PluginCommand pluginCommand = getCommand("bc");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        } else {
            getLogger().severe("Command 'bc' missing from plugin.yml");
        }

        Bukkit.getPluginManager().registerEvents(new GameListener(this), this);
        Bukkit.getPluginManager().registerEvents(setupModeManager, this);
        scoreboardManager.start();
        tablistManager.start();

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new BlazeChaosExpansion(this).register();
            getLogger().info("PlaceholderAPI hooked.");
        }

        getLogger().info("Blaze's Chaos v" + getPluginMeta().getVersion()
                + " enabled (lang=" + lang().languageCode() + ").");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
        if (scoreboardManager != null) {
            scoreboardManager.stop();
        }
        if (tablistManager != null) {
            tablistManager.stop();
        }
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
        getLogger().info("Blaze's Chaos disabled.");
    }

    public void reloadPlugin() {
        configManager.reloadAll();
        eventManager.reload();
        worldResetManager.reloadSkip();
        lobbyManager.load();
        arenaManager.load();
        scoreboardManager.start();
        tablistManager.start();
    }

    public @NotNull ConfigManager configs() {
        return configManager;
    }

    public @NotNull LanguageManager lang() {
        return configManager.lang();
    }

    public @NotNull DatabaseManager database() {
        return databaseManager;
    }

    public @NotNull ArenaManager arenaManager() {
        return arenaManager;
    }

    public @NotNull GameManager gameManager() {
        return gameManager;
    }

    public @NotNull ChaosEventManager eventManager() {
        return eventManager;
    }

    public @NotNull WorldResetManager worldResetManager() {
        return worldResetManager;
    }

    public @NotNull LobbyManager lobbyManager() {
        return lobbyManager;
    }

    public @NotNull ScoreboardManager scoreboardManager() {
        return scoreboardManager;
    }

    public @NotNull TablistManager tablistManager() {
        return tablistManager;
    }

    public @NotNull SetupModeManager setupMode() {
        return setupModeManager;
    }

    public @NotNull VaultHook vaultHook() {
        return vaultHook;
    }
}

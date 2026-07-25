package com.blazeschaos;

import com.blazeschaos.arena.ArenaManager;
import com.blazeschaos.coins.CoinsManager;
import com.blazeschaos.command.BlazeChaosCommand;
import com.blazeschaos.config.ConfigManager;
import com.blazeschaos.cosmetics.CosmeticTrailManager;
import com.blazeschaos.database.DatabaseManager;
import com.blazeschaos.event.ChaosEventManager;
import com.blazeschaos.event.EventDifficultyManager;
import com.blazeschaos.game.GameManager;
import com.blazeschaos.game.SurvivalObjectiveService;
import com.blazeschaos.lang.LanguageManager;
import com.blazeschaos.listener.GameListener;
import com.blazeschaos.listener.LobbyProtectionListener;
import com.blazeschaos.lobby.LobbyManager;
import com.blazeschaos.lobby.SpawnConfirmListener;
import com.blazeschaos.loot.LootManager;
import com.blazeschaos.loot.LootRarityManager;
import com.blazeschaos.npc.NpcListener;
import com.blazeschaos.npc.NpcManager;
import com.blazeschaos.npc.gui.NpcGui;
import com.blazeschaos.npc.gui.SurvivalObjectiveGui;
import com.blazeschaos.placeholder.BlazeChaosAnimationExpansion;
import com.blazeschaos.placeholder.BlazeChaosExpansion;
import com.blazeschaos.scoreboard.AnimationManager;
import com.blazeschaos.scoreboard.ScoreboardManager;
import com.blazeschaos.setup.SetupModeManager;
import com.blazeschaos.shop.ShopManager;
import com.blazeschaos.tablist.TablistManager;
import com.blazeschaos.util.PlaceholderService;
import com.blazeschaos.vault.VaultHook;
import com.blazeschaos.world.PassiveAnimalManager;
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
    private AnimationManager animationManager;
    private SetupModeManager setupModeManager;
    private VaultHook vaultHook;
    private CoinsManager coinsManager;
    private ShopManager shopManager;
    private CosmeticTrailManager cosmeticTrailManager;
    private LootManager lootManager;
    private EventDifficultyManager difficultyManager;
    private LootRarityManager lootRarityManager;
    private PlaceholderService placeholderService;
    private PassiveAnimalManager passiveAnimalManager;
    private SurvivalObjectiveService survivalObjectiveService;
    private SpawnConfirmListener spawnConfirmListener;
    private BlazeChaosExpansion placeholderExpansion;
    private BlazeChaosAnimationExpansion animationExpansion;
    private NpcManager npcManager;
    private NpcGui npcGui;
    private SurvivalObjectiveGui survivalObjectiveGui;
    private com.blazeschaos.npc.NpcCommandHandler npcCommands;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        configManager.loadAll();

        this.databaseManager = new DatabaseManager(this);
        databaseManager.connect();

        this.vaultHook = new VaultHook(this);
        vaultHook.hook();

        this.placeholderService = new PlaceholderService(this);
        this.coinsManager = new CoinsManager(this);
        this.difficultyManager = new EventDifficultyManager(this);
        this.lootRarityManager = new LootRarityManager(this);
        this.lootManager = new LootManager(this);
        this.shopManager = new ShopManager(this);
        this.cosmeticTrailManager = new CosmeticTrailManager(this);

        this.arenaManager = new ArenaManager(this);
        this.eventManager = new ChaosEventManager(this);
        this.worldResetManager = new WorldResetManager(this);
        this.lobbyManager = new LobbyManager(this);
        this.animationManager = new AnimationManager(this);
        this.scoreboardManager = new ScoreboardManager(this);
        this.tablistManager = new TablistManager(this);
        this.passiveAnimalManager = new PassiveAnimalManager(this);
        this.survivalObjectiveService = new SurvivalObjectiveService(this);
        this.gameManager = new GameManager(this);
        this.setupModeManager = new SetupModeManager(this);
        this.spawnConfirmListener = new SpawnConfirmListener(this);
        this.npcGui = new NpcGui(this);
        this.survivalObjectiveGui = new SurvivalObjectiveGui(this);
        this.npcManager = new NpcManager(this);
        this.npcCommands = new com.blazeschaos.npc.NpcCommandHandler(this);

        BlazeChaosCommand command = new BlazeChaosCommand(this);
        PluginCommand pluginCommand = getCommand("bc");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        } else {
            getLogger().severe("Command 'bc' missing from plugin.yml");
        }

        Bukkit.getPluginManager().registerEvents(new GameListener(this), this);
        Bukkit.getPluginManager().registerEvents(new LobbyProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(setupModeManager, this);
        Bukkit.getPluginManager().registerEvents(shopManager, this);
        Bukkit.getPluginManager().registerEvents(spawnConfirmListener, this);
        Bukkit.getPluginManager().registerEvents(new NpcListener(this), this);
        Bukkit.getPluginManager().registerEvents(npcGui, this);
        Bukkit.getPluginManager().registerEvents(survivalObjectiveGui, this);
        survivalObjectiveService.register();

        animationManager.start();
        scoreboardManager.start();
        tablistManager.start();
        passiveAnimalManager.start();
        cosmeticTrailManager.start();
        npcManager.scheduleStartupSkinRefresh();

        hookPlaceholderAPI();

        getLogger().info("Blaze's Chaos v" + getPluginMeta().getVersion()
                + " enabled (lang=" + lang().languageCode() + ").");
    }

    @Override
    public void onDisable() {
        if (cosmeticTrailManager != null) {
            cosmeticTrailManager.stop();
        }
        if (npcManager != null) {
            npcManager.stop();
        }
        if (gameManager != null) {
            gameManager.shutdown();
        }
        if (animationManager != null) {
            animationManager.stop();
        }
        if (scoreboardManager != null) {
            scoreboardManager.stop();
        }
        if (tablistManager != null) {
            tablistManager.stop();
        }
        if (passiveAnimalManager != null) {
            passiveAnimalManager.stop();
        }
        unregisterPlaceholders();
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
        lootManager.reload();
        difficultyManager.reload();
        lootRarityManager.reload();
        animationManager.reload();
        animationManager.start();
        npcManager.reload();
        scoreboardManager.start();
        tablistManager.start();
        passiveAnimalManager.start();
        cosmeticTrailManager.reload();
        cosmeticTrailManager.start();
        survivalObjectiveService.registerRecipe();
        hookPlaceholderAPI();
    }

    private void hookPlaceholderAPI() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        unregisterPlaceholders();
        placeholderExpansion = new BlazeChaosExpansion(this);
        placeholderExpansion.register();
        animationExpansion = new BlazeChaosAnimationExpansion(this);
        animationExpansion.register();
        getLogger().info("PlaceholderAPI hooked (blazechaos + blazechaosanimation).");
    }

    private void unregisterPlaceholders() {
        if (placeholderExpansion != null) {
            try {
                placeholderExpansion.unregister();
            } catch (Throwable ignored) {
            }
            placeholderExpansion = null;
        }
        if (animationExpansion != null) {
            try {
                animationExpansion.unregister();
            } catch (Throwable ignored) {
            }
            animationExpansion = null;
        }
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

    public @NotNull AnimationManager animationManager() {
        return animationManager;
    }

    public @NotNull SetupModeManager setupMode() {
        return setupModeManager;
    }

    public @NotNull VaultHook vaultHook() {
        return vaultHook;
    }

    public @NotNull CoinsManager coinsManager() {
        return coinsManager;
    }

    public @NotNull ShopManager shopManager() {
        return shopManager;
    }

    public @NotNull CosmeticTrailManager cosmetics() {
        return cosmeticTrailManager;
    }

    public @NotNull LootManager lootManager() {
        return lootManager;
    }

    public @NotNull EventDifficultyManager difficultyManager() {
        return difficultyManager;
    }

    public @NotNull LootRarityManager lootRarityManager() {
        return lootRarityManager;
    }

    public @NotNull PlaceholderService placeholders() {
        return placeholderService;
    }

    public @NotNull PassiveAnimalManager passiveAnimals() {
        return passiveAnimalManager;
    }

    public @NotNull SurvivalObjectiveService survivalObjective() {
        return survivalObjectiveService;
    }

    public @NotNull SpawnConfirmListener spawnConfirm() {
        return spawnConfirmListener;
    }

    public @NotNull NpcManager npcManager() {
        return npcManager;
    }

    public @NotNull NpcGui npcGui() {
        return npcGui;
    }

    public @NotNull SurvivalObjectiveGui survivalObjectiveGui() {
        return survivalObjectiveGui;
    }

    public @NotNull com.blazeschaos.npc.NpcCommandHandler npcCommands() {
        return npcCommands;
    }
}

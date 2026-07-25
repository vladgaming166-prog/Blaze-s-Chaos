package com.blazeschaos.game;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.util.ColorUtil;
import com.blazeschaos.util.ItemsAdderHook;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Solo Survival Chaos Shard: loot, craft, world drop, obtain = instant win.
 */
public final class SurvivalObjectiveService implements Listener {

    private final BlazesChaosPlugin plugin;
    private final NamespacedKey shardKey;
    private final NamespacedKey recipeKey;
    private boolean recipeRegistered;

    public SurvivalObjectiveService(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        this.shardKey = new NamespacedKey(plugin, "chaos_shard");
        this.recipeKey = new NamespacedKey(plugin, "chaos_shard_recipe");
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Delay one tick so Paper recipe manager is ready after enable / reload
        Bukkit.getScheduler().runTask(plugin, this::registerRecipe);
    }

    /**
     * Solo Survival win timer (seconds). Prefers {@code win-time-seconds},
     * falls back to legacy {@code survive-seconds}.
     */
    public int winTimeSeconds() {
        int win = plugin.getConfig().getInt("solo-survival.win-time-seconds", -1);
        if (win > 0) {
            return win;
        }
        return Math.max(1, plugin.getConfig().getInt("solo-survival.survive-seconds", 1200));
    }

    public void setWinTimeSeconds(int seconds) {
        int value = Math.max(1, seconds);
        plugin.getConfig().set("solo-survival.win-time-seconds", value);
        plugin.getConfig().set("solo-survival.survive-seconds", value);
        plugin.saveConfig();
    }

    public boolean isCraftingObtainEnabled() {
        if (plugin.getConfig().contains("solo-survival.shard.obtain.crafting")) {
            return plugin.getConfig().getBoolean("solo-survival.shard.obtain.crafting", true);
        }
        return plugin.getConfig().getBoolean("solo-survival.crafting.enabled", true);
    }

    public boolean isMiningObtainEnabled() {
        return plugin.getConfig().getBoolean("solo-survival.shard.obtain.mining", true);
    }

    public boolean isLootObtainEnabled() {
        if (plugin.getConfig().contains("solo-survival.shard.obtain.loot-chests")) {
            return plugin.getConfig().getBoolean("solo-survival.shard.obtain.loot-chests", true);
        }
        return plugin.getConfig().getBoolean("solo-survival.loot.enabled", true);
    }

    /**
     * Registers the Chaos Shard shaped recipe for Paper 1.21+.
     * Safe across /reload and restarts — removes previous key first, no duplicates.
     */
    public void registerRecipe() {
        unregisterRecipe();
        if (!isCraftingObtainEnabled()) {
            return;
        }
        ItemStack result = createChaosShard();
        if (result.getType().isAir() || result.getAmount() < 1) {
            result = new ItemStack(Material.AMETHYST_SHARD);
            ItemMeta meta = result.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(shardKey, PersistentDataType.BYTE, (byte) 1);
                result.setItemMeta(meta);
            }
        }
        result.setAmount(1);

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, result);
        List<String> shape = plugin.getConfig().getStringList("solo-survival.crafting.shape");
        if (shape.isEmpty()) {
            shape = List.of("ICI", "CEC", "ICI");
        }
        recipe.shape(shape.toArray(new String[0]));

        var ingredients = plugin.getConfig().getConfigurationSection("solo-survival.crafting.ingredients");
        if (ingredients == null) {
            recipe.setIngredient('I', new RecipeChoice.MaterialChoice(Material.IRON_INGOT));
            recipe.setIngredient('C', new RecipeChoice.MaterialChoice(Material.COAL));
            recipe.setIngredient('E', new RecipeChoice.MaterialChoice(Material.EGG));
        } else {
            for (String key : ingredients.getKeys(false)) {
                if (key.isEmpty()) {
                    continue;
                }
                char c = key.charAt(0);
                Material mat = Material.matchMaterial(ingredients.getString(key, "AIR"));
                if (mat != null && mat != Material.AIR) {
                    recipe.setIngredient(c, new RecipeChoice.MaterialChoice(mat));
                }
            }
        }

        boolean added;
        try {
            // Paper: resendRecipes=true pushes the recipe to online clients
            added = Bukkit.addRecipe(recipe, true);
        } catch (NoSuchMethodError legacy) {
            try {
                added = Bukkit.addRecipe(recipe);
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not register Chaos Shard recipe: " + ex.getMessage());
                return;
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not register Chaos Shard recipe: " + ex.getMessage());
            return;
        }
        if (!added && Bukkit.getRecipe(recipeKey) == null) {
            plugin.getLogger().warning("Chaos Shard recipe was not added (addRecipe returned false).");
            return;
        }
        recipeRegistered = true;
        discoverForOnline();
        plugin.getLogger().info("Registered Chaos Shard crafting recipe (" + recipeKey + ").");
    }

    public void unregisterRecipe() {
        try {
            Bukkit.removeRecipe(recipeKey, true);
        } catch (NoSuchMethodError legacy) {
            try {
                Bukkit.removeRecipe(recipeKey);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        recipeRegistered = false;
    }

    public void discoverFor(@NotNull Player player) {
        if (!recipeRegistered || !isCraftingObtainEnabled()) {
            return;
        }
        try {
            player.discoverRecipe(recipeKey);
        } catch (Throwable ignored) {
        }
    }

    private void discoverForOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            discoverFor(player);
        }
    }

    public @NotNull NamespacedKey shardKey() {
        return shardKey;
    }

    public boolean isChaosShard(@Nullable ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(shardKey, PersistentDataType.BYTE);
    }

    public @NotNull ItemStack createChaosShard() {
        Material material = Material.matchMaterial(
                plugin.getConfig().getString("solo-survival.shard-item.material", "AMETHYST_SHARD"));
        if (material == null) {
            material = Material.AMETHYST_SHARD;
        }
        ItemStack stack = ItemsAdderHook.chaosShardOrFallback(plugin, material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String name = plugin.getConfig().getString("solo-survival.shard-item.name",
                    "<light_purple><bold>Chaos Shard</bold></light_purple>");
            meta.displayName(ColorUtil.parse(name));
            List<String> loreCfg = plugin.getConfig().getStringList("solo-survival.shard-item.lore");
            if (loreCfg.isEmpty()) {
                loreCfg = List.of(
                        "<gray>The heart of chaos.</gray>",
                        "<yellow>Obtain this to win Solo Survival.</yellow>"
                );
            }
            List<Component> lore = new ArrayList<>();
            for (String line : loreCfg) {
                lore.add(ColorUtil.parse(line));
            }
            meta.lore(lore);
            int cmd = plugin.getConfig().getInt("solo-survival.shard-item.custom-model-data", 0);
            if (cmd > 0) {
                meta.setCustomModelData(cmd);
            }
            meta.getPersistentDataContainer().set(shardKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Chance 0.0–1.0 to insert a Chaos Shard into a filled match chest. */
    public void maybeAddShardToLoot(@NotNull org.bukkit.inventory.Inventory inventory) {
        if (!isLootObtainEnabled()) {
            return;
        }
        double chance = plugin.getConfig().getDouble("solo-survival.shard.loot.chance",
                plugin.getConfig().getDouble("solo-survival.loot.chest-chance", 0.008));
        if (ThreadLocalRandom.current().nextDouble() > Math.max(0.0, Math.min(1.0, chance))) {
            return;
        }
        int slot = ThreadLocalRandom.current().nextInt(inventory.getSize());
        inventory.setItem(slot, createChaosShard());
    }

    private boolean isShardOre(@NotNull Material type) {
        Set<Material> defaults = EnumSet.of(
                Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
                Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
                Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE,
                Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
                Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
                Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
                Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
                Material.ANCIENT_DEBRIS
        );
        List<String> configured = plugin.getConfig().getStringList("solo-survival.shard.mining.ores");
        if (configured.isEmpty()) {
            return defaults.contains(type);
        }
        for (String name : configured) {
            Material mat = Material.matchMaterial(name);
            if (mat != null && mat == type) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOreBreak(@NotNull BlockBreakEvent event) {
        if (!isMiningObtainEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        GameInstance game = plugin.gameManager().getByPlayer(player);
        if (game == null || !game.getMode().isSoloSurvival() || !game.getState().isActive()) {
            return;
        }
        if (!game.isAlive(player.getUniqueId())) {
            return;
        }
        if (!isShardOre(event.getBlock().getType())) {
            return;
        }
        // Default 0.03% = 0.0003
        double chance = plugin.getConfig().getDouble("solo-survival.shard.mining.chance", 0.0003);
        if (ThreadLocalRandom.current().nextDouble() > Math.max(0.0, Math.min(1.0, chance))) {
            return;
        }
        Location dropAt = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        Item dropped = event.getBlock().getWorld().dropItemNaturally(dropAt, createChaosShard());
        dropped.setPickupDelay(5);
        dropped.setGlowing(true);
        game.trackEntity(dropped);
        plugin.lang().send(player, "solo-survival.shard-mined");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLootGenerate(@NotNull LootGenerateEvent event) {
        if (!isLootObtainEnabled()) {
            return;
        }
        // Very rare natural structure / buried treasure / dungeon / mineshaft / etc.
        double chance = plugin.getConfig().getDouble("solo-survival.shard.loot.chance", 0.008);
        if (ThreadLocalRandom.current().nextDouble() > Math.max(0.0, Math.min(1.0, chance))) {
            return;
        }
        List<ItemStack> loot = event.getLoot();
        loot.add(createChaosShard());
    }

    @EventHandler
    public void onJoinDiscover(@NotNull PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                discoverFor(event.getPlayer());
            }
        }, 40L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareCraft(@NotNull PrepareItemCraftEvent event) {
        if (!isCraftingObtainEnabled() || event.getRecipe() == null) {
            return;
        }
        NamespacedKey key = null;
        if (event.getRecipe() instanceof org.bukkit.Keyed keyed) {
            key = keyed.getKey();
        }
        if (key == null || !key.equals(recipeKey)) {
            // Also accept if result already tagged
            ItemStack result = event.getInventory().getResult();
            if (!isChaosShard(result)) {
                return;
            }
        }
        event.getInventory().setResult(createChaosShard());
    }

    public void spawnShardInWorld(@NotNull GameInstance game) {
        World world = game.getInstanceWorld();
        Location center = game.centerLocation();
        if (world == null || center == null) {
            return;
        }
        int radius = Math.max(8, (int) (game.getArena().getBorderSize() / 3.0));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location spot = null;
        for (int attempt = 0; attempt < 24; attempt++) {
            int x = center.getBlockX() + random.nextInt(-radius, radius + 1);
            int z = center.getBlockZ() + random.nextInt(-radius, radius + 1);
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location candidate = new Location(world, x + 0.5, y, z + 0.5);
            if (!candidate.getBlock().getType().isSolid() && !candidate.getBlock().isLiquid()) {
                spot = candidate;
                break;
            }
        }
        if (spot == null) {
            spot = center.clone().add(0.5, 1.0, 0.5);
        }
        Item dropped = world.dropItemNaturally(spot, createChaosShard());
        dropped.setPickupDelay(20);
        dropped.setGlowing(true);
        game.trackEntity(dropped);
        for (Player player : game.getPlayers()) {
            plugin.lang().send(player, "solo-survival.shard-spawned");
        }
    }

    public boolean isVictoryBlock(@NotNull Block block, @NotNull GameInstance game) {
        Location altar = game.victoryAltarLocation();
        if (altar != null && altar.getWorld() != null
                && altar.getWorld().equals(block.getWorld())
                && altar.getBlockX() == block.getX()
                && altar.getBlockY() == block.getY()
                && altar.getBlockZ() == block.getZ()) {
            return true;
        }
        if (!plugin.getConfig().getBoolean("solo-survival.victory-altar.accept-material-anywhere", false)
                && altar != null) {
            return false;
        }
        String matName = plugin.getConfig().getString("solo-survival.victory-altar.material", "LODESTONE");
        Material mat = Material.matchMaterial(matName == null ? "LODESTONE" : matName.toUpperCase(Locale.ROOT));
        return mat != null && block.getType() == mat;
    }

    /** Instant win the moment the player obtains a Chaos Shard (CHAOS_SHARD objective). */
    public void onShardObtained(@NotNull Player player, @NotNull GameInstance game) {
        if (!game.getMode().isSoloSurvival()) {
            return;
        }
        if (game.getSurvivalObjective() != SurvivalObjective.CHAOS_SHARD) {
            return;
        }
        if (!game.getState().isActive() || !game.isAlive(player.getUniqueId())) {
            return;
        }
        game.completeSurvivalVictory(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(@NotNull EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!isChaosShard(event.getItem().getItemStack())) {
            return;
        }
        GameInstance game = plugin.gameManager().getByPlayer(player);
        if (game != null) {
            Bukkit.getScheduler().runTask(plugin, () -> onShardObtained(player, game));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(@NotNull CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!isChaosShard(event.getCurrentItem()) && !isChaosShard(event.getRecipe().getResult())) {
            return;
        }
        GameInstance game = plugin.gameManager().getByPlayer(player);
        if (game != null) {
            Bukkit.getScheduler().runTask(plugin, () -> onShardObtained(player, game));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInvClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (!isChaosShard(current)) {
            return;
        }
        GameInstance game = plugin.gameManager().getByPlayer(player);
        if (game != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (hasShard(player)) {
                    onShardObtained(player, game);
                }
            });
        }
    }

    private boolean hasShard(@NotNull Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isChaosShard(stack)) {
                return true;
            }
        }
        return isChaosShard(player.getInventory().getItemInOffHand());
    }

    public boolean tryComplete(@NotNull Player player, @NotNull GameInstance game, @NotNull Block block) {
        // Legacy altar path still works, but obtain-on-pickup is primary for CHAOS_SHARD
        if (!game.getMode().isSoloSurvival() || !game.getState().isActive()) {
            return false;
        }
        if (!game.isAlive(player.getUniqueId())) {
            return false;
        }
        if (!isVictoryBlock(block, game)) {
            return false;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isChaosShard(hand)) {
            hand = player.getInventory().getItemInOffHand();
        }
        if (!isChaosShard(hand)) {
            plugin.lang().send(player, "solo-survival.need-shard");
            return true;
        }
        hand.setAmount(hand.getAmount() - 1);
        game.completeSurvivalVictory(player);
        return true;
    }
}

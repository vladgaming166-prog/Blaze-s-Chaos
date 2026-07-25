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
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        registerRecipe();
    }

    public void registerRecipe() {
        if (!plugin.getConfig().getBoolean("solo-survival.crafting.enabled", true)) {
            return;
        }
        if (recipeRegistered) {
            try {
                Bukkit.removeRecipe(recipeKey);
            } catch (Throwable ignored) {
            }
        }
        ItemStack result = createChaosShard();
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, result);
        List<String> shape = plugin.getConfig().getStringList("solo-survival.crafting.shape");
        if (shape.isEmpty()) {
            shape = List.of("GGG", "GAG", "GGG");
        }
        recipe.shape(shape.toArray(new String[0]));
        var ingredients = plugin.getConfig().getConfigurationSection("solo-survival.crafting.ingredients");
        if (ingredients == null) {
            recipe.setIngredient('G', Material.GOLD_INGOT);
            recipe.setIngredient('A', Material.AMETHYST_SHARD);
        } else {
            for (String key : ingredients.getKeys(false)) {
                if (key.isEmpty()) {
                    continue;
                }
                char c = key.charAt(0);
                Material mat = Material.matchMaterial(ingredients.getString(key, "AIR"));
                if (mat != null && mat != Material.AIR) {
                    recipe.setIngredient(c, mat);
                }
            }
        }
        try {
            Bukkit.addRecipe(recipe);
            recipeRegistered = true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not register Chaos Shard recipe: " + ex.getMessage());
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

    /** Chance 0.0–1.0 to insert a Chaos Shard into a filled chest. */
    public void maybeAddShardToLoot(@NotNull org.bukkit.inventory.Inventory inventory) {
        if (!plugin.getConfig().getBoolean("solo-survival.loot.enabled", true)) {
            return;
        }
        double chance = plugin.getConfig().getDouble("solo-survival.loot.chest-chance", 0.15);
        if (ThreadLocalRandom.current().nextDouble() > Math.max(0.0, Math.min(1.0, chance))) {
            return;
        }
        int slot = ThreadLocalRandom.current().nextInt(inventory.getSize());
        inventory.setItem(slot, createChaosShard());
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

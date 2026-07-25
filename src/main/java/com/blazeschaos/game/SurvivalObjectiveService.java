package com.blazeschaos.game;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.arena.Arena;
import com.blazeschaos.util.ColorUtil;
import com.blazeschaos.util.ItemsAdderHook;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Solo Survival objective: Chaos Shard + Victory Altar / Victory Block.
 */
public final class SurvivalObjectiveService {

    private final BlazesChaosPlugin plugin;
    private final NamespacedKey shardKey;

    public SurvivalObjectiveService(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        this.shardKey = new NamespacedKey(plugin, "chaos_shard");
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
                        "<gray>Survive the chaos.</gray>",
                        "<yellow>Use at the Victory Altar to win.</yellow>"
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

    public void spawnShardInWorld(@NotNull GameInstance game) {
        Arena arena = game.getArena();
        World world = arena.getWorld();
        Location center = arena.getCenter() != null ? arena.getCenter() : arena.getSpawn();
        if (world == null || center == null) {
            return;
        }
        int radius = Math.max(8, (int) (arena.getBorderSize() / 3.0));
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

    public boolean isVictoryBlock(@NotNull Block block, @NotNull Arena arena) {
        Location altar = arena.getVictoryAltar();
        if (altar != null && altar.getWorld() != null
                && altar.getWorld().equals(block.getWorld())
                && altar.getBlockX() == block.getX()
                && altar.getBlockY() == block.getY()
                && altar.getBlockZ() == block.getZ()) {
            return true;
        }
        if (!plugin.getConfig().getBoolean("solo-survival.victory-altar.accept-material-anywhere", false)
                && altar != null) {
            // Prefer configured location when set
            return false;
        }
        String matName = plugin.getConfig().getString("solo-survival.victory-altar.material", "LODESTONE");
        Material mat = Material.matchMaterial(matName == null ? "LODESTONE" : matName.toUpperCase(Locale.ROOT));
        return mat != null && block.getType() == mat;
    }

    public boolean tryComplete(@NotNull Player player, @NotNull GameInstance game, @NotNull Block block) {
        if (!game.getMode().isSoloSurvival() || !game.getState().isActive()) {
            return false;
        }
        if (!game.isAlive(player.getUniqueId())) {
            return false;
        }
        if (!isVictoryBlock(block, game.getArena())) {
            return false;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isChaosShard(hand)) {
            hand = player.getInventory().getItemInOffHand();
        }
        if (!isChaosShard(hand)) {
            plugin.lang().send(player, "solo-survival.need-shard");
            return true; // consumed click feedback
        }
        // Consume one shard
        hand.setAmount(hand.getAmount() - 1);
        game.completeSurvivalVictory(player);
        return true;
    }
}

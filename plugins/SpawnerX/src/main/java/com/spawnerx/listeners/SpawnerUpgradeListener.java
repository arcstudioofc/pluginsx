package com.spawnerx.listeners;

import com.spawnerx.SpawnerX;
import com.spawnerx.managers.ConfigManager;
import com.spawnerx.utils.SpawnerUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Locale;

/**
 * Listener responsável pelo upgrade de spawners via clique esquerdo.
 */
public class SpawnerUpgradeListener implements Listener {

    private final SpawnerX plugin;

    public SpawnerUpgradeListener(SpawnerX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerLeftClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SPAWNER) {
            return;
        }

        ConfigManager config = plugin.getConfigManager();
        if (!config.isUpgradeActive()) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            return;
        }

        if (!config.isUpgradeMaterial(hand.getType())) {
            return;
        }

        if (plugin.isLicenseLocked()) {
            event.setCancelled(true);
            player.sendMessage(plugin.getLocaleManager().getMessage("license.locked"));
            return;
        }

        // Há intenção explícita de upgrade: não deixar quebrar por engano.
        event.setCancelled(true);

        CreatureSpawner spawner = (CreatureSpawner) block.getState();
        PersistentDataContainer container = spawner.getPersistentDataContainer();

        if (!container.has(plugin.getSpawnerManager().getStackKey(), PersistentDataType.INTEGER)) {
            player.sendMessage(plugin.getLocaleManager().getMessage("upgrade.only-spawnerx"));
            return;
        }

        int stack = container.getOrDefault(plugin.getSpawnerManager().getStackKey(), PersistentDataType.INTEGER, 1);
        if (stack > 1) {
            player.sendMessage(plugin.getLocaleManager().getMessage("upgrade.stack-not-supported"));
            return;
        }

        if (config.isUpgradePermissionRequired() && !player.hasPermission(config.getUpgradePermissionNode())) {
            player.sendMessage(plugin.getLocaleManager().getMessage("upgrade.no-permission"));
            return;
        }

        int currentLevel = plugin.getSpawnerManager().getSpawnerLevel(spawner);
        if (currentLevel >= config.getUpgradeMaxLevel()) {
            player.sendMessage(plugin.getLocaleManager().getMessage("upgrade.max-level",
                "level", String.valueOf(currentLevel)));
            return;
        }

        ConfigManager.UpgradeLevel nextLevel = config.getNextUpgradeLevel(currentLevel);
        if (nextLevel == null || nextLevel.level() != currentLevel + 1) {
            player.sendMessage(plugin.getLocaleManager().getMessage("upgrade.invalid-path"));
            return;
        }

        Material requiredMaterial = nextLevel.costMaterial();
        if (requiredMaterial != hand.getType()) {
            player.sendMessage(plugin.getLocaleManager().getMessage("upgrade.wrong-material",
                "required", formatMaterial(requiredMaterial),
                "provided", formatMaterial(hand.getType())));
            return;
        }

        int requiredAmount = nextLevel.costAmount();
        if (hand.getAmount() < requiredAmount) {
            player.sendMessage(plugin.getLocaleManager().getMessage("upgrade.not-enough-material",
                "required", String.valueOf(requiredAmount),
                "current", String.valueOf(hand.getAmount()),
                "material", formatMaterial(requiredMaterial)));
            return;
        }

        consumeHandMaterial(player, hand, requiredAmount);

        int newLevel = nextLevel.level();
        plugin.getSpawnerManager().setSpawnerLevel(spawner, newLevel);
        plugin.getSpawnerManager().applySpawnerStats(spawner, stack, newLevel);
        spawner.update();

        plugin.getSpawnerManager().updateSpawnerHologram(block, spawner.getSpawnedType(), stack, newLevel);
        playUpgradeAnimation(block.getLocation());

        String typeName = SpawnerUtils.getEntityDisplayName(spawner.getSpawnedType());
        player.sendMessage(plugin.getLocaleManager().getMessage("upgrade.success",
            "type", typeName,
            "level", String.valueOf(newLevel)));
    }

    private void consumeHandMaterial(Player player, ItemStack hand, int amount) {
        int remaining = hand.getAmount() - amount;
        if (remaining <= 0) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            hand.setAmount(remaining);
        }
    }

    private String formatMaterial(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    private void playUpgradeAnimation(Location blockLocation) {
        World world = blockLocation.getWorld();
        if (world == null) {
            return;
        }

        Location center = blockLocation.clone().add(0.5, 0.7, 0.5);
        world.playSound(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.15f);

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (step >= 6) {
                    world.spawnParticle(Particle.END_ROD, center, 30, 0.35, 0.35, 0.35, 0.04);
                    world.spawnParticle(Particle.FIREWORK, center, 18, 0.2, 0.2, 0.2, 0.01);
                    world.playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.25f);
                    cancel();
                    return;
                }

                double radius = 0.40 + (step * 0.04);
                double yOffset = 0.15 + (step * 0.03);
                for (int i = 0; i < 12; i++) {
                    double angle = ((Math.PI * 2) / 12.0) * i + (step * 0.5);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Location particleLoc = center.clone().add(x, yOffset, z);
                    world.spawnParticle(Particle.ENCHANT, particleLoc, 2, 0.0, 0.0, 0.0, 0.0);
                }

                step++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
}

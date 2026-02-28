package com.spawnerx.listeners;

import com.spawnerx.SpawnerX;
import com.spawnerx.utils.SpawnerUtils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Listener para eventos de explosão envolvendo spawners
 * Gerencia o drop de spawners em explosões
 */
public class SpawnerExplosionListener implements Listener {
    
    private final SpawnerX plugin;
    private final Random random;
    
    public SpawnerExplosionListener(SpawnerX plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }
    
    /**
     * Processa a explosão e gerencia drops de spawners
     */
    private void handleExplosion(List<Block> blocks) {
        if (plugin.isLicenseLocked()) {
            return;
        }
        if (!plugin.getConfigManager().isExplosionDropAllowed()) {
            return;
        }
        
        double dropChance = plugin.getConfigManager().getExplosionDropChance();
        List<Block> spawnersToRemove = new ArrayList<>();
        
        for (Block block : blocks) {
            if (block.getType() == Material.SPAWNER) {
                // Obter tipo de entidade do spawner antes de remover o bloco
                CreatureSpawner spawner = (CreatureSpawner) block.getState();
                EntityType entityType = spawner.getSpawnedType();
                PersistentDataContainer container = spawner.getPersistentDataContainer();
                int stack = container.getOrDefault(plugin.getSpawnerManager().getStackKey(), PersistentDataType.INTEGER, 1);
                int level = plugin.getSpawnerManager().getSpawnerLevel(spawner);

                // Processar cada spawner no stack
                int droppedCount = 0;
                for (int i = 0; i < stack; i++) {
                    double roll = random.nextDouble() * 100.0;
                    if (roll <= dropChance) {
                        ItemStack spawnerItem = plugin.getSpawnerManager().createSpawner(entityType, 1, level);
                        block.getWorld().dropItemNaturally(block.getLocation(), spawnerItem);
                        droppedCount++;
                    }
                }
                
                if (droppedCount > 0) {
                    String entityName = SpawnerUtils.getEntityDisplayName(entityType);
                    plugin.getLogger().info("Spawner de " + entityName + " (x" + stack + ") destruído por explosão. " + 
                        droppedCount + " itens dropados em " +
                        block.getLocation().getBlockX() + ", " +
                        block.getLocation().getBlockY() + ", " +
                        block.getLocation().getBlockZ());
                }

                plugin.getSpawnerManager().removeSpawnerHologram(block);
                plugin.getStackSpawnChainTracker().clear(block.getLocation());
                spawnersToRemove.add(block);
            }
        }
        
        // Remover spawners da lista de blocos do evento para evitar drop vanilla
        for (Block spawnerBlock : spawnersToRemove) {
            spawnerBlock.setType(Material.AIR);
            blocks.remove(spawnerBlock);
        }
    }
}

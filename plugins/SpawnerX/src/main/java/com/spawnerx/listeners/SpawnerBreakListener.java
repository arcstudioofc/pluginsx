package com.spawnerx.listeners;

import com.spawnerx.SpawnerX;
import com.spawnerx.utils.SpawnerUtils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Random;

/**
 * Listener para eventos de quebra de spawner
 * Gerencia as regras de quebra configuradas
 */
public class SpawnerBreakListener implements Listener {
    
    private final SpawnerX plugin;
    private final Random random = new Random();
    
    public SpawnerBreakListener(SpawnerX plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        
        // Verificar se é um spawner
        if (block.getType() != Material.SPAWNER) {
            return;
        }
        
        Player player = event.getPlayer();

        if (plugin.isLicenseLocked()) {
            event.setCancelled(true);
            player.sendMessage(plugin.getLocaleManager().getMessage("license.locked"));
            return;
        }
        
        // Verificar permissão
        if (!player.hasPermission("spawnerx.spawner.break")) {
            event.setCancelled(true);
            player.sendMessage(plugin.getLocaleManager().getMessage("break.no-permission"));
            return;
        }
        
        ItemStack tool = player.getInventory().getItemInMainHand();
        List<Material> validTools = plugin.getConfigManager().getValidTools();
        boolean hasTool = validTools.contains(tool.getType());
        boolean hasSilk = !plugin.getConfigManager().requiresSilkTouch() || tool.containsEnchantment(Enchantment.SILK_TOUCH);
        boolean meetsRequirements = hasTool && hasSilk;

        CreatureSpawner spawner = (CreatureSpawner) block.getState();
        EntityType entityType = spawner.getSpawnedType();
        PersistentDataContainer container = spawner.getPersistentDataContainer();
        int currentStack = container.getOrDefault(plugin.getSpawnerManager().getStackKey(), PersistentDataType.INTEGER, 1);
        int currentLevel = plugin.getSpawnerManager().getSpawnerLevel(spawner);

        if (!meetsRequirements) {
            if (plugin.getConfigManager().isAllowBreakWithoutRequirements()) {
                // Permitir quebra sem requisitos (sem drop)
                String entityName = SpawnerUtils.getEntityDisplayName(entityType);
                player.sendMessage(plugin.getLocaleManager().getMessage("break.success-no-req", "type", entityName));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                plugin.getSpawnerManager().removeSpawnerHologram(block);
                plugin.getStackSpawnChainTracker().clear(block.getLocation());
                return; // Deixa o bloco ser quebrado normalmente pelo Minecraft (sem drop customizado)
            } else {
                // Comportamento padrão: cancelar quebra
                event.setCancelled(true);
                if (!hasTool) {
                    player.sendMessage(plugin.getLocaleManager().getMessage("break.wrong-tool"));
                } else if (!hasSilk) {
                    player.sendMessage(plugin.getLocaleManager().getMessage("break.no-silk-touch"));
                }
                return;
            }
        }
        
        // Cancelar drop padrão e experiência
        event.setDropItems(false);
        event.setExpToDrop(0);
        
        // Lógica de Chance de Drop
        double chance = plugin.getConfigManager().getBreakDropChance();
        boolean shouldDrop = random.nextDouble() * 100 <= chance;
        boolean dropStacked = currentStack > 1 && player.isSneaking();

        if (shouldDrop) {
            // Criar o spawner item
            int dropSize = dropStacked ? currentStack : 1;
            ItemStack spawnerItem = plugin.getSpawnerManager().createSpawner(entityType, dropSize, currentLevel);
            
            // Entregar o item
            if (plugin.getConfigManager().isBreakDropToInventory()) {
                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(spawnerItem);
                } else {
                    block.getWorld().dropItemNaturally(block.getLocation(), spawnerItem);
                }
            } else {
                block.getWorld().dropItemNaturally(block.getLocation(), spawnerItem);
            }
            
            player.sendMessage(plugin.getLocaleManager().getMessage("break.success", "type", SpawnerUtils.getEntityDisplayName(entityType)));
        } else {
            player.sendMessage(plugin.getLocaleManager().getMessage("break.fail-chance", "type", SpawnerUtils.getEntityDisplayName(entityType)));
        }

        // Se estiver agachado, dropar 1 item com o stack inteiro
        if (dropStacked) {
            plugin.getSpawnerManager().removeSpawnerHologram(block);
            plugin.getStackSpawnChainTracker().clear(block.getLocation());
            return;
        }

        // Se houver mais de 1 no stack, não quebrar o bloco, apenas diminuir o stack
        if (currentStack > 1) {
            event.setCancelled(true);
            int newStack = currentStack - 1;
            container.set(plugin.getSpawnerManager().getStackKey(), PersistentDataType.INTEGER, newStack);
            plugin.getSpawnerManager().applySpawnerStats(spawner, newStack, currentLevel);
            spawner.update();
            plugin.getSpawnerManager().updateSpawnerHologram(block, entityType, newStack, currentLevel);
            
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);
            return;
        }

        plugin.getSpawnerManager().removeSpawnerHologram(block);
        plugin.getStackSpawnChainTracker().clear(block.getLocation());
    }
}

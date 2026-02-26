package com.spawnerx.listeners;

import com.spawnerx.SpawnerX;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Listener do shop de spawners
 */
public class SpawnerShopListener implements Listener {

    private final SpawnerX plugin;

    public SpawnerShopListener(SpawnerX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        if (!plugin.getSpawnerShopManager().isShopInventory(inventory)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (plugin.isLicenseLocked()) {
            player.sendMessage(plugin.getLocaleManager().getMessage("license.locked"));
            player.closeInventory();
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;
        plugin.getSpawnerShopManager().handleClick(player, inventory, clicked);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (plugin.getSpawnerShopManager().isShopInventory(event.getInventory())) {
            event.setCancelled(true);
        }
    }
}

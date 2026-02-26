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
 * Listener do menu administrativo de entrega de spawners.
 */
public class AdminSpawnerMenuListener implements Listener {

    private final SpawnerX plugin;

    public AdminSpawnerMenuListener(SpawnerX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        if (!plugin.getAdminSpawnerMenuManager().isAdminMenuInventory(inventory)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (plugin.isLicenseLocked()) {
            player.sendMessage(plugin.getLocaleManager().getMessage("license.locked"));
            player.closeInventory();
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) {
            return;
        }

        plugin.getAdminSpawnerMenuManager().handleClick(player, inventory, clicked);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (plugin.getAdminSpawnerMenuManager().isAdminMenuInventory(event.getInventory())) {
            event.setCancelled(true);
        }
    }
}

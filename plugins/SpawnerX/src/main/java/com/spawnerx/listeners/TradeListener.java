package com.spawnerx.listeners;

import com.spawnerx.SpawnerX;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener de eventos do sistema de trade.
 */
public class TradeListener implements Listener {

    private final SpawnerX plugin;

    public TradeListener(SpawnerX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!plugin.getTradeManager().isTradeInventory(event.getView().getTopInventory())) {
            return;
        }
        plugin.getTradeManager().handleInventoryClick(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!plugin.getTradeManager().isTradeInventory(event.getView().getTopInventory())) {
            return;
        }
        plugin.getTradeManager().handleInventoryDrag(event);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!plugin.getTradeManager().isTradeInventory(event.getInventory())) {
            return;
        }
        plugin.getTradeManager().handleInventoryClose(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getTradeManager().handlePlayerQuit(event.getPlayer());
    }
}

package com.spawnerx.managers;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Holder para identificar inventarios de trade.
 */
public class TradeHolder implements InventoryHolder {

    private final UUID sessionId;

    public TradeHolder(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}

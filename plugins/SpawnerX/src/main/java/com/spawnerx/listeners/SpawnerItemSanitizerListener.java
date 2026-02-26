package com.spawnerx.listeners;

import com.spawnerx.SpawnerX;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Sanitiza spawners legados ao jogador entrar.
 */
public class SpawnerItemSanitizerListener implements Listener {

    private final SpawnerX plugin;

    public SpawnerItemSanitizerListener(SpawnerX plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (plugin.isLicenseLocked()) {
            return;
        }

        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();

        ItemStack[] storage = inventory.getStorageContents();
        boolean storageChanged = false;
        for (int i = 0; i < storage.length; i++) {
            ItemStack current = storage[i];
            if (!plugin.getSpawnerManager().isLegacySpawnerWithBlockState(current)) {
                continue;
            }

            ItemStack sanitized = plugin.getSpawnerManager().sanitizeSpawnerItem(current);
            if (sanitized != current) {
                storage[i] = sanitized;
                storageChanged = true;
            }
        }

        if (storageChanged) {
            inventory.setStorageContents(storage);
        }

        ItemStack offHand = inventory.getItemInOffHand();
        if (plugin.getSpawnerManager().isLegacySpawnerWithBlockState(offHand)) {
            ItemStack sanitized = plugin.getSpawnerManager().sanitizeSpawnerItem(offHand);
            if (sanitized != offHand) {
                inventory.setItemInOffHand(sanitized);
            }
        }
    }
}

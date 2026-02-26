package com.spawnerx.listeners;

import com.spawnerx.SpawnerX;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener para o sistema de empilhamento inteligente de spawners
 * Spawners só empilham se o EntityType for igual, ignorando outros metadados
 */
public class SpawnerStackListener implements Listener {

    private final SpawnerX plugin;

    public SpawnerStackListener(SpawnerX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (plugin.isLicenseLocked()) return;
        if (!plugin.getConfigManager().isStackingEnabled()) return;
        if (!isSupportedPlaceAction(event.getAction())) return;
        if (event.getWhoClicked() != null && event.getWhoClicked().getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        if (event.getView() != null && event.getView().getType() == InventoryType.CREATIVE) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        if (isSpawner(cursor) && plugin.getSpawnerManager().isLegacySpawnerWithBlockState(cursor)) {
            ItemStack sanitizedCursor = plugin.getSpawnerManager().sanitizeSpawnerItem(cursor);
            event.setCursor(sanitizedCursor);
            cursor = sanitizedCursor;
        }

        if (isSpawner(current) && plugin.getSpawnerManager().isLegacySpawnerWithBlockState(current)) {
            ItemStack sanitizedCurrent = plugin.getSpawnerManager().sanitizeSpawnerItem(current);
            event.setCurrentItem(sanitizedCurrent);
            current = sanitizedCurrent;
        }

        if (isSpawner(cursor) && isSpawner(current)) {
            EntityType typeA = plugin.getSpawnerManager().getSpawnerEntity(cursor);
            EntityType typeB = plugin.getSpawnerManager().getSpawnerEntity(current);
            int levelA = plugin.getSpawnerManager().getSpawnerLevel(cursor);
            int levelB = plugin.getSpawnerManager().getSpawnerLevel(current);

            if (typeA != null && typeA == typeB && levelA == levelB) {
                int maxStack = plugin.getConfigManager().getLevelMaxStackOrDefault(levelA);
                int currentAmount = current.getAmount();
                int cursorAmount = cursor.getAmount();

                if (currentAmount < maxStack) {
                    event.setCancelled(true);
                    int canAdd = maxStack - currentAmount;
                    int toAdd = Math.min(canAdd, cursorAmount);

                    current.setAmount(currentAmount + toAdd);
                    if (cursorAmount - toAdd <= 0) {
                        event.getWhoClicked().setItemOnCursor(null);
                    } else {
                        cursor.setAmount(cursorAmount - toAdd);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (plugin.isLicenseLocked()) return;
        if (!plugin.getConfigManager().isStackingEnabled()) return;

        ItemStack item = event.getItem();
        if (!isSpawner(item)) return;

        if (plugin.getSpawnerManager().isLegacySpawnerWithBlockState(item)) {
            ItemStack sanitized = plugin.getSpawnerManager().sanitizeSpawnerItem(item);
            event.setItem(sanitized);
            item = sanitized;
        }

        EntityType type = plugin.getSpawnerManager().getSpawnerEntity(item);
        if (type == null) return;

        // Lógica simplificada para hopper: o Minecraft já tenta empilhar se os itens forem iguais.
        // Como forçamos o EntityType no NBT, se os NBTs forem idênticos, eles empilham sozinhos.
    }

    private boolean isSpawner(ItemStack item) {
        return item != null && item.getType() == Material.SPAWNER;
    }

    private boolean isSupportedPlaceAction(InventoryAction action) {
        return action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_SOME;
    }
}

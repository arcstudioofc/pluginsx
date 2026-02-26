package com.spawnerx.managers;

import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Holder para identificar inventários do menu admin.
 */
public class AdminSpawnerMenuHolder implements InventoryHolder {

    public enum View {
        MOBS,
        LEVELS
    }

    private final View view;
    private final int page;
    private final EntityType selectedEntity;

    public AdminSpawnerMenuHolder(View view, int page, EntityType selectedEntity) {
        this.view = view;
        this.page = page;
        this.selectedEntity = selectedEntity;
    }

    public View getView() {
        return view;
    }

    public int getPage() {
        return page;
    }

    public EntityType getSelectedEntity() {
        return selectedEntity;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}

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
    private final int parentPage;

    public AdminSpawnerMenuHolder(View view, int page, EntityType selectedEntity, int parentPage) {
        this.view = view;
        this.page = page;
        this.selectedEntity = selectedEntity;
        this.parentPage = parentPage;
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

    public int getParentPage() {
        return parentPage;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}

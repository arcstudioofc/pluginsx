package com.spawnerx.managers;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Holder para identificar inventários do shop.
 */
public class SpawnerShopHolder implements InventoryHolder {

    public enum View {
        CATEGORIES,
        CATEGORY,
        PUBLIC_SALES
    }

    private final View view;
    private final String categoryId;
    private final int page;
    private final int parentPage;

    public SpawnerShopHolder(View view, String categoryId, int page, int parentPage) {
        this.view = view;
        this.categoryId = categoryId;
        this.page = page;
        this.parentPage = parentPage;
    }

    public View getView() {
        return view;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public int getPage() {
        return page;
    }

    public int getParentPage() {
        return parentPage;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}

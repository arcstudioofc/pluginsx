package com.spawnerx.managers;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Holder para identificar e navegar no menu de interação de spawners.
 */
public class SpawnerInteractMenuHolder implements InventoryHolder {

    public enum View {
        SUMMARY,
        STACK_DETAILS
    }

    private final View view;
    private final UUID worldUuid;
    private final int x;
    private final int y;
    private final int z;
    private final int page;
    private final int parentPage;

    public SpawnerInteractMenuHolder(View view, UUID worldUuid, int x, int y, int z, int page, int parentPage) {
        this.view = view;
        this.worldUuid = worldUuid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.page = page;
        this.parentPage = parentPage;
    }

    public View getView() {
        return view;
    }

    public UUID getWorldUuid() {
        return worldUuid;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getPage() {
        return page;
    }

    public int getParentPage() {
        return parentPage;
    }

    public Block resolveBlock() {
        World world = Bukkit.getWorld(worldUuid);
        if (world == null) {
            return null;
        }
        return world.getBlockAt(x, y, z);
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}

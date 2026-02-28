package com.spawnerx.listeners;

import com.spawnerx.SpawnerX;
import org.bukkit.Location;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Avança o índice da cadeia apenas quando o spawn realmente aconteceu.
 */
public class SpawnerStackChainListener implements Listener {

    private final SpawnerX plugin;

    public SpawnerStackChainListener(SpawnerX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        CreatureSpawner spawner = event.getSpawner();
        if (spawner == null) {
            return;
        }

        Location location = spawner.getLocation();
        if (!plugin.getConfigManager().isStackSpawnChained()) {
            plugin.getStackSpawnChainTracker().clear(location);
            return;
        }

        PersistentDataContainer container = spawner.getPersistentDataContainer();
        if (!container.has(plugin.getSpawnerManager().getStackKey(), PersistentDataType.INTEGER)) {
            return;
        }

        int stack = Math.max(1, container.getOrDefault(plugin.getSpawnerManager().getStackKey(), PersistentDataType.INTEGER, 1));
        if (stack <= 1) {
            plugin.getStackSpawnChainTracker().clear(location);
            return;
        }

        if (location.getWorld() == null) {
            return;
        }

        long tick = location.getWorld().getFullTime();
        plugin.getStackSpawnChainTracker().advanceIfNeeded(
            location,
            stack,
            plugin.getConfigManager().getStackSpawnOrder(),
            tick
        );
    }
}

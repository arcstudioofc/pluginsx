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
 * Bloqueia spawn de spawners do SpawnerX fora do distance-active
 */
public class SpawnerDistanceListener implements Listener {

    private final SpawnerX plugin;

    public SpawnerDistanceListener(SpawnerX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        CreatureSpawner spawner = event.getSpawner();
        if (spawner == null) return;

        PersistentDataContainer container = spawner.getPersistentDataContainer();
        if (!container.has(plugin.getSpawnerManager().getStackKey(), PersistentDataType.INTEGER)) {
            return;
        }

        if (plugin.isLicenseLocked()) {
            event.setCancelled(true);
            return;
        }

        int distance = plugin.getConfigManager().getSpawnerActiveDistance();
        Location location = spawner.getLocation();
        boolean playerNearby = !location.getWorld().getNearbyPlayers(location, distance).isEmpty();
        if (!playerNearby) {
            event.setCancelled(true);
        }
    }
}

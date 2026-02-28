package com.spawnerx.listeners;

import com.spawnerx.SpawnerX;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Reconcília spawners carregados para aplicar o modo atual imediatamente.
 */
public class SpawnerChunkMigrationListener implements Listener {

    private final SpawnerX plugin;

    public SpawnerChunkMigrationListener(SpawnerX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        plugin.getSpawnerManager().reconcileChunkSpawners(event.getChunk(), true);
    }
}

package com.spawnerx.listeners;

import com.spawnerx.SpawnerX;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Exibe feedback quando um admin entra e a licença está válida.
 */
public class LicenseAdminFeedbackListener implements Listener {

    private final SpawnerX plugin;

    public LicenseAdminFeedbackListener(SpawnerX plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.isLicenseValid()) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("spawnerx.admin")) {
            return;
        }

        player.sendMessage(plugin.getLocaleManager().getMessage("license.admin-feedback"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        player.spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0.0, 1.0, 0.0),
            18, 0.4, 0.6, 0.4, 0.01);
        player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0.0, 1.0, 0.0),
            20, 0.5, 0.8, 0.5, 0.01);
    }
}

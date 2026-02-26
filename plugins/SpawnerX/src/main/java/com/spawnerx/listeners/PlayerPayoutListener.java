package com.spawnerx.listeners;

import com.spawnerx.SpawnerX;
import com.spawnerx.managers.PublicSalesManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Realiza o pagamento de XP pendente quando o vendedor entra no servidor.
 */
public class PlayerPayoutListener implements Listener {

    private final SpawnerX plugin;

    public PlayerPayoutListener(SpawnerX plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (plugin.isLicenseLocked()) {
            return;
        }

        int amount = plugin.getPublicSalesManager().payoutPending(event.getPlayer());
        if (amount > 0) {
            event.getPlayer().sendMessage(plugin.getLocaleManager().getMessage(
                "shop.payout.received-pending",
                "amount", String.valueOf(amount),
                "currency", plugin.getConfigManager().getShopCurrency()
            ));
        }

        PublicSalesManager.ExpiredReturnPayout returned = plugin.getPublicSalesManager().payoutPendingExpiredReturns(event.getPlayer());
        if (returned.listingsReturned() <= 0) {
            return;
        }

        event.getPlayer().sendMessage(plugin.getLocaleManager().getMessage(
            "shop.sell.expired-returned-login",
            "listings", String.valueOf(returned.listingsReturned()),
            "items", String.valueOf(returned.itemsReturned())
        ));
    }
}

package com.spawnerx.commands;

import com.spawnerx.SpawnerX;
import com.spawnerx.managers.LicenseManager;
import com.spawnerx.managers.PublicSale;
import com.spawnerx.managers.PublicSalesManager;
import com.spawnerx.managers.TradeManager;
import com.spawnerx.utils.SpawnerUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Executor de comandos do SpawnerX.
 */
public class SpawnerXCommand implements CommandExecutor, TabCompleter {

    private final SpawnerX plugin;

    public SpawnerXCommand(SpawnerX plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (plugin.isLicenseLocked()) {
                sendLicenseLockedInfo(sender);
                return true;
            }
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        if (plugin.isLicenseLocked() && !subCommand.equals("reload") && !subCommand.equals("license")) {
            sendLicenseLockedInfo(sender);
            return true;
        }

        return switch (subCommand) {
            case "reload" -> handleReload(sender);
            case "license" -> handleLicense(sender, args);
            case "admin" -> handleAdmin(sender, args);
            case "shop" -> handleShop(sender);
            case "sell" -> handleSell(sender, args);
            case "trade" -> handleTrade(sender, args);
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            default -> {
                sender.sendMessage(plugin.getLocaleManager().getMessage("general.invalid-usage",
                    "usage", "/" + label + " [reload|license|admin|shop|sell|trade|help]"));
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("spawnerx.admin")) {
            sender.sendMessage(plugin.getLocaleManager().getMessage("commands.no-permission"));
            return true;
        }

        try {
            plugin.reload();
            sender.sendMessage(plugin.getLocaleManager().getMessage("commands.reload.success"));
            sender.sendMessage(plugin.getLocaleManager().getMessage("license.validating"));
        } catch (Exception e) {
            sender.sendMessage(plugin.getLocaleManager().getMessage("commands.reload.error"));
            plugin.getLogger().severe("Erro ao recarregar configuração: " + e.getMessage());
        }

        return true;
    }

    private boolean handleLicense(CommandSender sender, String[] args) {
        if (!sender.hasPermission("spawnerx.admin")) {
            sender.sendMessage(plugin.getLocaleManager().getMessage("commands.no-permission"));
            return true;
        }

        if (args.length == 2) {
            String actionOrLegacyKey = args[1] == null ? "" : args[1].trim();
            if (actionOrLegacyKey.isBlank()) {
                sendLicenseUsage(sender);
                return true;
            }

            if (actionOrLegacyKey.equalsIgnoreCase("status")) {
                LicenseManager.LicenseStatus status = plugin.getLicenseManager().getStatus();
                sender.sendMessage(plugin.getLocaleManager().getMessage(getLicenseStatusMessagePath(status)));
                return true;
            }

            if (actionOrLegacyKey.equalsIgnoreCase("key")) {
                sendLicenseUsage(sender);
                return true;
            }

            saveLicenseKey(sender, actionOrLegacyKey);
            return true;
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("key") && !args[2].isBlank()) {
            saveLicenseKey(sender, args[2]);
            return true;
        }

        sendLicenseUsage(sender);
        return true;
    }

    private void saveLicenseKey(CommandSender sender, String key) {
        plugin.getConfigManager().setLicenseKey(key);
        sender.sendMessage(plugin.getLocaleManager().getMessage("license.saved"));
        sender.sendMessage(plugin.getLocaleManager().getMessage("license.reload-required"));
    }

    private void sendLicenseUsage(CommandSender sender) {
        sender.sendMessage(plugin.getLocaleManager().getMessage("general.invalid-usage",
            "usage", plugin.getLocaleManager().getMessage("license.usage")));
    }

    private String getLicenseStatusMessagePath(LicenseManager.LicenseStatus status) {
        return switch (status) {
            case VALID -> "license.status.valid";
            case VALIDATING -> "license.status.validating";
            case INVALID -> "license.status.invalid";
        };
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("spawnerx.admin")) {
            sender.sendMessage(plugin.getLocaleManager().getMessage("commands.no-permission"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLocaleManager().getMessage("admin.only-players"));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(plugin.getLocaleManager().getMessage("general.invalid-usage",
                "usage", "/spawnerx admin"));
            return true;
        }

        plugin.getAdminSpawnerMenuManager().openMobMenu(player);
        return true;
    }

    private boolean handleShop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLocaleManager().getMessage("shop.only-players"));
            return true;
        }
        if (!sender.hasPermission("spawnerx.shop")) {
            sender.sendMessage(plugin.getLocaleManager().getMessage("commands.no-permission"));
            return true;
        }

        plugin.getSpawnerShopManager().openShop(player);
        return true;
    }

    private boolean handleSell(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLocaleManager().getMessage("shop.only-players"));
            return true;
        }
        if (args.length < 2) {
            sendSellUsage(sender);
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "xp" -> handleSellXp(player, args);
            case "stats" -> handleSellStats(player, args);
            default -> {
                sendSellUsage(sender);
                yield true;
            }
        };
    }

    private boolean handleTrade(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLocaleManager().getMessage("trade.only-players"));
            return true;
        }

        if (!player.hasPermission("spawnerx.trade")) {
            player.sendMessage(plugin.getLocaleManager().getMessage("commands.no-permission"));
            return true;
        }

        if (args.length != 2) {
            player.sendMessage(plugin.getLocaleManager().getMessage(
                "general.invalid-usage",
                "usage", plugin.getLocaleManager().getMessage("trade.usage")
            ));
            return true;
        }

        String action = args[1];
        if (action.equalsIgnoreCase("accept")) {
            return handleTradeAccept(player);
        }
        if (action.equalsIgnoreCase("deny")) {
            return handleTradeDeny(player);
        }

        return handleTradeInvite(player, action);
    }

    private boolean handleTradeInvite(Player player, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            target = Bukkit.getPlayer(targetName);
        }

        if (target == null || !target.isOnline()) {
            player.sendMessage(plugin.getLocaleManager().getMessage("trade.target-offline", "player", targetName));
            return true;
        }

        TradeManager.InviteStatus status = plugin.getTradeManager().sendInvite(player, target);
        switch (status) {
            case SUCCESS -> {
                return true;
            }
            case DISABLED -> player.sendMessage(plugin.getLocaleManager().getMessage("trade.disabled"));
            case SELF_TARGET -> player.sendMessage(plugin.getLocaleManager().getMessage("trade.self-target"));
            case TARGET_OFFLINE -> player.sendMessage(plugin.getLocaleManager().getMessage("trade.target-offline", "player", targetName));
            case TOO_FAR -> player.sendMessage(plugin.getLocaleManager().getMessage(
                "trade.too-far",
                "distance", String.valueOf(plugin.getConfigManager().getTradeMaxDistanceBlocks())
            ));
            case SENDER_BUSY -> player.sendMessage(plugin.getLocaleManager().getMessage("trade.sender-busy"));
            case TARGET_BUSY -> player.sendMessage(plugin.getLocaleManager().getMessage("trade.target-busy", "player", target.getName()));
        }
        return true;
    }

    private boolean handleTradeAccept(Player player) {
        TradeManager.RespondStatus status = plugin.getTradeManager().acceptInvite(player);
        switch (status) {
            case SUCCESS -> {
                return true;
            }
            case DISABLED -> player.sendMessage(plugin.getLocaleManager().getMessage("trade.disabled"));
            case NO_PENDING -> player.sendMessage(plugin.getLocaleManager().getMessage("trade.invite.none"));
            case SENDER_OFFLINE -> player.sendMessage(plugin.getLocaleManager().getMessage("trade.invite.sender-offline"));
            case TOO_FAR -> player.sendMessage(plugin.getLocaleManager().getMessage(
                "trade.too-far",
                "distance", String.valueOf(plugin.getConfigManager().getTradeMaxDistanceBlocks())
            ));
            case PLAYER_BUSY -> player.sendMessage(plugin.getLocaleManager().getMessage("trade.sender-busy"));
        }
        return true;
    }

    private boolean handleTradeDeny(Player player) {
        TradeManager.RespondStatus status = plugin.getTradeManager().denyInvite(player);
        switch (status) {
            case SUCCESS -> {
                return true;
            }
            case DISABLED -> player.sendMessage(plugin.getLocaleManager().getMessage("trade.disabled"));
            case NO_PENDING -> player.sendMessage(plugin.getLocaleManager().getMessage("trade.invite.none"));
            case SENDER_OFFLINE -> player.sendMessage(plugin.getLocaleManager().getMessage("trade.invite.sender-offline"));
            case TOO_FAR -> player.sendMessage(plugin.getLocaleManager().getMessage(
                "trade.too-far",
                "distance", String.valueOf(plugin.getConfigManager().getTradeMaxDistanceBlocks())
            ));
            case PLAYER_BUSY -> player.sendMessage(plugin.getLocaleManager().getMessage("trade.sender-busy"));
        }
        return true;
    }

    private boolean handleSellXp(Player player, String[] args) {
        if (!player.hasPermission("spawnerx.sell")) {
            player.sendMessage(plugin.getLocaleManager().getMessage("commands.no-permission"));
            return true;
        }

        if (args.length != 3 && args.length != 4) {
            sendSellUsage(player);
            return true;
        }

        int price;
        try {
            price = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getLocaleManager().getMessage("shop.sell.invalid-price"));
            return true;
        }

        if (price < 1) {
            player.sendMessage(plugin.getLocaleManager().getMessage("shop.sell.invalid-price"));
            return true;
        }

        int quantity = 1;
        if (args.length == 4) {
            try {
                quantity = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.getLocaleManager().getMessage("shop.sell.invalid-price"));
                return true;
            }
            if (quantity < 1) {
                player.sendMessage(plugin.getLocaleManager().getMessage("shop.sell.invalid-price"));
                return true;
            }
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage(plugin.getLocaleManager().getMessage("shop.sell.no-item"));
            return true;
        }
        if (hand.getAmount() < quantity) {
            player.sendMessage(plugin.getLocaleManager().getMessage("shop.sell.no-item"));
            return true;
        }

        if (plugin.getSpawnerManager().isLegacySpawnerWithBlockState(hand)) {
            ItemStack sanitized = plugin.getSpawnerManager().sanitizeSpawnerItem(hand);
            player.getInventory().setItemInMainHand(sanitized);
            hand = sanitized;
        }

        if (!plugin.getSpawnerManager().isValidSpawner(hand)) {
            player.sendMessage(plugin.getLocaleManager().getMessage("shop.sell.only-spawner"));
            return true;
        }

        PublicSalesManager.CreateSaleResult result = plugin.getPublicSalesManager().createSale(player, hand, price, quantity);
        switch (result.status()) {
            case DISABLED -> player.sendMessage(plugin.getLocaleManager().getMessage("shop.sell.disabled"));
            case INVALID_ITEM -> player.sendMessage(plugin.getLocaleManager().getMessage("shop.sell.only-spawner"));
            case INVALID_PRICE -> player.sendMessage(plugin.getLocaleManager().getMessage("shop.sell.invalid-price"));
            case LIMIT_REACHED -> player.sendMessage(plugin.getLocaleManager().getMessage("shop.sell.limit-reached",
                "max", String.valueOf(plugin.getConfigManager().getShopPublicSalesMaxActivePerPlayer())));
            case SUCCESS -> {
                consumeItemsFromHand(player, hand, quantity);

                PublicSale sale = result.sale();
                String typeName = sale == null
                    ? "Spawner"
                    : SpawnerUtils.getEntityDisplayName(sale.entityType());
                String expires = sale == null
                    ? "-"
                    : plugin.getPublicSalesManager().formatRemainingTime(sale);

                player.sendMessage(plugin.getLocaleManager().getMessage("shop.sell.success",
                    "type", typeName,
                    "price", String.valueOf(price),
                    "currency", plugin.getConfigManager().getShopCurrency(),
                    "amount", String.valueOf(quantity),
                    "expires", expires));
            }
        }

        return true;
    }

    private boolean handleSellStats(Player player, String[] args) {
        if (args.length == 2) {
            if (!player.hasPermission("spawnerx.sell")) {
                player.sendMessage(plugin.getLocaleManager().getMessage("commands.no-permission"));
                return true;
            }

            sendStatsPanel(player, player.getUniqueId(), player.getName(), false);
            return true;
        }

        if (args.length != 3) {
            sendSellUsage(player);
            return true;
        }

        if (!player.hasPermission("spawnerx.admin")) {
            player.sendMessage(plugin.getLocaleManager().getMessage("shop.sell.stats.no-permission-target"));
            return true;
        }

        StatsTarget target = resolveStatsTarget(args[2]);
        if (target == null) {
            player.sendMessage(plugin.getLocaleManager().getMessage("shop.sell.stats.target-not-found",
                "player", args[2]));
            return true;
        }

        PublicSalesManager publicSales = plugin.getPublicSalesManager();
        UUID targetUuid = target.uuid();
        boolean hasData = publicSales.hasPlayerStats(targetUuid)
            || publicSales.countSalesByPlayer(targetUuid) > 0
            || publicSales.getPendingXp(targetUuid) > 0;

        if (!hasData) {
            player.sendMessage(plugin.getLocaleManager().getMessage("shop.sell.stats.no-data",
                "player", target.displayName()));
            return true;
        }

        sendStatsPanel(player, target.uuid(), target.displayName(), true);
        return true;
    }

    private void sendStatsPanel(Player viewer, UUID targetUuid, String fallbackName, boolean targetView) {
        PublicSalesManager publicSales = plugin.getPublicSalesManager();
        PublicSalesManager.PlayerSellStats stats = publicSales.getPlayerStats(targetUuid);

        String displayName = fallbackName;
        if (displayName == null || displayName.isBlank()) {
            displayName = stats.lastKnownName();
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = targetUuid.toString();
        }

        String currency = plugin.getConfigManager().getShopCurrency();

        viewer.sendMessage(plugin.getLocaleManager().getMessage(
            targetView ? "shop.sell.stats.target-header" : "shop.sell.stats.header",
            "player", displayName
        ));
        viewer.sendMessage(plugin.getLocaleManager().getMessage(
            "shop.sell.stats.active-listings",
            "count", String.valueOf(publicSales.countSalesByPlayer(targetUuid))
        ));
        viewer.sendMessage(plugin.getLocaleManager().getMessage(
            "shop.sell.stats.pending-xp",
            "amount", String.valueOf(publicSales.getPendingXp(targetUuid)),
            "currency", currency
        ));
        viewer.sendMessage(plugin.getLocaleManager().getMessage(
            "shop.sell.stats.history-sold",
            "sales", String.valueOf(stats.salesCompleted()),
            "items", String.valueOf(stats.itemsSold()),
            "xp", String.valueOf(stats.xpEarnedTotal()),
            "currency", currency
        ));
        viewer.sendMessage(plugin.getLocaleManager().getMessage(
            "shop.sell.stats.history-bought",
            "purchases", String.valueOf(stats.purchasesCompleted()),
            "items", String.valueOf(stats.itemsBought()),
            "xp", String.valueOf(stats.xpSpentTotal()),
            "currency", currency
        ));
        viewer.sendMessage(plugin.getLocaleManager().getMessage(
            "shop.sell.stats.own-removed",
            "count", String.valueOf(stats.ownListingsRemoved())
        ));
    }

    private StatsTarget resolveStatsTarget(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        Player online = Bukkit.getPlayerExact(input);
        if (online == null) {
            online = Bukkit.getPlayer(input);
        }
        if (online != null) {
            return new StatsTarget(online.getUniqueId(), online.getName());
        }

        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(input);
        if (cached != null) {
            String name = cached.getName();
            if (name == null || name.isBlank()) {
                name = input;
            }
            return new StatsTarget(cached.getUniqueId(), name);
        }

        UUID statsUuid = plugin.getPublicSalesManager().findPlayerByLastKnownName(input);
        if (statsUuid != null) {
            PublicSalesManager.PlayerSellStats stats = plugin.getPublicSalesManager().getPlayerStats(statsUuid);
            String name = stats.lastKnownName();
            if (name == null || name.isBlank()) {
                name = input;
            }
            return new StatsTarget(statsUuid, name);
        }

        return null;
    }

    private void sendSellUsage(CommandSender sender) {
        sender.sendMessage(plugin.getLocaleManager().getMessage(
            "general.invalid-usage",
            "usage", plugin.getLocaleManager().getMessage("shop.sell.stats.usage")
        ));
    }

    private void consumeItemsFromHand(Player player, ItemStack hand, int quantity) {
        int remaining = hand.getAmount() - quantity;
        if (remaining <= 0) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            return;
        }
        hand.setAmount(remaining);
    }

    private void sendHelp(CommandSender sender) {
        if (plugin.isLicenseLocked()) {
            sendLicenseLockedInfo(sender);
            return;
        }
        sender.sendMessage(plugin.getLocaleManager().getMessage("commands.help.header"));
        sender.sendMessage(plugin.getLocaleManager().getMessage("commands.help.reload"));
        sender.sendMessage(plugin.getLocaleManager().getMessage("commands.help.license"));
        sender.sendMessage(plugin.getLocaleManager().getMessage("commands.help.admin"));
        sender.sendMessage(plugin.getLocaleManager().getMessage("commands.help.shop"));
        sender.sendMessage(plugin.getLocaleManager().getMessage("commands.help.sell"));
        sender.sendMessage(plugin.getLocaleManager().getMessage("commands.help.trade"));
        sender.sendMessage(plugin.getLocaleManager().getMessage("commands.help.help"));
        sender.sendMessage(plugin.getLocaleManager().getMessage("commands.help.footer"));
    }

    private void sendLicenseLockedInfo(CommandSender sender) {
        sender.sendMessage(plugin.getLocaleManager().getMessage("license.locked"));
        sender.sendMessage(plugin.getLocaleManager().getMessage("license.allowed-commands"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (plugin.isLicenseLocked()) {
            if (args.length == 1) {
                return filterPrefix(Arrays.asList("reload", "license"), args[0]);
            }
            if (args[0].equalsIgnoreCase("license")) {
                if (args.length == 2) {
                    return filterPrefix(List.of("key", "status"), args[1]);
                }
                if (args.length == 3 && args[1].equalsIgnoreCase("key")) {
                    return new ArrayList<>();
                }
            }
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return filterPrefix(Arrays.asList("reload", "license", "admin", "shop", "sell", "trade", "help"), args[0]);
        }

        if (args[0].equalsIgnoreCase("license")) {
            if (args.length == 2) {
                return filterPrefix(List.of("key", "status"), args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("key")) {
                return new ArrayList<>();
            }
            return new ArrayList<>();
        }

        if (args[0].equalsIgnoreCase("admin")) {
            return new ArrayList<>();
        }

        if (args[0].equalsIgnoreCase("sell")) {
            if (args.length == 2) {
                return filterPrefix(List.of("xp", "stats"), args[1]);
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("xp")) {
                return filterPrefix(List.of("<valor>"), args[2]);
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("xp")) {
                return filterPrefix(List.of("<quantidade>"), args[3]);
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("stats")) {
                if (!sender.hasPermission("spawnerx.admin")) {
                    return new ArrayList<>();
                }
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }

        if (args[0].equalsIgnoreCase("trade")) {
            if (args.length == 2) {
                List<String> options = new ArrayList<>();
                options.add("accept");
                options.add("deny");
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.equals(sender)) {
                        options.add(online.getName());
                    }
                }
                return filterPrefix(options, args[1]);
            }
            return new ArrayList<>();
        }

        return new ArrayList<>();
    }

    private List<String> filterPrefix(List<String> values, String prefix) {
        return values.stream()
            .filter(value -> value.toLowerCase().startsWith(prefix.toLowerCase()))
            .collect(Collectors.toList());
    }

    private record StatsTarget(UUID uuid, String displayName) {}
}

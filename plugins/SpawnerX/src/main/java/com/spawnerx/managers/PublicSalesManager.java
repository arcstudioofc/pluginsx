package com.spawnerx.managers;

import com.spawnerx.SpawnerX;
import com.spawnerx.utils.ExperienceUtils;
import com.spawnerx.utils.SpawnerUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gerencia o marketplace de vendas publicas de spawners.
 */
public class PublicSalesManager {

    private final SpawnerX plugin;
    private final SalesStorage storage;

    private final Map<String, PublicSale> activeSales = new HashMap<>();
    private final Map<UUID, Integer> pendingXp = new HashMap<>();
    private final Map<UUID, PlayerSellStats> playerStats = new HashMap<>();
    private final Map<UUID, List<PendingSpawnerReturn>> pendingExpiredReturns = new HashMap<>();
    private boolean storageAvailable = true;
    private boolean storageFailureLogged = false;

    private BukkitTask cleanupTask;

    public PublicSalesManager(SpawnerX plugin) {
        this.plugin = plugin;
        SalesStorage initializedStorage = null;
        try {
            initializedStorage = new SalesStorage(plugin);
        } catch (Exception e) {
            this.storage = null;
            handleStorageFailure("Falha ao inicializar storage de vendas públicas.", e);
            return;
        }
        this.storage = initializedStorage;

        try {
            SalesStorage.StorageData data = storage.load();
            activeSales.putAll(data.sales());
            pendingXp.putAll(data.pendingXp());
            playerStats.putAll(data.stats());
            pendingExpiredReturns.putAll(data.pendingExpiredReturns());

            removeExpiredSales();
            saveState();
        } catch (Exception e) {
            handleStorageFailure("Falha ao carregar dados de vendas públicas.", e);
        }
    }

    public boolean isEnabled() {
        return storageAvailable
            && plugin.isLicenseValid()
            && plugin.getConfigManager().isShopEnabled()
            && plugin.getConfigManager().isShopPublicSalesEnabled();
    }

    public int getMenuSlot() {
        return plugin.getConfigManager().getShopPublicSalesMenuSlot();
    }

    public List<PublicSale> getActiveSales() {
        if (storageAvailable) {
            removeExpiredSales();
        }
        List<PublicSale> list = new ArrayList<>(activeSales.values());
        list.sort(Comparator.comparingLong(PublicSale::createdAt).reversed());
        return list;
    }

    public int countSalesByPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return 0;
        }
        if (storageAvailable) {
            removeExpiredSales();
        }

        int count = 0;
        for (PublicSale sale : activeSales.values()) {
            if (sale.sellerUuid().equals(playerUuid)) {
                count++;
            }
        }
        return count;
    }

    public int getPendingXp(UUID playerUuid) {
        if (playerUuid == null) {
            return 0;
        }
        return Math.max(0, pendingXp.getOrDefault(playerUuid, 0));
    }

    public boolean hasPlayerStats(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }
        return playerStats.containsKey(playerUuid);
    }

    public PlayerSellStats getPlayerStats(UUID playerUuid) {
        if (playerUuid == null) {
            return PlayerSellStats.empty("");
        }
        return playerStats.getOrDefault(playerUuid, PlayerSellStats.empty(""));
    }

    public UUID findPlayerByLastKnownName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }
        String target = playerName.trim();
        for (Map.Entry<UUID, PlayerSellStats> entry : playerStats.entrySet()) {
            String knownName = entry.getValue().lastKnownName();
            if (knownName != null && knownName.equalsIgnoreCase(target)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public CreateSaleResult createSale(Player seller, ItemStack handItem, int priceXp, int quantity) {
        if (!isEnabled()) {
            return new CreateSaleResult(CreateSaleStatus.DISABLED, null);
        }
        if (priceXp < 1) {
            return new CreateSaleResult(CreateSaleStatus.INVALID_PRICE, null);
        }
        if (quantity < 1) {
            return new CreateSaleResult(CreateSaleStatus.INVALID_ITEM, null);
        }
        if (!plugin.getSpawnerManager().isValidSpawner(handItem)) {
            return new CreateSaleResult(CreateSaleStatus.INVALID_ITEM, null);
        }

        removeExpiredSales();

        int maxActive = Math.max(1, plugin.getConfigManager().getShopPublicSalesMaxActivePerPlayer());
        if (countSalesByPlayer(seller.getUniqueId()) >= maxActive) {
            return new CreateSaleResult(CreateSaleStatus.LIMIT_REACHED, null);
        }

        EntityType entityType = plugin.getSpawnerManager().getSpawnerEntity(handItem);
        if (entityType == null || !SpawnerUtils.isMobEntityType(entityType)) {
            return new CreateSaleResult(CreateSaleStatus.INVALID_ITEM, null);
        }

        int stackSize = Math.max(1, plugin.getSpawnerManager().getSpawnerStack(handItem));
        int level = Math.max(0, plugin.getSpawnerManager().getSpawnerLevel(handItem));
        long now = System.currentTimeMillis();
        long expiresAt = now + (Math.max(1, plugin.getConfigManager().getShopPublicSalesExpirationHours()) * 3_600_000L);

        String id = UUID.randomUUID().toString().replace("-", "");
        PublicSale sale = new PublicSale(
            id,
            seller.getUniqueId(),
            seller.getName(),
            entityType,
            stackSize,
            level,
            quantity,
            priceXp,
            now,
            expiresAt
        );

        activeSales.put(id, sale);
        upsertLastKnownName(seller.getUniqueId(), seller.getName());
        saveState();

        return new CreateSaleResult(CreateSaleStatus.SUCCESS, sale);
    }

    public PurchaseResult buySale(Player buyer, String saleId) {
        if (!isEnabled()) {
            return new PurchaseResult(PurchaseStatus.DISABLED, null);
        }

        removeExpiredSales();
        PublicSale sale = activeSales.get(saleId);
        if (sale == null) {
            return new PurchaseResult(PurchaseStatus.NOT_FOUND, null);
        }

        int itemAmount = Math.max(1, sale.itemAmount());

        if (buyer.getUniqueId().equals(sale.sellerUuid())) {
            giveSpawnerToPlayer(buyer, sale.entityType(), sale.stackSize(), sale.level(), itemAmount);

            activeSales.remove(saleId);
            applyStatsDelta(
                sale.sellerUuid(),
                buyer.getName(),
                0,
                0,
                0,
                0,
                0,
                0,
                1
            );
            saveState();
            return new PurchaseResult(PurchaseStatus.OWN_SALE_REMOVED, sale);
        }

        int totalXp = ExperienceUtils.getTotalExperience(buyer);
        if (totalXp < sale.priceXp()) {
            return new PurchaseResult(PurchaseStatus.NOT_ENOUGH_XP, sale);
        }

        ExperienceUtils.setTotalExperience(buyer, totalXp - sale.priceXp());

        giveSpawnerToPlayer(buyer, sale.entityType(), sale.stackSize(), sale.level(), itemAmount);

        activeSales.remove(saleId);
        applyStatsDelta(
            sale.sellerUuid(),
            sale.sellerName(),
            1,
            itemAmount,
            sale.priceXp(),
            0,
            0,
            0,
            0
        );
        applyStatsDelta(
            buyer.getUniqueId(),
            buyer.getName(),
            0,
            0,
            0,
            1,
            itemAmount,
            sale.priceXp(),
            0
        );
        creditSeller(sale, buyer.getName());
        saveState();

        return new PurchaseResult(PurchaseStatus.SUCCESS, sale);
    }

    public ExpiredReturnPayout payoutPendingExpiredReturns(Player player) {
        if (!plugin.isLicenseValid()) {
            return ExpiredReturnPayout.empty();
        }
        if (!storageAvailable) {
            return ExpiredReturnPayout.empty();
        }
        if (player == null) {
            return ExpiredReturnPayout.empty();
        }

        List<PendingSpawnerReturn> pending = pendingExpiredReturns.remove(player.getUniqueId());
        if (pending == null || pending.isEmpty()) {
            return ExpiredReturnPayout.empty();
        }

        int listingsReturned = 0;
        int itemsReturned = 0;

        for (PendingSpawnerReturn pendingReturn : pending) {
            if (pendingReturn == null || pendingReturn.entityType() == null) {
                continue;
            }

            int amount = Math.max(1, pendingReturn.itemAmount());
            giveSpawnerToPlayer(
                player,
                pendingReturn.entityType(),
                pendingReturn.stackSize(),
                pendingReturn.level(),
                amount
            );
            listingsReturned = safeAdd(listingsReturned, 1);
            itemsReturned = safeAdd(itemsReturned, amount);
        }

        if (listingsReturned <= 0) {
            saveState();
            return ExpiredReturnPayout.empty();
        }

        upsertLastKnownName(player.getUniqueId(), player.getName());
        saveState();
        return new ExpiredReturnPayout(listingsReturned, itemsReturned);
    }

    public int payoutPending(Player player) {
        if (!plugin.isLicenseValid()) {
            return 0;
        }
        if (!storageAvailable) {
            return 0;
        }
        Integer amount = pendingXp.remove(player.getUniqueId());
        if (amount == null || amount <= 0) {
            return 0;
        }

        int current = ExperienceUtils.getTotalExperience(player);
        ExperienceUtils.setTotalExperience(player, current + amount);
        upsertLastKnownName(player.getUniqueId(), player.getName());
        saveState();
        return amount;
    }

    public String formatRemainingTime(PublicSale sale) {
        long remaining = Math.max(0L, sale.expiresAt() - System.currentTimeMillis());
        long totalMinutes = (long) Math.ceil(remaining / 60000.0D);
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;

        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1L, minutes) + "m";
    }

    public void startCleanupTask() {
        stopCleanupTask();

        long minutes = Math.max(1L, plugin.getConfigManager().getShopPublicSalesAutoCleanupMinutes());
        long intervalTicks = minutes * 60L * 20L;

        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::removeExpiredSales, intervalTicks, intervalTicks);
    }

    public void stopCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    public void reloadSettings() {
        if (!storageAvailable || !plugin.isLicenseValid()) {
            stopCleanupTask();
            return;
        }
        removeExpiredSales();
        startCleanupTask();
    }

    public void shutdown() {
        stopCleanupTask();
        saveState();
    }

    private void creditSeller(PublicSale sale, String buyerName) {
        Player seller = Bukkit.getPlayer(sale.sellerUuid());
        if (seller != null && seller.isOnline()) {
            int totalXp = ExperienceUtils.getTotalExperience(seller);
            ExperienceUtils.setTotalExperience(seller, totalXp + sale.priceXp());
            upsertLastKnownName(seller.getUniqueId(), seller.getName());
            seller.sendMessage(plugin.getLocaleManager().getMessage(
                "shop.payout.received-live",
                "amount", String.valueOf(sale.priceXp()),
                "buyer", buyerName,
                "currency", plugin.getConfigManager().getShopCurrency()
            ));
            return;
        }

        upsertLastKnownName(sale.sellerUuid(), sale.sellerName());
        pendingXp.merge(sale.sellerUuid(), sale.priceXp(), Integer::sum);
    }

    private int removeExpiredSales() {
        if (!storageAvailable) {
            return 0;
        }
        long now = System.currentTimeMillis();
        List<PublicSale> expiredSales = new ArrayList<>();

        for (Map.Entry<String, PublicSale> entry : activeSales.entrySet()) {
            if (entry.getValue().isExpired(now)) {
                expiredSales.add(entry.getValue());
            }
        }

        for (PublicSale sale : expiredSales) {
            activeSales.remove(sale.id());
            handleExpiredSale(sale);
        }

        if (!expiredSales.isEmpty()) {
            saveState();
        }

        return expiredSales.size();
    }

    private void saveState() {
        if (!storageAvailable || storage == null) {
            return;
        }
        try {
            storage.save(activeSales, pendingXp, playerStats, pendingExpiredReturns);
        } catch (Exception e) {
            handleStorageFailure("Falha ao persistir estado de vendas públicas.", e);
        }
    }

    private void handleStorageFailure(String message, Exception error) {
        storageAvailable = false;
        stopCleanupTask();

        if (storageFailureLogged) {
            return;
        }
        storageFailureLogged = true;

        plugin.getLogger().severe(message);
        if (storage != null) {
            plugin.getLogger().severe("Storage SQLite indisponível em: " + storage.getDatabasePath());
        }
        if (error != null && error.getMessage() != null) {
            plugin.getLogger().severe("Motivo: " + error.getMessage());
        }
    }

    private void handleExpiredSale(PublicSale sale) {
        if (sale == null) {
            return;
        }

        int amount = Math.max(1, sale.itemAmount());
        Player seller = Bukkit.getPlayer(sale.sellerUuid());

        if (seller != null && seller.isOnline()) {
            giveSpawnerToPlayer(seller, sale.entityType(), sale.stackSize(), sale.level(), amount);
            upsertLastKnownName(seller.getUniqueId(), seller.getName());
            notifyExpiredReturn(seller, sale, amount);
            return;
        }

        upsertLastKnownName(sale.sellerUuid(), sale.sellerName());
        pendingExpiredReturns.computeIfAbsent(sale.sellerUuid(), ignored -> new ArrayList<>())
            .add(new PendingSpawnerReturn(sale.entityType(), sale.stackSize(), sale.level(), amount));
    }

    private void notifyExpiredReturn(Player seller, PublicSale sale, int amount) {
        if (plugin.getLocaleManager().getLocaleConfig() == null) {
            return;
        }

        String displayName = plugin.getSpawnerManager().formatSpawnerDisplayName(
            sale.entityType(),
            sale.stackSize(),
            sale.level()
        );
        seller.sendMessage(plugin.getLocaleManager().getMessage(
            "shop.sell.expired-returned",
            "name", displayName,
            "items", String.valueOf(Math.max(1, amount))
        ));
    }

    private void giveSpawnerToPlayer(Player player, EntityType entityType, int stackSize, int level, int amount) {
        int remaining = Math.max(1, amount);

        while (remaining > 0) {
            ItemStack spawnerItem = plugin.getSpawnerManager().createSpawner(entityType, stackSize, level);
            int maxStackSize = Math.max(1, spawnerItem.getMaxStackSize());
            int splitAmount = Math.min(maxStackSize, remaining);
            spawnerItem.setAmount(splitAmount);
            addOrDrop(player, spawnerItem);
            remaining -= splitAmount;
        }
    }

    private void addOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private void upsertLastKnownName(UUID playerUuid, String playerName) {
        if (playerUuid == null) {
            return;
        }

        String normalizedName = normalizeName(playerName);
        playerStats.compute(playerUuid, (ignored, current) -> {
            PlayerSellStats base = current == null ? PlayerSellStats.empty(normalizedName) : current;
            String resolvedName = resolveLastKnownName(base.lastKnownName(), normalizedName);
            if (resolvedName.equals(base.lastKnownName())) {
                return base;
            }
            return new PlayerSellStats(
                base.salesCompleted(),
                base.itemsSold(),
                base.xpEarnedTotal(),
                base.purchasesCompleted(),
                base.itemsBought(),
                base.xpSpentTotal(),
                base.ownListingsRemoved(),
                resolvedName
            );
        });
    }

    private void applyStatsDelta(
        UUID playerUuid,
        String playerName,
        int salesCompleted,
        int itemsSold,
        int xpEarnedTotal,
        int purchasesCompleted,
        int itemsBought,
        int xpSpentTotal,
        int ownListingsRemoved
    ) {
        if (playerUuid == null) {
            return;
        }

        String normalizedName = normalizeName(playerName);
        playerStats.compute(playerUuid, (ignored, current) -> {
            PlayerSellStats base = current == null ? PlayerSellStats.empty(normalizedName) : current;
            return new PlayerSellStats(
                safeAdd(base.salesCompleted(), salesCompleted),
                safeAdd(base.itemsSold(), itemsSold),
                safeAdd(base.xpEarnedTotal(), xpEarnedTotal),
                safeAdd(base.purchasesCompleted(), purchasesCompleted),
                safeAdd(base.itemsBought(), itemsBought),
                safeAdd(base.xpSpentTotal(), xpSpentTotal),
                safeAdd(base.ownListingsRemoved(), ownListingsRemoved),
                resolveLastKnownName(base.lastKnownName(), normalizedName)
            );
        });
    }

    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim();
    }

    private String resolveLastKnownName(String currentName, String candidateName) {
        if (candidateName != null && !candidateName.isBlank()) {
            return candidateName;
        }
        if (currentName == null || currentName.isBlank()) {
            return "";
        }
        return currentName;
    }

    private int safeAdd(int current, int delta) {
        if (delta <= 0) {
            return Math.max(0, current);
        }
        long sum = (long) current + delta;
        if (sum > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) sum;
    }

    public record PlayerSellStats(
        int salesCompleted,
        int itemsSold,
        int xpEarnedTotal,
        int purchasesCompleted,
        int itemsBought,
        int xpSpentTotal,
        int ownListingsRemoved,
        String lastKnownName
    ) {
        public static PlayerSellStats empty(String lastKnownName) {
            return new PlayerSellStats(0, 0, 0, 0, 0, 0, 0, lastKnownName == null ? "" : lastKnownName);
        }
    }

    public record PendingSpawnerReturn(
        EntityType entityType,
        int stackSize,
        int level,
        int itemAmount
    ) {}

    public record ExpiredReturnPayout(int listingsReturned, int itemsReturned) {
        public static ExpiredReturnPayout empty() {
            return new ExpiredReturnPayout(0, 0);
        }
    }

    public enum CreateSaleStatus {
        SUCCESS,
        DISABLED,
        INVALID_ITEM,
        INVALID_PRICE,
        LIMIT_REACHED
    }

    public record CreateSaleResult(CreateSaleStatus status, PublicSale sale) {}

    public enum PurchaseStatus {
        SUCCESS,
        DISABLED,
        NOT_FOUND,
        NOT_ENOUGH_XP,
        OWN_SALE_REMOVED
    }

    public record PurchaseResult(PurchaseStatus status, PublicSale sale) {}
}

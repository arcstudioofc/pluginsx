package com.spawnerx.managers;

import com.spawnerx.SpawnerX;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistencia em SQLite para anuncios publicos, pagamentos pendentes e devolucoes pendentes.
 */
public class SalesStorage {

    private static final String CREATE_META_TABLE = """
        CREATE TABLE IF NOT EXISTS meta (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
        """;
    private static final String CREATE_SALES_TABLE = """
        CREATE TABLE IF NOT EXISTS sales (
            id TEXT PRIMARY KEY,
            seller_uuid TEXT NOT NULL,
            seller_name TEXT NOT NULL,
            entity_type TEXT NOT NULL,
            stack_size INTEGER NOT NULL,
            level INTEGER NOT NULL,
            item_amount INTEGER NOT NULL,
            price_xp INTEGER NOT NULL,
            created_at INTEGER NOT NULL,
            expires_at INTEGER NOT NULL
        )
        """;
    private static final String CREATE_PENDING_XP_TABLE = """
        CREATE TABLE IF NOT EXISTS pending_xp (
            player_uuid TEXT PRIMARY KEY,
            amount INTEGER NOT NULL
        )
        """;
    private static final String CREATE_PLAYER_STATS_TABLE = """
        CREATE TABLE IF NOT EXISTS player_stats (
            player_uuid TEXT PRIMARY KEY,
            sales_completed INTEGER NOT NULL,
            items_sold INTEGER NOT NULL,
            xp_earned_total INTEGER NOT NULL,
            purchases_completed INTEGER NOT NULL,
            items_bought INTEGER NOT NULL,
            xp_spent_total INTEGER NOT NULL,
            own_listings_removed INTEGER NOT NULL,
            last_known_name TEXT NOT NULL
        )
        """;
    private static final String CREATE_PENDING_EXPIRED_ITEMS_TABLE = """
        CREATE TABLE IF NOT EXISTS pending_expired_items (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            player_uuid TEXT NOT NULL,
            entity_type TEXT NOT NULL,
            stack_size INTEGER NOT NULL,
            level INTEGER NOT NULL,
            item_amount INTEGER NOT NULL
        )
        """;
    private static final String CREATE_PENDING_EXPIRED_ITEMS_PLAYER_INDEX = """
        CREATE INDEX IF NOT EXISTS idx_pending_expired_items_player_uuid
        ON pending_expired_items(player_uuid)
        """;

    private static final String SQL_BEGIN_IMMEDIATE = "BEGIN IMMEDIATE";
    private static final String SQL_COMMIT = "COMMIT";
    private static final String SQL_ROLLBACK = "ROLLBACK";

    private static final String SQL_DELETE_SALES = "DELETE FROM sales";
    private static final String SQL_DELETE_PENDING_XP = "DELETE FROM pending_xp";
    private static final String SQL_DELETE_PLAYER_STATS = "DELETE FROM player_stats";
    private static final String SQL_DELETE_PENDING_EXPIRED_ITEMS = "DELETE FROM pending_expired_items";

    private static final String SQL_INSERT_SALE = """
        INSERT INTO sales (
            id, seller_uuid, seller_name, entity_type, stack_size, level, item_amount, price_xp, created_at, expires_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    private static final String SQL_INSERT_PENDING_XP = """
        INSERT INTO pending_xp (player_uuid, amount) VALUES (?, ?)
        """;
    private static final String SQL_INSERT_PLAYER_STATS = """
        INSERT INTO player_stats (
            player_uuid, sales_completed, items_sold, xp_earned_total, purchases_completed, items_bought,
            xp_spent_total, own_listings_removed, last_known_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    private static final String SQL_INSERT_PENDING_EXPIRED_ITEM = """
        INSERT INTO pending_expired_items (player_uuid, entity_type, stack_size, level, item_amount)
        VALUES (?, ?, ?, ?, ?)
        """;

    private static final String SQL_SELECT_SALES = """
        SELECT id, seller_uuid, seller_name, entity_type, stack_size, level, item_amount, price_xp, created_at, expires_at
        FROM sales
        """;
    private static final String SQL_SELECT_PENDING_XP = "SELECT player_uuid, amount FROM pending_xp";
    private static final String SQL_SELECT_PLAYER_STATS = """
        SELECT player_uuid, sales_completed, items_sold, xp_earned_total, purchases_completed, items_bought,
               xp_spent_total, own_listings_removed, last_known_name
        FROM player_stats
        """;
    private static final String SQL_SELECT_PENDING_EXPIRED_ITEMS = """
        SELECT id, player_uuid, entity_type, stack_size, level, item_amount
        FROM pending_expired_items
        ORDER BY id ASC
        """;

    private final SpawnerX plugin;
    private final File databaseFile;
    private final File legacySalesFile;
    private final String jdbcUrl;

    public SalesStorage(SpawnerX plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(new File(plugin.getDataFolder(), "database"), "sales.db");
        this.legacySalesFile = new File(plugin.getDataFolder(), "sales.yml");
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();

        ensureDatabaseFolder();
        initializeDatabase();
        migrateLegacyYamlIfNeeded();
    }

    public String getDatabasePath() {
        return databaseFile.getAbsolutePath();
    }

    public StorageData load() {
        try (Connection connection = openConnection()) {
            Map<String, PublicSale> sales = loadSalesFromDatabase(connection);
            Map<UUID, Integer> pendingXp = loadPendingXpFromDatabase(connection);
            Map<UUID, PublicSalesManager.PlayerSellStats> stats = loadStatsFromDatabase(connection);
            Map<UUID, List<PublicSalesManager.PendingSpawnerReturn>> pendingExpiredReturns =
                loadPendingExpiredItemsFromDatabase(connection);
            return new StorageData(sales, pendingXp, stats, pendingExpiredReturns);
        } catch (SQLException e) {
            throw new StorageException("Erro ao carregar dados do SQLite (" + getDatabasePath() + ")", e);
        }
    }

    public void save(
        Map<String, PublicSale> sales,
        Map<UUID, Integer> pendingXp,
        Map<UUID, PublicSalesManager.PlayerSellStats> stats,
        Map<UUID, List<PublicSalesManager.PendingSpawnerReturn>> pendingExpiredReturns
    ) {
        try (Connection connection = openConnection()) {
            beginImmediate(connection);
            try {
                clearTables(connection);
                saveSales(connection, sales);
                savePendingXp(connection, pendingXp);
                saveStats(connection, stats);
                savePendingExpiredReturns(connection, pendingExpiredReturns);
                commit(connection);
            } catch (SQLException e) {
                rollback(connection);
                throw e;
            }
        } catch (SQLException e) {
            throw new StorageException("Erro ao salvar dados no SQLite (" + getDatabasePath() + ")", e);
        }
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
        }
        return connection;
    }

    private void initializeDatabase() {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute(CREATE_META_TABLE);
            statement.execute(CREATE_SALES_TABLE);
            statement.execute(CREATE_PENDING_XP_TABLE);
            statement.execute(CREATE_PLAYER_STATS_TABLE);
            statement.execute(CREATE_PENDING_EXPIRED_ITEMS_TABLE);
            statement.execute(CREATE_PENDING_EXPIRED_ITEMS_PLAYER_INDEX);
        } catch (SQLException e) {
            throw new StorageException("Erro ao inicializar schema SQLite (" + getDatabasePath() + ")", e);
        }
    }

    private void migrateLegacyYamlIfNeeded() {
        if (!legacySalesFile.exists()) {
            return;
        }

        boolean databaseEmpty;
        try (Connection connection = openConnection()) {
            databaseEmpty = isDatabaseEmpty(connection);
        } catch (SQLException e) {
            throw new StorageException("Erro ao verificar estado inicial do SQLite (" + getDatabasePath() + ")", e);
        }

        if (!databaseEmpty) {
            return;
        }

        StorageData legacyData = loadLegacyYamlData();
        save(legacyData.sales(), legacyData.pendingXp(), legacyData.stats(), legacyData.pendingExpiredReturns());
        backupLegacyFile();
        plugin.getLogger().info("sales.yml migrado automaticamente para database/sales.db.");
    }

    private boolean isDatabaseEmpty(Connection connection) throws SQLException {
        return countRows(connection, "sales") == 0
            && countRows(connection, "pending_xp") == 0
            && countRows(connection, "player_stats") == 0
            && countRows(connection, "pending_expired_items") == 0;
    }

    private int countRows(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
            return 0;
        }
    }

    private void backupLegacyFile() {
        File parent = legacySalesFile.getParentFile();
        if (parent == null) {
            return;
        }

        File backup = new File(parent, "sales.yml.bak");
        if (backup.exists()) {
            backup = new File(parent, "sales.yml.bak." + System.currentTimeMillis());
        }

        if (!legacySalesFile.renameTo(backup)) {
            plugin.getLogger().warning("Migração concluída, mas não foi possível criar backup de sales.yml em " + backup.getName());
        }
    }

    private StorageData loadLegacyYamlData() {
        FileConfiguration config = YamlConfiguration.loadConfiguration(legacySalesFile);
        Map<String, PublicSale> sales = loadSales(config.getConfigurationSection("sales"));
        Map<UUID, Integer> pendingXp = loadPendingXp(config.getConfigurationSection("pending-xp"));
        Map<UUID, PublicSalesManager.PlayerSellStats> stats = loadStats(config.getConfigurationSection("stats"));
        Map<UUID, List<PublicSalesManager.PendingSpawnerReturn>> pendingExpiredReturns =
            loadPendingExpiredReturns(config.getConfigurationSection("pending-expired-items"));
        return new StorageData(sales, pendingXp, stats, pendingExpiredReturns);
    }

    private Map<String, PublicSale> loadSalesFromDatabase(Connection connection) throws SQLException {
        Map<String, PublicSale> sales = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(SQL_SELECT_SALES);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String id = resultSet.getString("id");
                String sellerUuidRaw = resultSet.getString("seller_uuid");
                String sellerName = resultSet.getString("seller_name");
                String entityTypeRaw = resultSet.getString("entity_type");

                UUID sellerUuid;
                EntityType entityType;
                try {
                    sellerUuid = UUID.fromString(sellerUuidRaw);
                    entityType = EntityType.valueOf(entityTypeRaw);
                } catch (Exception ignored) {
                    plugin.getLogger().warning("Venda publica invalida ignorada no SQLite: " + id);
                    continue;
                }

                int stackSize = Math.max(1, resultSet.getInt("stack_size"));
                int level = Math.max(0, resultSet.getInt("level"));
                int itemAmount = Math.max(1, resultSet.getInt("item_amount"));
                int priceXp = Math.max(1, resultSet.getInt("price_xp"));
                long createdAt = resultSet.getLong("created_at");
                long expiresAt = resultSet.getLong("expires_at");
                if (createdAt <= 0L) {
                    createdAt = System.currentTimeMillis();
                }
                if (expiresAt <= 0L) {
                    expiresAt = createdAt;
                }

                sales.put(id, new PublicSale(
                    id,
                    sellerUuid,
                    sellerName == null ? "Unknown" : sellerName,
                    entityType,
                    stackSize,
                    level,
                    itemAmount,
                    priceXp,
                    createdAt,
                    expiresAt
                ));
            }
        }
        return sales;
    }

    private Map<UUID, Integer> loadPendingXpFromDatabase(Connection connection) throws SQLException {
        Map<UUID, Integer> pending = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(SQL_SELECT_PENDING_XP);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String uuidRaw = resultSet.getString("player_uuid");
                int amount = Math.max(0, resultSet.getInt("amount"));
                if (amount <= 0) {
                    continue;
                }
                try {
                    pending.put(UUID.fromString(uuidRaw), amount);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("UUID invalido em pending_xp ignorado: " + uuidRaw);
                }
            }
        }
        return pending;
    }

    private Map<UUID, PublicSalesManager.PlayerSellStats> loadStatsFromDatabase(Connection connection) throws SQLException {
        Map<UUID, PublicSalesManager.PlayerSellStats> stats = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(SQL_SELECT_PLAYER_STATS);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String uuidRaw = resultSet.getString("player_uuid");

                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidRaw);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("UUID invalido em player_stats ignorado: " + uuidRaw);
                    continue;
                }

                stats.put(uuid, new PublicSalesManager.PlayerSellStats(
                    Math.max(0, resultSet.getInt("sales_completed")),
                    Math.max(0, resultSet.getInt("items_sold")),
                    Math.max(0, resultSet.getInt("xp_earned_total")),
                    Math.max(0, resultSet.getInt("purchases_completed")),
                    Math.max(0, resultSet.getInt("items_bought")),
                    Math.max(0, resultSet.getInt("xp_spent_total")),
                    Math.max(0, resultSet.getInt("own_listings_removed")),
                    safeText(resultSet.getString("last_known_name"))
                ));
            }
        }
        return stats;
    }

    private Map<UUID, List<PublicSalesManager.PendingSpawnerReturn>> loadPendingExpiredItemsFromDatabase(Connection connection) throws SQLException {
        Map<UUID, List<PublicSalesManager.PendingSpawnerReturn>> pending = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(SQL_SELECT_PENDING_EXPIRED_ITEMS);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String uuidRaw = resultSet.getString("player_uuid");
                String entityTypeRaw = resultSet.getString("entity_type");

                UUID uuid;
                EntityType entityType;
                try {
                    uuid = UUID.fromString(uuidRaw);
                    entityType = EntityType.valueOf(entityTypeRaw);
                } catch (Exception ignored) {
                    plugin.getLogger().warning("Entrada invalida em pending_expired_items ignorada: " + uuidRaw);
                    continue;
                }

                pending.computeIfAbsent(uuid, ignored -> new ArrayList<>()).add(
                    new PublicSalesManager.PendingSpawnerReturn(
                        entityType,
                        Math.max(1, resultSet.getInt("stack_size")),
                        Math.max(0, resultSet.getInt("level")),
                        Math.max(1, resultSet.getInt("item_amount"))
                    )
                );
            }
        }
        return pending;
    }

    private void clearTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(SQL_DELETE_SALES);
            statement.executeUpdate(SQL_DELETE_PENDING_XP);
            statement.executeUpdate(SQL_DELETE_PLAYER_STATS);
            statement.executeUpdate(SQL_DELETE_PENDING_EXPIRED_ITEMS);
        }
    }

    private void saveSales(Connection connection, Map<String, PublicSale> sales) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_INSERT_SALE)) {
            for (Map.Entry<String, PublicSale> entry : sales.entrySet()) {
                PublicSale sale = entry.getValue();
                if (sale == null || sale.sellerUuid() == null || sale.entityType() == null) {
                    continue;
                }

                statement.setString(1, safeText(sale.id()));
                statement.setString(2, sale.sellerUuid().toString());
                statement.setString(3, safeText(sale.sellerName()));
                statement.setString(4, sale.entityType().name());
                statement.setInt(5, Math.max(1, sale.stackSize()));
                statement.setInt(6, Math.max(0, sale.level()));
                statement.setInt(7, Math.max(1, sale.itemAmount()));
                statement.setInt(8, Math.max(1, sale.priceXp()));
                statement.setLong(9, Math.max(1L, sale.createdAt()));
                statement.setLong(10, Math.max(1L, sale.expiresAt()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void savePendingXp(Connection connection, Map<UUID, Integer> pendingXp) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_INSERT_PENDING_XP)) {
            for (Map.Entry<UUID, Integer> entry : pendingXp.entrySet()) {
                UUID playerUuid = entry.getKey();
                int amount = entry.getValue() == null ? 0 : entry.getValue();
                if (playerUuid == null || amount <= 0) {
                    continue;
                }

                statement.setString(1, playerUuid.toString());
                statement.setInt(2, amount);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void saveStats(Connection connection, Map<UUID, PublicSalesManager.PlayerSellStats> stats) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_INSERT_PLAYER_STATS)) {
            for (Map.Entry<UUID, PublicSalesManager.PlayerSellStats> entry : stats.entrySet()) {
                UUID playerUuid = entry.getKey();
                PublicSalesManager.PlayerSellStats playerStats = entry.getValue();
                if (playerUuid == null || playerStats == null) {
                    continue;
                }

                statement.setString(1, playerUuid.toString());
                statement.setInt(2, Math.max(0, playerStats.salesCompleted()));
                statement.setInt(3, Math.max(0, playerStats.itemsSold()));
                statement.setInt(4, Math.max(0, playerStats.xpEarnedTotal()));
                statement.setInt(5, Math.max(0, playerStats.purchasesCompleted()));
                statement.setInt(6, Math.max(0, playerStats.itemsBought()));
                statement.setInt(7, Math.max(0, playerStats.xpSpentTotal()));
                statement.setInt(8, Math.max(0, playerStats.ownListingsRemoved()));
                statement.setString(9, safeText(playerStats.lastKnownName()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void savePendingExpiredReturns(
        Connection connection,
        Map<UUID, List<PublicSalesManager.PendingSpawnerReturn>> pendingExpiredReturns
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_INSERT_PENDING_EXPIRED_ITEM)) {
            for (Map.Entry<UUID, List<PublicSalesManager.PendingSpawnerReturn>> entry : pendingExpiredReturns.entrySet()) {
                UUID playerUuid = entry.getKey();
                List<PublicSalesManager.PendingSpawnerReturn> pendingList = entry.getValue();
                if (playerUuid == null || pendingList == null || pendingList.isEmpty()) {
                    continue;
                }

                for (PublicSalesManager.PendingSpawnerReturn pendingReturn : pendingList) {
                    if (pendingReturn == null || pendingReturn.entityType() == null) {
                        continue;
                    }

                    statement.setString(1, playerUuid.toString());
                    statement.setString(2, pendingReturn.entityType().name());
                    statement.setInt(3, Math.max(1, pendingReturn.stackSize()));
                    statement.setInt(4, Math.max(0, pendingReturn.level()));
                    statement.setInt(5, Math.max(1, pendingReturn.itemAmount()));
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private void beginImmediate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(SQL_BEGIN_IMMEDIATE);
        }
    }

    private void commit(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(SQL_COMMIT);
        }
    }

    private void rollback(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.execute(SQL_ROLLBACK);
        } catch (SQLException rollbackError) {
            plugin.getLogger().warning("Falha ao executar ROLLBACK no SQLite: " + rollbackError.getMessage());
        }
    }

    private void ensureDatabaseFolder() {
        File folder = databaseFile.getParentFile();
        if (folder != null && !folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Nao foi possivel criar pasta de banco: " + folder.getAbsolutePath());
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private Map<String, PublicSale> loadSales(ConfigurationSection section) {
        if (section == null) {
            return Collections.emptyMap();
        }

        Map<String, PublicSale> sales = new HashMap<>();
        for (String id : section.getKeys(false)) {
            String base = id + ".";
            String sellerUuidRaw = section.getString(base + "seller-uuid", "");
            String sellerName = section.getString(base + "seller-name", "Unknown");
            String entityTypeRaw = section.getString(base + "entity-type", "");

            UUID sellerUuid;
            EntityType entityType;
            try {
                sellerUuid = UUID.fromString(sellerUuidRaw);
                entityType = EntityType.valueOf(entityTypeRaw);
            } catch (Exception ignored) {
                plugin.getLogger().warning("Venda publica invalida ignorada em sales.yml: " + id);
                continue;
            }

            int stackSize = Math.max(1, section.getInt(base + "stack-size", 1));
            int level = Math.max(0, section.getInt(base + "level", 0));
            int itemAmount = Math.max(1, section.getInt(base + "item-amount", 1));
            int priceXp = Math.max(1, section.getInt(base + "price-xp", 1));
            long createdAt = section.getLong(base + "created-at", System.currentTimeMillis());
            long expiresAt = section.getLong(base + "expires-at", createdAt);

            PublicSale sale = new PublicSale(
                id,
                sellerUuid,
                sellerName,
                entityType,
                stackSize,
                level,
                itemAmount,
                priceXp,
                createdAt,
                expiresAt
            );
            sales.put(id, sale);
        }

        return sales;
    }

    private Map<UUID, Integer> loadPendingXp(ConfigurationSection section) {
        if (section == null) {
            return Collections.emptyMap();
        }

        Map<UUID, Integer> pending = new HashMap<>();
        for (String uuidRaw : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidRaw);
                int amount = Math.max(0, section.getInt(uuidRaw, 0));
                if (amount > 0) {
                    pending.put(uuid, amount);
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("UUID invalido em pending-xp ignorado: " + uuidRaw);
            }
        }

        return pending;
    }

    private Map<UUID, PublicSalesManager.PlayerSellStats> loadStats(ConfigurationSection section) {
        if (section == null) {
            return Collections.emptyMap();
        }

        Map<UUID, PublicSalesManager.PlayerSellStats> stats = new HashMap<>();
        for (String uuidRaw : section.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidRaw);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("UUID invalido em stats ignorado: " + uuidRaw);
                continue;
            }

            String base = uuidRaw + ".";
            int salesCompleted = Math.max(0, section.getInt(base + "sales-completed", 0));
            int itemsSold = Math.max(0, section.getInt(base + "items-sold", 0));
            int xpEarnedTotal = Math.max(0, section.getInt(base + "xp-earned-total", 0));
            int purchasesCompleted = Math.max(0, section.getInt(base + "purchases-completed", 0));
            int itemsBought = Math.max(0, section.getInt(base + "items-bought", 0));
            int xpSpentTotal = Math.max(0, section.getInt(base + "xp-spent-total", 0));
            int ownListingsRemoved = Math.max(0, section.getInt(base + "own-listings-removed", 0));
            String lastKnownName = section.getString(base + "last-known-name", "");

            PublicSalesManager.PlayerSellStats playerStats = new PublicSalesManager.PlayerSellStats(
                salesCompleted,
                itemsSold,
                xpEarnedTotal,
                purchasesCompleted,
                itemsBought,
                xpSpentTotal,
                ownListingsRemoved,
                lastKnownName == null ? "" : lastKnownName
            );
            stats.put(uuid, playerStats);
        }

        return stats;
    }

    private Map<UUID, List<PublicSalesManager.PendingSpawnerReturn>> loadPendingExpiredReturns(ConfigurationSection section) {
        if (section == null) {
            return Collections.emptyMap();
        }

        Map<UUID, List<PublicSalesManager.PendingSpawnerReturn>> pending = new HashMap<>();
        for (String uuidRaw : section.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidRaw);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("UUID invalido em pending-expired-items ignorado: " + uuidRaw);
                continue;
            }

            ConfigurationSection playerSection = section.getConfigurationSection(uuidRaw);
            if (playerSection == null) {
                continue;
            }

            List<PublicSalesManager.PendingSpawnerReturn> pendingList = new ArrayList<>();
            for (String indexKey : playerSection.getKeys(false)) {
                String base = indexKey + ".";
                String entityTypeRaw = playerSection.getString(base + "entity-type", "");

                EntityType entityType;
                try {
                    entityType = EntityType.valueOf(entityTypeRaw);
                } catch (Exception ignored) {
                    plugin.getLogger().warning("Entrada invalida em pending-expired-items ignorada para " + uuidRaw + "." + indexKey);
                    continue;
                }

                int stackSize = Math.max(1, playerSection.getInt(base + "stack-size", 1));
                int level = Math.max(0, playerSection.getInt(base + "level", 0));
                int itemAmount = Math.max(1, playerSection.getInt(base + "item-amount", 1));

                pendingList.add(new PublicSalesManager.PendingSpawnerReturn(entityType, stackSize, level, itemAmount));
            }

            if (!pendingList.isEmpty()) {
                pending.put(uuid, pendingList);
            }
        }

        return pending;
    }

    public record StorageData(
        Map<String, PublicSale> sales,
        Map<UUID, Integer> pendingXp,
        Map<UUID, PublicSalesManager.PlayerSellStats> stats,
        Map<UUID, List<PublicSalesManager.PendingSpawnerReturn>> pendingExpiredReturns
    ) {}

    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

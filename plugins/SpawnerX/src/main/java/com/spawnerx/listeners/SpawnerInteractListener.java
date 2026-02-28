package com.spawnerx.listeners;

import com.spawnerx.SpawnerX;
import com.spawnerx.managers.ConfigManager;
import com.spawnerx.managers.SpawnerInteractMenuHolder;
import com.spawnerx.utils.SpawnerUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Listener para interação com spawners (Shift + Botão Direito).
 */
public class SpawnerInteractListener implements Listener {

    private static final int SUMMARY_SIZE = 27;
    private static final int DETAILS_SIZE = 54;
    private static final int SUMMARY_SLOT_DYNAMIC = 11;
    private static final int SUMMARY_SLOT_INFO = 13;
    private static final int SUMMARY_SLOT_OWNER = 15;
    private static final int DETAILS_SLOT_TITLE = 4;
    private static final int DETAILS_SLOT_PREV = 45;
    private static final int DETAILS_SLOT_BACK = 48;
    private static final int DETAILS_SLOT_PAGE = 49;
    private static final int DETAILS_SLOT_NEXT = 53;
    private static final int DETAILS_SLOT_EMPTY = 22;
    private static final int[] DETAILS_CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };

    private final SpawnerX plugin;
    private final Map<UUID, BukkitTask> activeMenus = new HashMap<>();
    private final NamespacedKey navKey;

    public SpawnerInteractListener(SpawnerX plugin) {
        this.plugin = plugin;
        this.navKey = new NamespacedKey(plugin, "interact_menu_nav");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (!isValidSpawnerBlock(block)) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        if (plugin.isLicenseLocked()) {
            event.setCancelled(true);
            player.sendMessage(plugin.getLocaleManager().getMessage("license.locked"));
            return;
        }

        event.setCancelled(true);
        openSummaryMenu(player, block);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof SpawnerInteractMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (plugin.isLicenseLocked()) {
            player.sendMessage(plugin.getLocaleManager().getMessage("license.locked"));
            player.closeInventory();
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        String nav = getNavigationAction(clicked);
        if (holder.getView() == SpawnerInteractMenuHolder.View.SUMMARY) {
            handleSummaryClick(player, holder, nav);
            return;
        }

        handleStackDetailsClick(player, holder, nav);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SpawnerInteractMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof SpawnerInteractMenuHolder)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Inventory topInventory = player.getOpenInventory().getTopInventory();
            if (!(topInventory.getHolder() instanceof SpawnerInteractMenuHolder)) {
                stopMenuTask(uuid);
            }
        });
    }

    private void handleSummaryClick(Player player, SpawnerInteractMenuHolder holder, String nav) {
        if (!"open-details".equals(nav)) {
            return;
        }

        Block block = holder.resolveBlock();
        if (!isValidSpawnerBlock(block)) {
            player.closeInventory();
            return;
        }

        openStackDetailsMenu(player, block, 0, holder.getPage());
    }

    private void handleStackDetailsClick(Player player, SpawnerInteractMenuHolder holder, String nav) {
        Block block = holder.resolveBlock();
        if (!isValidSpawnerBlock(block)) {
            player.closeInventory();
            return;
        }

        if ("back".equals(nav)) {
            openSummaryMenu(player, block);
            return;
        }
        if ("prev".equals(nav)) {
            openStackDetailsMenu(player, block, holder.getPage() - 1, holder.getParentPage());
            return;
        }
        if ("next".equals(nav)) {
            openStackDetailsMenu(player, block, holder.getPage() + 1, holder.getParentPage());
        }
    }

    private void openSummaryMenu(Player player, Block block) {
        if (!isValidSpawnerBlock(block)) {
            return;
        }

        CreatureSpawner spawner = (CreatureSpawner) block.getState();
        EntityType entityType = spawner.getSpawnedType();
        String entityName = SpawnerUtils.getEntityDisplayName(entityType);

        String title = plugin.getLocaleManager().getMessage("menu.title", "type", entityName);
        SpawnerInteractMenuHolder holder = new SpawnerInteractMenuHolder(
            SpawnerInteractMenuHolder.View.SUMMARY,
            block.getWorld().getUID(),
            block.getX(),
            block.getY(),
            block.getZ(),
            0,
            0
        );
        Inventory gui = Bukkit.createInventory(holder, SUMMARY_SIZE,
            LegacyComponentSerializer.legacyAmpersand().deserialize(title));

        fillBackground(gui);
        updateSummaryMenu(player, gui, block);

        player.openInventory(gui);
        startMenuTask(player);
    }

    private void openStackDetailsMenu(Player player, Block block, int page, int parentPage) {
        if (!isValidSpawnerBlock(block)) {
            player.closeInventory();
            return;
        }

        CreatureSpawner spawner = (CreatureSpawner) block.getState();
        PersistentDataContainer container = spawner.getPersistentDataContainer();
        boolean changed = container.has(plugin.getSpawnerManager().getStackKey(), PersistentDataType.INTEGER)
            && plugin.getSpawnerManager().reconcileSpawnerStats(spawner);
        if (changed) {
            spawner.update();
            spawner = (CreatureSpawner) block.getState();
        }
        container = spawner.getPersistentDataContainer();
        int stackSize = Math.max(1, container.getOrDefault(
            plugin.getSpawnerManager().getStackKey(), PersistentDataType.INTEGER, 1));
        int totalPages = Math.max(1, (int) Math.ceil((double) stackSize / DETAILS_CONTENT_SLOTS.length));
        int currentPage = clampPage(page, totalPages);

        EntityType entityType = spawner.getSpawnedType();
        String entityName = SpawnerUtils.getEntityDisplayName(entityType);
        String title = plugin.getLocaleManager().getMessage("menu.stack-details.title",
            "type", entityName,
            "page", String.valueOf(currentPage + 1),
            "pages", String.valueOf(totalPages));

        SpawnerInteractMenuHolder holder = new SpawnerInteractMenuHolder(
            SpawnerInteractMenuHolder.View.STACK_DETAILS,
            block.getWorld().getUID(),
            block.getX(),
            block.getY(),
            block.getZ(),
            currentPage,
            parentPage
        );
        Inventory gui = Bukkit.createInventory(holder, DETAILS_SIZE,
            LegacyComponentSerializer.legacyAmpersand().deserialize(title));

        fillBackground(gui);
        renderStackDetailsMenu(gui, spawner, block, currentPage, totalPages, stackSize);

        player.openInventory(gui);
        startMenuTask(player);
    }

    private void updateSummaryMenu(Player player, Inventory gui, Block block) {
        if (!isValidSpawnerBlock(block)) {
            player.closeInventory();
            return;
        }

        CreatureSpawner spawner = (CreatureSpawner) block.getState();
        PersistentDataContainer container = spawner.getPersistentDataContainer();
        boolean changed = container.has(plugin.getSpawnerManager().getStackKey(), PersistentDataType.INTEGER)
            && plugin.getSpawnerManager().reconcileSpawnerStats(spawner);
        if (changed) {
            spawner.update();
            spawner = (CreatureSpawner) block.getState();
        }

        EntityType entityType = spawner.getSpawnedType();
        String entityName = SpawnerUtils.getEntityDisplayName(entityType);
        container = spawner.getPersistentDataContainer();
        int stack = Math.max(1, container.getOrDefault(plugin.getSpawnerManager().getStackKey(), PersistentDataType.INTEGER, 1));
        int level = plugin.getSpawnerManager().getSpawnerLevel(spawner);

        gui.setItem(SUMMARY_SLOT_INFO, createSummaryInfoItem(spawner, block, entityType, entityName, stack, level));
        gui.setItem(SUMMARY_SLOT_DYNAMIC, createSummaryDynamicItem());
        ItemStack ownerItem = createOwnerItem(spawner);
        if (ownerItem != null) {
            gui.setItem(SUMMARY_SLOT_OWNER, ownerItem);
        }
    }

    private void updateStackDetailsMenu(Player player, Inventory gui, SpawnerInteractMenuHolder holder) {
        Block block = holder.resolveBlock();
        if (!isValidSpawnerBlock(block)) {
            player.closeInventory();
            return;
        }

        CreatureSpawner spawner = (CreatureSpawner) block.getState();
        PersistentDataContainer container = spawner.getPersistentDataContainer();
        boolean changed = container.has(plugin.getSpawnerManager().getStackKey(), PersistentDataType.INTEGER)
            && plugin.getSpawnerManager().reconcileSpawnerStats(spawner);
        if (changed) {
            spawner.update();
            spawner = (CreatureSpawner) block.getState();
        }

        container = spawner.getPersistentDataContainer();
        int stackSize = Math.max(1, container.getOrDefault(
            plugin.getSpawnerManager().getStackKey(), PersistentDataType.INTEGER, 1));
        int totalPages = Math.max(1, (int) Math.ceil((double) stackSize / DETAILS_CONTENT_SLOTS.length));
        int requestedPage = holder.getPage();
        int currentPage = clampPage(requestedPage, totalPages);

        if (currentPage != requestedPage) {
            openStackDetailsMenu(player, block, currentPage, holder.getParentPage());
            return;
        }

        fillBackground(gui);
        renderStackDetailsMenu(gui, spawner, block, currentPage, totalPages, stackSize);
    }

    private void renderStackDetailsMenu(Inventory gui, CreatureSpawner spawner, Block block,
                                        int page, int totalPages, int stackSize) {
        EntityType entityType = spawner.getSpawnedType();
        int level = plugin.getSpawnerManager().getSpawnerLevel(spawner);
        int delay = spawner.getDelay();
        int minDelay = spawner.getMinSpawnDelay();
        int maxDelay = spawner.getMaxSpawnDelay();
        int spawnCount = spawner.getSpawnCount();
        int maxNearby = spawner.getMaxNearbyEntities();
        ConfigManager.StackSpawnMode chainMode = plugin.getConfigManager().getStackSpawnMode();
        ConfigManager.StackSpawnOrder chainOrder = plugin.getConfigManager().getStackSpawnOrder();
        int nextIndex = plugin.getStackSpawnChainTracker().peekNextIndex(block.getLocation(), stackSize, chainOrder);
        String chainModeLabel = getChainModeLabel(chainMode);
        String chainOrderLabel = getChainOrderLabel(chainOrder);

        boolean active = hasNearbyPlayers(block);
        String activeStatus = plugin.getLocaleManager().getMessage(
            active ? "menu.dynamic.status-active" : "menu.dynamic.status-inactive");

        gui.setItem(DETAILS_SLOT_TITLE, createInfoItem(Material.CLOCK,
            plugin.getLocaleManager().getMessage("menu.dynamic.title")));

        if (page > 0) {
            gui.setItem(DETAILS_SLOT_PREV, createNavItem("prev", Material.ARROW,
                plugin.getLocaleManager().getMessage("menu.stack-details.nav.prev")));
        }
        if (page < totalPages - 1) {
            gui.setItem(DETAILS_SLOT_NEXT, createNavItem("next", Material.ARROW,
                plugin.getLocaleManager().getMessage("menu.stack-details.nav.next")));
        }
        gui.setItem(DETAILS_SLOT_BACK, createNavItem("back", Material.BARRIER,
            plugin.getLocaleManager().getMessage("menu.stack-details.nav.back")));
        gui.setItem(DETAILS_SLOT_PAGE, createInfoItem(Material.PAPER,
            plugin.getLocaleManager().getMessage("menu.stack-details.nav.page",
                "page", String.valueOf(page + 1),
                "pages", String.valueOf(totalPages))));

        int start = page * DETAILS_CONTENT_SLOTS.length;
        int end = Math.min(start + DETAILS_CONTENT_SLOTS.length, stackSize);
        int index = 0;

        for (int i = start; i < end; i++) {
            int unitIndex = i + 1;
            ItemStack detailItem = createStackDetailItem(
                entityType,
                level,
                unitIndex,
                stackSize,
                spawnCount,
                maxNearby,
                delay,
                minDelay,
                maxDelay,
                activeStatus,
                chainModeLabel,
                chainOrderLabel,
                nextIndex
            );
            gui.setItem(DETAILS_CONTENT_SLOTS[index], detailItem);
            index++;
        }

        if (start >= end) {
            gui.setItem(DETAILS_SLOT_EMPTY, createInfoItem(Material.BARRIER,
                plugin.getLocaleManager().getMessage("menu.stack-details.empty")));
        }
    }

    private ItemStack createSummaryInfoItem(CreatureSpawner spawner, Block block, EntityType entityType,
                                            String entityName, int stack, int level) {
        ItemStack info = new ItemStack(Material.SPAWNER);
        ItemMeta meta = info.getItemMeta();
        if (meta == null) {
            return info;
        }

        meta.displayName(deserializeNoItalic(
            plugin.getLocaleManager().getMessage("menu.info-item.name", "type", entityName)));

        String rarity = plugin.getConfigManager().getRarity(entityType.name());
        String rarityClean = rarity.replaceAll("&[0-9a-fk-or]", "");
        String rarityColor = extractColorCode(rarity);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(deserializeNoItalic(plugin.getLocaleManager().getMessage("spawner.lore.info")));
        lore.add(deserializeNoItalic(plugin.getLocaleManager().getMessage("spawner.lore.type", "type", entityName)));
        lore.add(deserializeNoItalic(plugin.getLocaleManager().getMessage("spawner.lore.rarity",
            "rarity", rarityClean, "rarity_color", rarityColor)));
        lore.add(deserializeNoItalic(plugin.getLocaleManager().getMessage("spawner.lore.stack", "amount", String.valueOf(stack))));
        lore.add(deserializeNoItalic(plugin.getLocaleManager().getMessage("spawner.lore.level", "level", String.valueOf(level))));

        boolean validEnvironment = canSpawnNow(spawner, block, entityType);
        String envStatus = plugin.getLocaleManager().getMessage(
            validEnvironment ? "menu.status.valid" : "menu.status.invalid");
        lore.add(deserializeNoItalic(plugin.getLocaleManager().getMessage(
            "menu.info-item.environment", "status", envStatus)));
        lore.add(Component.empty());

        meta.lore(lore);
        info.setItemMeta(meta);
        return info;
    }

    private ItemStack createSummaryDynamicItem() {
        ItemStack dynamicInfo = new ItemStack(Material.CLOCK);
        ItemMeta dynamicMeta = dynamicInfo.getItemMeta();
        if (dynamicMeta == null) {
            return dynamicInfo;
        }

        dynamicMeta.displayName(deserializeNoItalic(plugin.getLocaleManager().getMessage("menu.dynamic.title")));
        dynamicMeta.getPersistentDataContainer().set(navKey, PersistentDataType.STRING, "open-details");
        dynamicInfo.setItemMeta(dynamicMeta);
        return dynamicInfo;
    }

    private ItemStack createOwnerItem(CreatureSpawner spawner) {
        PersistentDataContainer container = spawner.getPersistentDataContainer();
        String ownerName = container.get(plugin.getSpawnerManager().getOwnerKey(), PersistentDataType.STRING);
        if (ownerName == null || ownerName.isBlank()) {
            return null;
        }

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        if (skullMeta == null) {
            return null;
        }

        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(ownerName));
        skullMeta.displayName(deserializeNoItalic(
            plugin.getLocaleManager().getMessage("menu.owner-item.name", "player", ownerName)));
        List<Component> skullLore = new ArrayList<>();
        skullLore.add(deserializeNoItalic(plugin.getLocaleManager().getMessage("menu.owner-item.lore")));
        skullMeta.lore(skullLore);
        skull.setItemMeta(skullMeta);
        return skull;
    }

    private ItemStack createStackDetailItem(EntityType entityType, int level, int index, int total,
                                            int spawnCount, int maxNearby, int delay, int minDelay, int maxDelay,
                                            String activeStatus, String chainModeLabel, String chainOrderLabel,
                                            int nextIndex) {
        ItemStack item = plugin.getSpawnerManager().createSpawner(entityType, 1, level);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(deserializeNoItalic(plugin.getLocaleManager().getMessage(
            "menu.stack-details.item-name",
            "index", String.valueOf(index),
            "total", String.valueOf(total)
        )));

        String nextMarker = index == nextIndex
            ? plugin.getLocaleManager().getMessage("menu.stack-details.chain.next-marker")
            : plugin.getLocaleManager().getMessage("menu.stack-details.chain.idle-marker");

        String loreRaw = plugin.getLocaleManager().getMessage(
            "menu.stack-details.item-lore",
            "type", SpawnerUtils.getEntityDisplayName(entityType),
            "level", String.valueOf(level),
            "index", String.valueOf(index),
            "total", String.valueOf(total),
            "spawn_count", String.valueOf(spawnCount),
            "max_nearby", String.valueOf(maxNearby),
            "delay", String.valueOf(delay),
            "min", String.valueOf(minDelay / 20),
            "max", String.valueOf(maxDelay / 20),
            "status", activeStatus,
            "chain_mode", chainModeLabel,
            "chain_order", chainOrderLabel,
            "next_index", String.valueOf(nextIndex),
            "next_marker", nextMarker
        );
        meta.lore(createLore(loreRaw));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavItem(String nav, Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(deserializeNoItalic(name));
            meta.getPersistentDataContainer().set(navKey, PersistentDataType.STRING, nav);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInfoItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(deserializeNoItalic(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillBackground(Inventory inventory) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            filler.setItemMeta(meta);
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private String getNavigationAction(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(navKey, PersistentDataType.STRING);
    }

    private void startMenuTask(Player player) {
        UUID uuid = player.getUniqueId();
        stopMenuTask(uuid);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    stopMenuTask(uuid);
                    return;
                }

                Inventory topInventory = player.getOpenInventory().getTopInventory();
                if (!(topInventory.getHolder() instanceof SpawnerInteractMenuHolder holder)) {
                    stopMenuTask(uuid);
                    return;
                }

                if (plugin.isLicenseLocked()) {
                    player.sendMessage(plugin.getLocaleManager().getMessage("license.locked"));
                    player.closeInventory();
                    stopMenuTask(uuid);
                    return;
                }

                if (holder.getView() == SpawnerInteractMenuHolder.View.SUMMARY) {
                    updateSummaryMenu(player, topInventory, holder.resolveBlock());
                    return;
                }

                updateStackDetailsMenu(player, topInventory, holder);
            }
        }.runTaskTimer(plugin, 0L, 2L);

        activeMenus.put(uuid, task);
    }

    private void stopMenuTask(UUID uuid) {
        BukkitTask task = activeMenus.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private boolean canSpawnNow(CreatureSpawner spawner, Block block, EntityType entityType) {
        boolean playerNearby = hasNearbyPlayers(block);
        int spawnRange = spawner.getSpawnRange();
        int maxNearby = spawner.getMaxNearbyEntities();

        long nearbyCount = block.getWorld()
            .getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), spawnRange, spawnRange, spawnRange)
            .stream()
            .filter(entity -> entity.getType() == entityType)
            .count();

        boolean belowCap = maxNearby <= 0 || nearbyCount < maxNearby;
        return playerNearby && belowCap;
    }

    private boolean hasNearbyPlayers(Block block) {
        int activeDistance = plugin.getConfigManager().getSpawnerActiveDistance();
        return !block.getWorld().getNearbyPlayers(block.getLocation(), activeDistance).isEmpty();
    }

    private boolean isValidSpawnerBlock(Block block) {
        return block != null && block.getType() == Material.SPAWNER && block.getWorld() != null;
    }

    private int clampPage(int page, int totalPages) {
        if (totalPages <= 0) {
            return 0;
        }
        if (page < 0) {
            return 0;
        }
        if (page >= totalPages) {
            return totalPages - 1;
        }
        return page;
    }

    private List<Component> createLore(String raw) {
        List<Component> lore = new ArrayList<>();
        for (String line : splitRawLines(raw)) {
            lore.add(deserializeNoItalic(line));
        }
        return lore;
    }

    private List<String> splitRawLines(String raw) {
        List<String> lines = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return lines;
        }
        for (String line : raw.split("\\\\n")) {
            lines.add(line);
        }
        return lines;
    }

    private String extractColorCode(String rarity) {
        int idx = rarity.indexOf("&");
        if (idx >= 0 && idx + 1 < rarity.length()) {
            return rarity.substring(idx, idx + 2);
        }
        return "&f";
    }

    private String getChainModeLabel(ConfigManager.StackSpawnMode mode) {
        if (mode == ConfigManager.StackSpawnMode.SIMULTANEOUS) {
            return plugin.getLocaleManager().getMessage("menu.stack-details.chain.mode.simultaneous");
        }
        return plugin.getLocaleManager().getMessage("menu.stack-details.chain.mode.chained");
    }

    private String getChainOrderLabel(ConfigManager.StackSpawnOrder order) {
        ConfigManager.StackSpawnOrder safeOrder = order == null
            ? ConfigManager.StackSpawnOrder.RANDOM_CYCLE
            : order;

        return switch (safeOrder) {
            case SEQUENTIAL -> plugin.getLocaleManager().getMessage("menu.stack-details.chain.order.sequential");
            case RANDOM -> plugin.getLocaleManager().getMessage("menu.stack-details.chain.order.random");
            case RANDOM_CYCLE -> plugin.getLocaleManager().getMessage("menu.stack-details.chain.order.random-cycle");
        };
    }

    private Component deserializeNoItalic(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
            .deserialize(text == null ? "" : text)
            .decoration(TextDecoration.ITALIC, false);
    }
}

package com.spawnerx.managers;

import com.spawnerx.SpawnerX;
import com.spawnerx.utils.SkullUtils;
import com.spawnerx.utils.SpawnerUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Gerencia o menu admin para entrega de spawners.
 */
public class AdminSpawnerMenuManager {

    private static final int MENU_SIZE = 54;
    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 48;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_NEXT = 53;
    private static final int SLOT_TITLE = 4;
    private static final int SLOT_EMPTY = 22;

    private final SpawnerX plugin;
    private final NamespacedKey navKey;
    private final NamespacedKey entityKey;
    private final NamespacedKey levelKey;

    public AdminSpawnerMenuManager(SpawnerX plugin) {
        this.plugin = plugin;
        this.navKey = new NamespacedKey(plugin, "admin_menu_nav");
        this.entityKey = new NamespacedKey(plugin, "admin_menu_entity");
        this.levelKey = new NamespacedKey(plugin, "admin_menu_level");
    }

    public void openMobMenu(Player player) {
        openMobMenu(player, 0);
    }

    public boolean isAdminMenuInventory(Inventory inventory) {
        return inventory.getHolder() instanceof AdminSpawnerMenuHolder;
    }

    public void handleClick(Player player, Inventory inventory, ItemStack clicked) {
        if (!(inventory.getHolder() instanceof AdminSpawnerMenuHolder holder)) {
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        String nav = container.get(navKey, PersistentDataType.STRING);

        if (holder.getView() == AdminSpawnerMenuHolder.View.MOBS) {
            handleMobViewClick(player, holder, container, nav);
            return;
        }

        handleLevelViewClick(player, holder, container, nav);
    }

    private void handleMobViewClick(Player player, AdminSpawnerMenuHolder holder,
                                    PersistentDataContainer container, String nav) {
        if ("prev".equals(nav)) {
            openMobMenu(player, holder.getPage() - 1);
            return;
        }
        if ("next".equals(nav)) {
            openMobMenu(player, holder.getPage() + 1);
            return;
        }

        String entityRaw = container.get(entityKey, PersistentDataType.STRING);
        if (entityRaw == null || entityRaw.isBlank()) {
            return;
        }

        EntityType entityType;
        try {
            entityType = EntityType.valueOf(entityRaw);
        } catch (IllegalArgumentException ex) {
            return;
        }

        openLevelMenu(player, entityType, 0, holder.getPage());
    }

    private void handleLevelViewClick(Player player, AdminSpawnerMenuHolder holder,
                                      PersistentDataContainer container, String nav) {
        if ("back".equals(nav)) {
            openMobMenu(player, holder.getParentPage());
            return;
        }
        if ("prev".equals(nav)) {
            if (holder.getSelectedEntity() != null) {
                openLevelMenu(player, holder.getSelectedEntity(), holder.getPage() - 1, holder.getParentPage());
            }
            return;
        }
        if ("next".equals(nav)) {
            if (holder.getSelectedEntity() != null) {
                openLevelMenu(player, holder.getSelectedEntity(), holder.getPage() + 1, holder.getParentPage());
            }
            return;
        }

        String entityRaw = container.get(entityKey, PersistentDataType.STRING);
        Integer level = container.get(levelKey, PersistentDataType.INTEGER);
        if (entityRaw == null || level == null) {
            return;
        }

        EntityType entityType;
        try {
            entityType = EntityType.valueOf(entityRaw);
        } catch (IllegalArgumentException ex) {
            return;
        }

        if (holder.getSelectedEntity() != null && holder.getSelectedEntity() != entityType) {
            openLevelMenu(player, holder.getSelectedEntity(), holder.getPage(), holder.getParentPage());
            return;
        }

        giveSpawner(player, entityType, level);
    }

    private void openMobMenu(Player player, int page) {
        List<EntityType> mobs = getAvailableEntities();
        int totalPages = Math.max(1, (int) Math.ceil((double) mobs.size() / CONTENT_SLOTS.length));
        int currentPage = clampPage(page, totalPages);

        String title = plugin.getLocaleManager().getMessage("admin.menu.mobs.title",
            "page", String.valueOf(currentPage + 1),
            "pages", String.valueOf(totalPages));

        Inventory inventory = Bukkit.createInventory(
            new AdminSpawnerMenuHolder(AdminSpawnerMenuHolder.View.MOBS, currentPage, null, currentPage),
            MENU_SIZE,
            LegacyComponentSerializer.legacyAmpersand().deserialize(title)
        );

        fillBackground(inventory);
        placeTitleBar(inventory, plugin.getLocaleManager().getMessage("admin.menu.mobs.header"));
        placeNavigation(inventory, currentPage, totalPages, false);

        int start = currentPage * CONTENT_SLOTS.length;
        int end = Math.min(start + CONTENT_SLOTS.length, mobs.size());
        int contentIndex = 0;

        for (int i = start; i < end; i++) {
            EntityType entityType = mobs.get(i);
            inventory.setItem(CONTENT_SLOTS[contentIndex], createMobItem(entityType));
            contentIndex++;
        }

        if (start >= end) {
            inventory.setItem(SLOT_EMPTY, createInfoItem(Material.BARRIER,
                plugin.getLocaleManager().getMessage("admin.menu.mobs.empty")));
        }

        player.openInventory(inventory);
    }

    private void openLevelMenu(Player player, EntityType entityType) {
        openLevelMenu(player, entityType, 0, 0);
    }

    private void openLevelMenu(Player player, EntityType entityType, int page, int parentPage) {
        List<Integer> levels = getAvailableLevels();
        int totalPages = Math.max(1, (int) Math.ceil((double) levels.size() / CONTENT_SLOTS.length));
        int currentPage = clampPage(page, totalPages);
        String entityName = SpawnerUtils.getEntityDisplayName(entityType);

        String title = plugin.getLocaleManager().getMessage("admin.menu.levels.title",
            "type", entityName,
            "page", String.valueOf(currentPage + 1),
            "pages", String.valueOf(totalPages));
        Inventory inventory = Bukkit.createInventory(
            new AdminSpawnerMenuHolder(AdminSpawnerMenuHolder.View.LEVELS, currentPage, entityType, parentPage),
            MENU_SIZE,
            LegacyComponentSerializer.legacyAmpersand().deserialize(title)
        );

        fillBackground(inventory);
        placeTitleBar(inventory, plugin.getLocaleManager().getMessage("admin.menu.levels.header",
            "type", entityName));
        placeNavigation(inventory, currentPage, totalPages, true);

        int start = currentPage * CONTENT_SLOTS.length;
        int end = Math.min(start + CONTENT_SLOTS.length, levels.size());
        int contentIndex = 0;

        for (int i = start; i < end; i++) {
            int level = levels.get(i);
            inventory.setItem(CONTENT_SLOTS[contentIndex], createLevelItem(entityType, level));
            contentIndex++;
        }

        if (start >= end) {
            inventory.setItem(SLOT_EMPTY, createInfoItem(Material.BARRIER,
                plugin.getLocaleManager().getMessage("admin.menu.levels.empty")));
        }

        player.openInventory(inventory);
    }

    private ItemStack createMobItem(EntityType entityType) {
        ItemStack item = plugin.getSpawnerManager().createSpawner(entityType, 1, 0);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String entityName = SpawnerUtils.getEntityDisplayName(entityType);
        meta.displayName(deserializeNoItalic(plugin.getLocaleManager().getMessage(
            "admin.menu.mobs.item-name",
            "type", entityName
        )));

        meta.lore(createLore(plugin.getLocaleManager().getMessage("admin.menu.mobs.item-lore")));

        meta.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, entityType.name());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLevelItem(EntityType entityType, int level) {
        ItemStack item = plugin.getSpawnerManager().createSpawner(entityType, 1, level);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(deserializeNoItalic(plugin.getLocaleManager().getMessage(
            "admin.menu.levels.item-name",
            "level", String.valueOf(level)
        )));

        meta.lore(createLore(plugin.getLocaleManager().getMessage("admin.menu.levels.item-lore")));

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(entityKey, PersistentDataType.STRING, entityType.name());
        container.set(levelKey, PersistentDataType.INTEGER, level);
        item.setItemMeta(meta);
        return item;
    }

    private void placeNavigation(Inventory inventory, int page, int totalPages, boolean showBack) {
        if (totalPages > 1 && page > 0) {
            inventory.setItem(SLOT_PREV, createNavItem("prev", plugin.getConfigManager().getShopNavHead("prev-head"),
                Material.ARROW, plugin.getLocaleManager().getMessage("admin.menu.nav.prev")));
        }
        if (totalPages > 1 && page < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, createNavItem("next", plugin.getConfigManager().getShopNavHead("next-head"),
                Material.ARROW, plugin.getLocaleManager().getMessage("admin.menu.nav.next")));
        }
        if (showBack) {
            inventory.setItem(SLOT_BACK, createNavItem("back", plugin.getConfigManager().getShopNavHead("back-head"),
                Material.BARRIER, plugin.getLocaleManager().getMessage("admin.menu.nav.back")));
        }

        inventory.setItem(SLOT_PAGE, createInfoItem(Material.PAPER,
            plugin.getLocaleManager().getMessage("admin.menu.nav.page",
                "page", String.valueOf(page + 1),
                "pages", String.valueOf(totalPages))));
    }

    private ItemStack createNavItem(String nav, String headBase64, Material fallbackMaterial, String name) {
        ItemStack item = createHeadOrFallback(headBase64, fallbackMaterial);
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

    private void placeTitleBar(Inventory inventory, String title) {
        Material titleMaterial = parseMaterial(plugin.getConfigManager().getShopTitleItem(), Material.SPAWNER);
        ItemStack item = new ItemStack(titleMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(deserializeNoItalic(title));
            item.setItemMeta(meta);
        }
        inventory.setItem(SLOT_TITLE, item);
    }

    private ItemStack createHeadOrFallback(String headBase64, Material fallbackMaterial) {
        if (headBase64 == null || headBase64.isBlank()) {
            return new ItemStack(fallbackMaterial);
        }

        ItemStack head = SkullUtils.createSkull(headBase64);
        if (head.getType() != Material.PLAYER_HEAD) {
            return new ItemStack(fallbackMaterial);
        }
        return head;
    }

    private void fillBackground(Inventory inventory) {
        Material primaryMat = parseMaterial(plugin.getConfigManager().getShopFillerPrimary(), Material.BLUE_STAINED_GLASS_PANE);
        Material secondaryMat = parseMaterial(plugin.getConfigManager().getShopFillerSecondary(), Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        Material accentMat = parseMaterial(plugin.getConfigManager().getShopFillerAccent(), Material.SEA_LANTERN);

        ItemStack primary = createFiller(primaryMat);
        ItemStack secondary = createFiller(secondaryMat);
        ItemStack accent = createFiller(accentMat);

        int size = inventory.getSize();
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int col = slot % 9;

            boolean outer = row == 0 || row == 5 || col == 0 || col == 8;
            boolean corner = (row == 0 || row == 5) && (col == 0 || col == 8);
            boolean edgeAccent = (col == 0 || col == 8) && (row == 2 || row == 3);
            boolean inner = row == 1 || row == 4;

            if (corner || edgeAccent) {
                inventory.setItem(slot, accent);
            } else if (outer) {
                inventory.setItem(slot, primary);
            } else if (inner) {
                inventory.setItem(slot, secondary);
            } else {
                inventory.setItem(slot, secondary);
            }
        }
    }

    private ItemStack createFiller(Material material) {
        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            filler.setItemMeta(meta);
        }
        return filler;
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private List<EntityType> getAvailableEntities() {
        return java.util.Arrays.stream(EntityType.values())
            .filter(SpawnerUtils::isMobEntityType)
            .sorted(Comparator.comparing(SpawnerUtils::getEntityDisplayName))
            .toList();
    }

    private List<Integer> getAvailableLevels() {
        List<Integer> levels = new ArrayList<>();
        levels.add(0);

        int maxLevel = Math.max(0, plugin.getConfigManager().getUpgradeMaxLevel());
        for (int level = 1; level <= maxLevel; level++) {
            if (plugin.getConfigManager().getUpgradeLevel(level) != null) {
                levels.add(level);
            }
        }

        return levels;
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

    private void giveSpawner(Player player, EntityType entityType, int level) {
        ItemStack spawner = plugin.getSpawnerManager().createSpawner(entityType, 1, level);
        player.getInventory().addItem(spawner).values()
            .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));

        player.sendMessage(plugin.getLocaleManager().getMessage(
            "admin.menu.give-success",
            "type", SpawnerUtils.getEntityDisplayName(entityType),
            "level", String.valueOf(Math.max(0, level))
        ));
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

    private Component deserializeNoItalic(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
            .deserialize(text == null ? "" : text)
            .decoration(TextDecoration.ITALIC, false);
    }
}

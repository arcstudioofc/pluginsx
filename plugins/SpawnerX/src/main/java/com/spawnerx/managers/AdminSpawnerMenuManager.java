package com.spawnerx.managers;

import com.spawnerx.SpawnerX;
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

/**
 * Gerencia o menu admin para entrega de spawners.
 */
public class AdminSpawnerMenuManager {

    private static final int MENU_SIZE = 54;
    private static final int LEVEL_MENU_SIZE = 27;
    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };
    private static final int SLOT_PREV = 45;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_NEXT = 53;
    private static final int SLOT_BACK = 18;

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

        openLevelMenu(player, entityType);
    }

    private void handleLevelViewClick(Player player, AdminSpawnerMenuHolder holder,
                                      PersistentDataContainer container, String nav) {
        if ("back".equals(nav)) {
            openMobMenu(player, 0);
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
            openLevelMenu(player, holder.getSelectedEntity());
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
            new AdminSpawnerMenuHolder(AdminSpawnerMenuHolder.View.MOBS, currentPage, null),
            MENU_SIZE,
            LegacyComponentSerializer.legacyAmpersand().deserialize(title)
        );

        fillBackground(inventory, MENU_SIZE);

        int start = currentPage * CONTENT_SLOTS.length;
        int end = Math.min(start + CONTENT_SLOTS.length, mobs.size());
        int contentIndex = 0;

        for (int i = start; i < end; i++) {
            EntityType entityType = mobs.get(i);
            inventory.setItem(CONTENT_SLOTS[contentIndex], createMobItem(entityType));
            contentIndex++;
        }

        if (currentPage > 0) {
            inventory.setItem(SLOT_PREV, createNavItem("prev", Material.ARROW,
                plugin.getLocaleManager().getMessage("admin.menu.nav.prev")));
        }
        if (currentPage < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, createNavItem("next", Material.ARROW,
                plugin.getLocaleManager().getMessage("admin.menu.nav.next")));
        }

        inventory.setItem(SLOT_PAGE, createInfoItem(Material.PAPER,
            plugin.getLocaleManager().getMessage("admin.menu.nav.page",
                "page", String.valueOf(currentPage + 1),
                "pages", String.valueOf(totalPages))));

        player.openInventory(inventory);
    }

    private void openLevelMenu(Player player, EntityType entityType) {
        List<Integer> levels = getAvailableLevels();
        String entityName = SpawnerUtils.getEntityDisplayName(entityType);

        String title = plugin.getLocaleManager().getMessage("admin.menu.levels.title", "type", entityName);
        Inventory inventory = Bukkit.createInventory(
            new AdminSpawnerMenuHolder(AdminSpawnerMenuHolder.View.LEVELS, 0, entityType),
            LEVEL_MENU_SIZE,
            LegacyComponentSerializer.legacyAmpersand().deserialize(title)
        );

        fillBackground(inventory, LEVEL_MENU_SIZE);
        inventory.setItem(SLOT_BACK, createNavItem("back", Material.BARRIER,
            plugin.getLocaleManager().getMessage("admin.menu.nav.back")));

        int[] levelSlots = {10, 11, 12, 13, 14, 15, 16};
        int index = 0;
        for (int level : levels) {
            if (index >= levelSlots.length) {
                break;
            }
            inventory.setItem(levelSlots[index], createLevelItem(entityType, level));
            index++;
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

        List<Component> lore = new ArrayList<>();
        lore.add(deserializeNoItalic(plugin.getLocaleManager().getMessage("admin.menu.mobs.item-lore")));
        meta.lore(lore);

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

        List<Component> lore = new ArrayList<>();
        lore.add(deserializeNoItalic(plugin.getLocaleManager().getMessage("admin.menu.levels.item-lore")));
        meta.lore(lore);

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(entityKey, PersistentDataType.STRING, entityType.name());
        container.set(levelKey, PersistentDataType.INTEGER, level);
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

    private void fillBackground(Inventory inventory, int size) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < size; i++) {
            inventory.setItem(i, filler);
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

    private Component deserializeNoItalic(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
            .deserialize(text == null ? "" : text)
            .decoration(TextDecoration.ITALIC, false);
    }
}

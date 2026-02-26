package com.spawnerx.managers;

import com.spawnerx.SpawnerX;
import com.spawnerx.utils.ExperienceUtils;
import com.spawnerx.utils.SkullUtils;
import com.spawnerx.utils.SpawnerUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gerencia o shop de spawners.
 */
public class SpawnerShopManager {

    public static final int SHOP_SIZE = 54;
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

    private final SpawnerX plugin;
    private final NamespacedKey shopEntityKey;
    private final NamespacedKey shopPriceKey;
    private final NamespacedKey shopCategoryKey;
    private final NamespacedKey shopNavKey;
    private final NamespacedKey shopSaleIdKey;

    public SpawnerShopManager(SpawnerX plugin) {
        this.plugin = plugin;
        this.shopEntityKey = new NamespacedKey(plugin, "shop_entity");
        this.shopPriceKey = new NamespacedKey(plugin, "shop_price");
        this.shopCategoryKey = new NamespacedKey(plugin, "shop_category");
        this.shopNavKey = new NamespacedKey(plugin, "shop_nav");
        this.shopSaleIdKey = new NamespacedKey(plugin, "shop_public_sale_id");
    }

    public void openShop(Player player) {
        if (plugin.isLicenseLocked()) {
            player.sendMessage(plugin.getLocaleManager().getMessage("license.locked"));
            return;
        }
        if (!plugin.getConfigManager().isShopEnabled()) {
            player.sendMessage(plugin.getLocaleManager().getMessage("shop.disabled"));
            return;
        }
        openCategories(player, 0);
    }

    public boolean isShopInventory(Inventory inventory) {
        return inventory.getHolder() instanceof SpawnerShopHolder;
    }

    public void handleClick(Player player, Inventory inventory, ItemStack clicked) {
        if (!(inventory.getHolder() instanceof SpawnerShopHolder holder)) {
            return;
        }
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        if (plugin.isLicenseLocked()) {
            player.sendMessage(plugin.getLocaleManager().getMessage("license.locked"));
            player.closeInventory();
            return;
        }
        if (!plugin.getConfigManager().isShopEnabled()) {
            player.sendMessage(plugin.getLocaleManager().getMessage("shop.disabled"));
            return;
        }

        switch (holder.getView()) {
            case CATEGORIES -> handleCategoriesClick(player, holder, clicked);
            case CATEGORY -> handleCategoryClick(player, holder, clicked);
            case PUBLIC_SALES -> handlePublicSalesClick(player, holder, clicked);
        }
    }

    private void handleCategoriesClick(Player player, SpawnerShopHolder holder, ItemStack clicked) {
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();

        String nav = container.get(shopNavKey, PersistentDataType.STRING);
        if ("prev".equals(nav)) {
            openCategories(player, holder.getPage() - 1);
            return;
        }
        if ("next".equals(nav)) {
            openCategories(player, holder.getPage() + 1);
            return;
        }
        if ("public-sales".equals(nav)) {
            openPublicSales(player, 0, holder.getPage());
            return;
        }

        String categoryId = container.get(shopCategoryKey, PersistentDataType.STRING);
        if (categoryId != null) {
            openCategory(player, categoryId, 0, holder.getPage());
        }
    }

    private void handleCategoryClick(Player player, SpawnerShopHolder holder, ItemStack clicked) {
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();

        String nav = container.get(shopNavKey, PersistentDataType.STRING);
        if ("prev".equals(nav)) {
            openCategory(player, holder.getCategoryId(), holder.getPage() - 1, holder.getParentPage());
            return;
        }
        if ("next".equals(nav)) {
            openCategory(player, holder.getCategoryId(), holder.getPage() + 1, holder.getParentPage());
            return;
        }
        if ("back".equals(nav)) {
            openCategories(player, holder.getParentPage());
            return;
        }

        String entityName = container.get(shopEntityKey, PersistentDataType.STRING);
        Integer price = container.get(shopPriceKey, PersistentDataType.INTEGER);
        if (entityName == null || price == null) {
            return;
        }

        EntityType type = SpawnerUtils.getEntityType(entityName);
        if (type == null || !SpawnerUtils.isMobEntityType(type)) {
            player.sendMessage(plugin.getLocaleManager().getMessage("shop.buy.invalid-item"));
            return;
        }

        int totalXp = ExperienceUtils.getTotalExperience(player);
        if (totalXp < price) {
            player.sendMessage(plugin.getLocaleManager().getMessage("shop.buy.not-enough-xp",
                "price", String.valueOf(price),
                "currency", plugin.getConfigManager().getShopCurrency()));
            return;
        }

        ExperienceUtils.setTotalExperience(player, totalXp - price);
        ItemStack spawner = plugin.getSpawnerManager().createSpawner(type, 1);
        player.getInventory().addItem(spawner);

        String typeName = SpawnerUtils.getEntityDisplayName(type);
        player.sendMessage(plugin.getLocaleManager().getMessage("shop.buy.success",
            "type", typeName,
            "price", String.valueOf(price),
            "currency", plugin.getConfigManager().getShopCurrency()));
    }

    private void handlePublicSalesClick(Player player, SpawnerShopHolder holder, ItemStack clicked) {
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();

        String nav = container.get(shopNavKey, PersistentDataType.STRING);
        if ("prev".equals(nav)) {
            openPublicSales(player, holder.getPage() - 1, holder.getParentPage());
            return;
        }
        if ("next".equals(nav)) {
            openPublicSales(player, holder.getPage() + 1, holder.getParentPage());
            return;
        }
        if ("back".equals(nav)) {
            openCategories(player, holder.getParentPage());
            return;
        }

        String saleId = container.get(shopSaleIdKey, PersistentDataType.STRING);
        if (saleId == null || saleId.isBlank()) {
            return;
        }

        PublicSalesManager.PurchaseResult result = plugin.getPublicSalesManager().buySale(player, saleId);
        switch (result.status()) {
            case DISABLED -> player.sendMessage(plugin.getLocaleManager().getMessage("shop.public.disabled"));
            case NOT_FOUND -> player.sendMessage(plugin.getLocaleManager().getMessage("shop.public.not-found"));
            case NOT_ENOUGH_XP -> {
                PublicSale sale = result.sale();
                if (sale != null) {
                    player.sendMessage(plugin.getLocaleManager().getMessage("shop.buy.not-enough-xp",
                        "price", String.valueOf(sale.priceXp()),
                        "currency", plugin.getConfigManager().getShopCurrency()));
                } else {
                    player.sendMessage(plugin.getLocaleManager().getMessage("shop.public.not-found"));
                }
            }
            case SUCCESS -> {
                PublicSale sale = result.sale();
                if (sale != null) {
                    player.sendMessage(plugin.getLocaleManager().getMessage("shop.public.buy.success",
                        "type", SpawnerUtils.getEntityDisplayName(sale.entityType()),
                        "items", String.valueOf(Math.max(1, sale.itemAmount())),
                        "price", String.valueOf(sale.priceXp()),
                        "currency", plugin.getConfigManager().getShopCurrency(),
                        "seller", sale.sellerName()));
                }
            }
            case OWN_SALE_REMOVED -> {
                PublicSale sale = result.sale();
                if (sale != null) {
                    String displayName = plugin.getSpawnerManager().formatSpawnerDisplayName(
                        sale.entityType(),
                        sale.stackSize(),
                        sale.level()
                    );
                    player.sendMessage(plugin.getLocaleManager().getMessage("shop.public.buy.removed-own",
                        "items", String.valueOf(Math.max(1, sale.itemAmount())),
                        "name", displayName));
                } else {
                    player.sendMessage(plugin.getLocaleManager().getMessage("shop.public.not-found"));
                }
            }
        }

        openPublicSales(player, holder.getPage(), holder.getParentPage());
    }

    private void openCategories(Player player, int page) {
        List<ShopCategory> categories = loadCategories();
        PageLayout<ShopCategory> layout = buildPageLayout(categories);
        int totalPages = layout.totalPages;
        int currentPage = clampPage(page, totalPages);

        String title = plugin.getLocaleManager().getMessage("shop.title.categories");
        Inventory inventory = Bukkit.createInventory(
            new SpawnerShopHolder(SpawnerShopHolder.View.CATEGORIES, null, currentPage, 0),
            SHOP_SIZE,
            LegacyComponentSerializer.legacyAmpersand().deserialize(title)
        );

        fillBackground(inventory);
        placeTitleBar(inventory, title);
        placeNavigation(inventory, currentPage, totalPages, false);

        Map<Integer, ShopCategory> manual = layout.manualByPageSlot.get(currentPage);
        if (manual != null) {
            for (Map.Entry<Integer, ShopCategory> entry : manual.entrySet()) {
                inventory.setItem(entry.getKey(), createCategoryItem(entry.getValue()));
            }
        }

        List<Integer> freeSlots = layout.freeSlotsByPage.get(currentPage);
        int start = layout.autoStartIndexByPage.get(currentPage);
        int end = Math.min(start + freeSlots.size(), layout.autoEntries.size());
        for (int i = start; i < end; i++) {
            ShopCategory category = layout.autoEntries.get(i);
            int slot = freeSlots.get(i - start);
            inventory.setItem(slot, createCategoryItem(category));
        }

        if (plugin.getPublicSalesManager().isEnabled()) {
            inventory.setItem(resolvePublicSalesMenuSlot(), createPublicSalesButtonItem());
        }

        player.openInventory(inventory);
    }

    private void openCategory(Player player, String categoryId, int page, int parentPage) {
        ShopCategory category = findCategory(categoryId);
        if (category == null) {
            openCategories(player, 0);
            return;
        }

        List<ShopItem> items = category.items;
        PageLayout<ShopItem> layout = buildPageLayout(items);
        int totalPages = layout.totalPages;
        int currentPage = clampPage(page, totalPages);

        String title = plugin.getLocaleManager().getMessage("shop.title.category",
            "category", category.title,
            "page", String.valueOf(currentPage + 1),
            "pages", String.valueOf(totalPages));
        Inventory inventory = Bukkit.createInventory(
            new SpawnerShopHolder(SpawnerShopHolder.View.CATEGORY, category.id, currentPage, parentPage),
            SHOP_SIZE,
            LegacyComponentSerializer.legacyAmpersand().deserialize(title)
        );

        fillBackground(inventory);
        placeTitleBar(inventory, category.title);
        placeNavigation(inventory, currentPage, totalPages, true);

        Map<Integer, ShopItem> manual = layout.manualByPageSlot.get(currentPage);
        if (manual != null) {
            for (Map.Entry<Integer, ShopItem> entry : manual.entrySet()) {
                inventory.setItem(entry.getKey(), createShopItem(entry.getValue()));
            }
        }

        List<Integer> freeSlots = layout.freeSlotsByPage.get(currentPage);
        int start = layout.autoStartIndexByPage.get(currentPage);
        int end = Math.min(start + freeSlots.size(), layout.autoEntries.size());
        for (int i = start; i < end; i++) {
            ShopItem item = layout.autoEntries.get(i);
            int slot = freeSlots.get(i - start);
            inventory.setItem(slot, createShopItem(item));
        }

        player.openInventory(inventory);
    }

    private void openPublicSales(Player player, int page, int parentPage) {
        List<PublicSale> sales = plugin.getPublicSalesManager().getActiveSales();

        int totalPages = Math.max(1, (int) Math.ceil((double) sales.size() / CONTENT_SLOTS.length));
        int currentPage = clampPage(page, totalPages);

        String title = plugin.getLocaleManager().getMessage("shop.public.title",
            "page", String.valueOf(currentPage + 1),
            "pages", String.valueOf(totalPages));

        Inventory inventory = Bukkit.createInventory(
            new SpawnerShopHolder(SpawnerShopHolder.View.PUBLIC_SALES, null, currentPage, parentPage),
            SHOP_SIZE,
            LegacyComponentSerializer.legacyAmpersand().deserialize(title)
        );

        fillBackground(inventory);
        placeTitleBar(inventory, plugin.getLocaleManager().getMessage("shop.public.menu.name"));
        placeNavigation(inventory, currentPage, totalPages, true);

        if (sales.isEmpty()) {
            inventory.setItem(22, createPublicSalesEmptyItem());
            player.openInventory(inventory);
            return;
        }

        int start = currentPage * CONTENT_SLOTS.length;
        int end = Math.min(start + CONTENT_SLOTS.length, sales.size());
        int index = 0;

        for (int i = start; i < end; i++) {
            PublicSale sale = sales.get(i);
            inventory.setItem(CONTENT_SLOTS[index], createPublicSaleItem(sale));
            index++;
        }

        player.openInventory(inventory);
    }

    private void placeNavigation(Inventory inventory, int page, int totalPages, boolean showBack) {
        if (totalPages > 1 && page > 0) {
            inventory.setItem(SLOT_PREV, createNavItem("prev", plugin.getConfigManager().getShopNavHead("prev-head"),
                plugin.getLocaleManager().getMessage("shop.nav.prev")));
        }
        if (totalPages > 1 && page < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, createNavItem("next", plugin.getConfigManager().getShopNavHead("next-head"),
                plugin.getLocaleManager().getMessage("shop.nav.next")));
        }
        if (showBack) {
            String backHead = plugin.getConfigManager().getShopNavHead("back-head");
            if (backHead != null && !backHead.isBlank()) {
                inventory.setItem(SLOT_BACK, createNavItem("back", backHead,
                    plugin.getLocaleManager().getMessage("shop.nav.back")));
            }
        }

        ItemStack pageItem = new ItemStack(Material.PAPER);
        ItemMeta meta = pageItem.getItemMeta();
        if (meta != null) {
            meta.displayName(deserializeNoItalic(
                plugin.getLocaleManager().getMessage("shop.nav.page",
                    "page", String.valueOf(page + 1),
                    "pages", String.valueOf(totalPages))));
            pageItem.setItemMeta(meta);
        }
        inventory.setItem(SLOT_PAGE, pageItem);
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

    private ItemStack createFiller(Material material) {
        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            filler.setItemMeta(meta);
        }
        return filler;
    }

    private ItemStack createNavItem(String nav, String headBase64, String name) {
        ItemStack head = SkullUtils.createSkull(headBase64);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.displayName(deserializeNoItalic(name));
            meta.getPersistentDataContainer().set(shopNavKey, PersistentDataType.STRING, nav);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createCategoryItem(ShopCategory category) {
        ItemStack item;
        if (category.iconHead != null && !category.iconHead.isBlank()) {
            item = SkullUtils.createSkull(category.iconHead);
        } else if (category.iconMaterial != null && !category.iconMaterial.isBlank()) {
            item = new ItemStack(parseMaterial(category.iconMaterial, Material.BOOK));
        } else {
            item = new ItemStack(Material.BOOK);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(deserializeNoItalic(category.title));
            List<Component> lore = new ArrayList<>();
            lore.add(deserializeNoItalic(
                plugin.getLocaleManager().getMessage("shop.category.lore")));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(shopCategoryKey, PersistentDataType.STRING, category.id);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createShopItem(ShopItem item) {
        ItemStack display;
        if (item.head != null && !item.head.isBlank()) {
            display = SkullUtils.createSkull(item.head);
        } else if (item.material != null && !item.material.isBlank()) {
            display = new ItemStack(parseMaterial(item.material, Material.SPAWNER));
        } else {
            display = plugin.getSpawnerManager().createSpawner(item.type, 1);
        }

        ItemMeta meta = display.getItemMeta();
        if (meta == null) {
            return display;
        }

        String displayName = plugin.getSpawnerManager().formatSpawnerDisplayName(item.type, 1);
        String typeName = SpawnerUtils.getEntityDisplayName(item.type);
        String name = plugin.getLocaleManager().getMessage("shop.item.name",
            "display_name", displayName,
            "type", typeName);
        meta.displayName(deserializeNoItalic(name));

        List<Component> lore = new ArrayList<>();
        List<String> loreLines = buildItemLore(item, displayName, typeName);
        for (String line : loreLines) {
            lore.add(deserializeNoItalic(
                replaceLorePlaceholders(line, item, displayName, typeName)));
        }
        meta.lore(lore);

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(shopEntityKey, PersistentDataType.STRING, item.type.name());
        container.set(shopPriceKey, PersistentDataType.INTEGER, item.price);

        display.setItemMeta(meta);
        return display;
    }

    private ItemStack createPublicSalesButtonItem() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String name = plugin.getLocaleManager().getMessage("shop.public.menu.name");
        meta.displayName(deserializeNoItalic(name));

        String loreRaw = plugin.getLocaleManager().getMessage("shop.public.menu.lore");
        List<Component> lore = new ArrayList<>();
        for (String line : splitRawLines(loreRaw)) {
            lore.add(deserializeNoItalic(line));
        }
        meta.lore(lore);

        meta.getPersistentDataContainer().set(shopNavKey, PersistentDataType.STRING, "public-sales");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPublicSaleItem(PublicSale sale) {
        ItemStack display = plugin.getSpawnerManager().createSpawner(sale.entityType(), sale.stackSize(), sale.level());
        ItemMeta meta = display.getItemMeta();
        if (meta == null) {
            return display;
        }
        display.setAmount(Math.max(1, sale.itemAmount()));

        String typeName = SpawnerUtils.getEntityDisplayName(sale.entityType());
        String displayName = plugin.getSpawnerManager().formatSpawnerDisplayName(sale.entityType(), sale.stackSize(), sale.level());
        String expires = plugin.getPublicSalesManager().formatRemainingTime(sale);

        String name = plugin.getLocaleManager().getMessage("shop.public.item.name",
            "display_name", displayName,
            "type", typeName,
            "seller", sale.sellerName(),
            "price", String.valueOf(sale.priceXp()),
            "currency", plugin.getConfigManager().getShopCurrency());
        meta.displayName(deserializeNoItalic(name));

        String loreRaw = plugin.getLocaleManager().getMessage("shop.public.item.lore");
        List<Component> lore = new ArrayList<>();
        for (String line : splitRawLines(loreRaw)) {
            lore.add(deserializeNoItalic(
                replacePublicSalePlaceholders(line, sale, displayName, typeName, expires)));
        }
        meta.lore(lore);

        meta.getPersistentDataContainer().set(shopSaleIdKey, PersistentDataType.STRING, sale.id());
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack createPublicSalesEmptyItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(deserializeNoItalic(
                plugin.getLocaleManager().getMessage("shop.public.empty")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<ShopCategory> loadCategories() {
        List<ShopCategory> categories = new ArrayList<>();
        ConfigurationSection section = plugin.getConfigManager().getShopCategoriesSection();
        if (section == null) {
            return categories;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection cat = section.getConfigurationSection(key);
            if (cat == null) {
                continue;
            }

            String title = cat.getString("title", key);
            String iconHead = cat.getString("icon-head", "");
            String iconMaterial = cat.getString("icon-material", "");
            int catSlot = cat.getInt("slot", -1);
            int catPage = cat.getInt("page", 0);

            List<ShopItem> items = new ArrayList<>();
            ConfigurationSection itemsSection = cat.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String mobKey : itemsSection.getKeys(false)) {
                    ConfigurationSection itemSection = itemsSection.getConfigurationSection(mobKey);
                    int price;
                    String head = "";
                    String material = "";
                    int slot = -1;
                    int page = 0;
                    List<String> lore = new ArrayList<>();

                    if (itemSection != null) {
                        price = itemSection.getInt("price", -1);
                        head = itemSection.getString("head", "");
                        material = itemSection.getString("material", "");
                        slot = itemSection.getInt("slot", -1);
                        page = itemSection.getInt("page", 0);

                        if (itemSection.isList("lore")) {
                            lore.addAll(itemSection.getStringList("lore"));
                        } else {
                            String loreRaw = itemSection.getString("lore", "");
                            if (!loreRaw.isBlank()) {
                                lore.addAll(splitRawLines(loreRaw));
                            }
                        }
                    } else {
                        price = itemsSection.getInt(mobKey, -1);
                    }

                    if (price <= 0) {
                        continue;
                    }

                    EntityType type = SpawnerUtils.getEntityType(mobKey);
                    if (type == null || !SpawnerUtils.isMobEntityType(type)) {
                        plugin.getLogger().warning("Mob inválido no shop: " + mobKey);
                        continue;
                    }

                    items.add(new ShopItem(type, price, head, material, slot, page, lore));
                }
            }

            categories.add(new ShopCategory(key, title, iconHead, iconMaterial, items, catSlot, catPage));
        }

        return categories;
    }

    private ShopCategory findCategory(String id) {
        for (ShopCategory category : loadCategories()) {
            if (category.id.equalsIgnoreCase(id)) {
                return category;
            }
        }
        return null;
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

    private List<String> buildItemLore(ShopItem item, String displayName, String typeName) {
        if (item.lore != null && !item.lore.isEmpty()) {
            return item.lore;
        }

        String loreRaw = plugin.getLocaleManager().getMessage("shop.item.lore",
            "price", String.valueOf(item.price),
            "currency", plugin.getConfigManager().getShopCurrency());
        return splitRawLines(loreRaw);
    }

    private String replaceLorePlaceholders(String line, ShopItem item, String displayName, String typeName) {
        return line
            .replace("{price}", String.valueOf(item.price))
            .replace("{currency}", plugin.getConfigManager().getShopCurrency())
            .replace("{type}", typeName)
            .replace("{display_name}", displayName);
    }

    private String replacePublicSalePlaceholders(String line, PublicSale sale, String displayName, String typeName, String expires) {
        return line
            .replace("{display_name}", displayName)
            .replace("{type}", typeName)
            .replace("{seller}", sale.sellerName())
            .replace("{price}", String.valueOf(sale.priceXp()))
            .replace("{currency}", plugin.getConfigManager().getShopCurrency())
            .replace("{stack}", String.valueOf(sale.stackSize()))
            .replace("{level}", String.valueOf(sale.level()))
            .replace("{items}", String.valueOf(Math.max(1, sale.itemAmount())))
            .replace("{expires}", expires);
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
            .deserialize(text)
            .decoration(TextDecoration.ITALIC, false);
    }

    private int resolvePublicSalesMenuSlot() {
        int slot = plugin.getConfigManager().getShopPublicSalesMenuSlot();
        if (isBlockedSlot(slot)) {
            return 40;
        }
        return slot;
    }

    private boolean isBlockedSlot(int slot) {
        return slot < 0 || slot >= SHOP_SIZE
            || slot == SLOT_PREV
            || slot == SLOT_NEXT
            || slot == SLOT_BACK
            || slot == SLOT_PAGE
            || slot == SLOT_TITLE;
    }

    private <T extends SlotEntry> PageLayout<T> buildPageLayout(List<T> entries) {
        List<T> autoEntries = new ArrayList<>();
        Map<Integer, Map<Integer, T>> manualByPageSlot = new HashMap<>();
        int maxManualPage = 0;

        for (T entry : entries) {
            if (!entry.hasManualSlot()) {
                autoEntries.add(entry);
                continue;
            }

            int slot = entry.getSlot();
            int page = Math.max(0, entry.getPage());
            if (isBlockedSlot(slot)) {
                plugin.getLogger().warning("Shop slot " + slot + " is blocked; using auto placement instead.");
                autoEntries.add(entry);
                continue;
            }

            Map<Integer, T> pageMap = manualByPageSlot.computeIfAbsent(page, k -> new HashMap<>());
            if (pageMap.containsKey(slot)) {
                plugin.getLogger().warning("Duplicate shop slot " + slot + " on page " + page + "; using auto placement.");
                autoEntries.add(entry);
                continue;
            }

            pageMap.put(slot, entry);
            if (page > maxManualPage) {
                maxManualPage = page;
            }
        }

        List<List<Integer>> freeSlotsByPage = new ArrayList<>();
        int remainingAuto = autoEntries.size();
        int page = 0;

        while (page <= maxManualPage || remainingAuto > 0) {
            List<Integer> freeSlots = new ArrayList<>();
            for (int slot : CONTENT_SLOTS) {
                freeSlots.add(slot);
            }

            Map<Integer, T> manualSlots = manualByPageSlot.get(page);
            if (manualSlots != null) {
                freeSlots.removeIf(manualSlots::containsKey);
            }

            freeSlotsByPage.add(freeSlots);
            remainingAuto -= freeSlots.size();
            page++;
        }

        List<Integer> autoStartIndexByPage = new ArrayList<>();
        int index = 0;
        for (List<Integer> freeSlots : freeSlotsByPage) {
            autoStartIndexByPage.add(index);
            index += freeSlots.size();
        }

        return new PageLayout<>(autoEntries, manualByPageSlot, freeSlotsByPage, autoStartIndexByPage, freeSlotsByPage.size());
    }

    private interface SlotEntry {
        int getSlot();
        int getPage();

        default boolean hasManualSlot() {
            return getSlot() >= 0;
        }
    }

    private static class ShopCategory implements SlotEntry {
        private final String id;
        private final String title;
        private final String iconHead;
        private final String iconMaterial;
        private final List<ShopItem> items;
        private final int slot;
        private final int page;

        private ShopCategory(String id, String title, String iconHead, String iconMaterial, List<ShopItem> items,
                             int slot, int page) {
            this.id = id;
            this.title = title;
            this.iconHead = iconHead;
            this.iconMaterial = iconMaterial;
            this.items = items;
            this.slot = slot;
            this.page = page;
        }

        @Override
        public int getSlot() {
            return slot;
        }

        @Override
        public int getPage() {
            return page;
        }
    }

    private static class ShopItem implements SlotEntry {
        private final EntityType type;
        private final int price;
        private final String head;
        private final String material;
        private final int slot;
        private final int page;
        private final List<String> lore;

        private ShopItem(EntityType type, int price, String head, String material,
                         int slot, int page, List<String> lore) {
            this.type = type;
            this.price = price;
            this.head = head;
            this.material = material;
            this.slot = slot;
            this.page = page;
            this.lore = lore;
        }

        @Override
        public int getSlot() {
            return slot;
        }

        @Override
        public int getPage() {
            return page;
        }
    }

    private static class PageLayout<T extends SlotEntry> {
        private final List<T> autoEntries;
        private final Map<Integer, Map<Integer, T>> manualByPageSlot;
        private final List<List<Integer>> freeSlotsByPage;
        private final List<Integer> autoStartIndexByPage;
        private final int totalPages;

        private PageLayout(List<T> autoEntries,
                           Map<Integer, Map<Integer, T>> manualByPageSlot,
                           List<List<Integer>> freeSlotsByPage,
                           List<Integer> autoStartIndexByPage,
                           int totalPages) {
            this.autoEntries = autoEntries;
            this.manualByPageSlot = manualByPageSlot;
            this.freeSlotsByPage = freeSlotsByPage;
            this.autoStartIndexByPage = autoStartIndexByPage;
            this.totalPages = totalPages;
        }
    }
}

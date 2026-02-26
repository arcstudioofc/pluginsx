package com.spawnerx.listeners;

import com.spawnerx.SpawnerX;
import com.spawnerx.utils.SpawnerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Listener para interação com spawners (Shift + Botão Direito)
 */
public class SpawnerInteractListener implements Listener {

    private final SpawnerX plugin;
    private final Map<UUID, BukkitTask> activeMenus = new HashMap<>();
    private final Map<UUID, Block> playerSpawnerMap = new HashMap<>();

    public SpawnerInteractListener(SpawnerX plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SPAWNER) return;
        
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        if (plugin.isLicenseLocked()) {
            event.setCancelled(true);
            player.sendMessage(plugin.getLocaleManager().getMessage("license.locked"));
            return;
        }
        
        event.setCancelled(true);
        openSpawnerMenu(player, block);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (activeMenus.containsKey(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (activeMenus.containsKey(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (activeMenus.containsKey(uuid)) {
            activeMenus.get(uuid).cancel();
            activeMenus.remove(uuid);
            playerSpawnerMap.remove(uuid);
        }
    }

    /**
     * Abre o menu de informações do spawner
     */
    private void openSpawnerMenu(Player player, Block block) {
        CreatureSpawner spawner = (CreatureSpawner) block.getState();
        EntityType entityType = spawner.getSpawnedType();
        String entityName = SpawnerUtils.getEntityDisplayName(entityType);
        
        String title = plugin.getLocaleManager().getMessage("menu.title", "type", entityName);
        Inventory gui = Bukkit.createInventory(null, 27, LegacyComponentSerializer.legacyAmpersand().deserialize(title));
        
        // Vidros decorativos
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.displayName(Component.text(" "));
            glass.setItemMeta(glassMeta);
        }
        
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, glass);
        }

        // Item do Owner (Cabeça do jogador) - Slot 15 (Direita do centro 13)
        PersistentDataContainer container = spawner.getPersistentDataContainer();
        String ownerName = container.get(plugin.getSpawnerManager().getOwnerKey(), PersistentDataType.STRING);
        
        if (ownerName != null) {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
            if (skullMeta != null) {
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(ownerName));
                skullMeta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(plugin.getLocaleManager().getMessage("menu.owner-item.name", "player", ownerName)));
                List<Component> skullLore = new ArrayList<>();
                skullLore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(plugin.getLocaleManager().getMessage("menu.owner-item.lore")));
                skullMeta.lore(skullLore);
                skull.setItemMeta(skullMeta);
            }
            gui.setItem(15, skull);
        }

        player.openInventory(gui);
        playerSpawnerMap.put(player.getUniqueId(), block);

        // Task de atualização em tempo real
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory() != gui) {
                    this.cancel();
                    activeMenus.remove(player.getUniqueId());
                    playerSpawnerMap.remove(player.getUniqueId());
                    return;
                }

                updateMenu(player, gui, block);
            }
        }.runTaskTimer(plugin, 0L, 10L); // Atualiza a cada 10 ticks (0.5s)

        activeMenus.put(player.getUniqueId(), task);
    }

    /**
     * Atualiza os itens dinâmicos do menu
     */
    private void updateMenu(Player player, Inventory gui, Block block) {
        if (block.getType() != Material.SPAWNER) {
            player.closeInventory();
            return;
        }

        CreatureSpawner spawner = (CreatureSpawner) block.getState();
        EntityType entityType = spawner.getSpawnedType();
        String entityName = SpawnerUtils.getEntityDisplayName(entityType);
        PersistentDataContainer container = spawner.getPersistentDataContainer();
        int stack = container.getOrDefault(plugin.getSpawnerManager().getStackKey(), PersistentDataType.INTEGER, 1);
        int level = plugin.getSpawnerManager().getSpawnerLevel(spawner);

        // Item Central: Spawner (Slot 13)
        ItemStack info = new ItemStack(Material.SPAWNER);
        ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(plugin.getLocaleManager().getMessage("menu.info-item.name", "type", entityName)));
            
            String rarity = plugin.getConfigManager().getRarity(entityType.name());
            String rarityClean = rarity.replaceAll("&[0-9a-fk-or]", "");
            String rarityColor = extractColorCode(rarity);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(plugin.getLocaleManager().getMessage("spawner.lore.info")));
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(plugin.getLocaleManager().getMessage("spawner.lore.type", "type", entityName)));
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(plugin.getLocaleManager().getMessage("spawner.lore.rarity", 
                "rarity", rarityClean, "rarity_color", rarityColor)));
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(plugin.getLocaleManager().getMessage("spawner.lore.stack", "amount", String.valueOf(stack))));
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(plugin.getLocaleManager().getMessage("spawner.lore.level", "level", String.valueOf(level))));

            int activeDistance = plugin.getConfigManager().getSpawnerActiveDistance();
            boolean playerNearby = false;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(block.getWorld()) && p.getLocation().distance(block.getLocation()) <= activeDistance) {
                    playerNearby = true;
                    break;
                }
            }

            int spawnRange = spawner.getSpawnRange();
            int maxNearby = spawner.getMaxNearbyEntities();
            long nearbyCount = block.getWorld()
                .getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), spawnRange, spawnRange, spawnRange)
                .stream()
                .filter(entity -> entity.getType() == entityType)
                .count();
            boolean belowCap = maxNearby <= 0 || nearbyCount < maxNearby;
            boolean validEnvironment = playerNearby && belowCap;

            String envStatus = plugin.getLocaleManager().getMessage(validEnvironment ? "menu.status.valid" : "menu.status.invalid");
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(
                plugin.getLocaleManager().getMessage("menu.info-item.environment", "status", envStatus)));
            lore.add(Component.empty());
            
            meta.lore(lore);
            info.setItemMeta(meta);
        }
        gui.setItem(13, info);

        // Item de Informações Dinâmicas (Slot 11 - Esquerda do centro 13)
        ItemStack dynamicInfo = new ItemStack(Material.CLOCK);
        ItemMeta dynamicMeta = dynamicInfo.getItemMeta();
        if (dynamicMeta != null) {
            dynamicMeta.displayName(LegacyComponentSerializer.legacyAmpersand()
                .deserialize(plugin.getLocaleManager().getMessage("menu.dynamic.title")));
            
            List<Component> dynamicLore = new ArrayList<>();
            int delay = spawner.getDelay();
            int minDelay = spawner.getMinSpawnDelay();
            int maxDelay = spawner.getMaxSpawnDelay();
            
            // Status baseado se há jogadores por perto (requisito para spawner vanilla)
            int activeDistance = plugin.getConfigManager().getSpawnerActiveDistance();
            boolean active = false;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(block.getWorld()) && p.getLocation().distance(block.getLocation()) <= activeDistance) {
                    active = true;
                    break;
                }
            }

            dynamicLore.add(Component.empty());
            dynamicLore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(
                plugin.getLocaleManager().getMessage("menu.dynamic.spawn-tick", "delay", String.valueOf(delay))));
            dynamicLore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(
                plugin.getLocaleManager().getMessage("menu.dynamic.spawn-time",
                    "min", String.valueOf(minDelay / 20),
                    "max", String.valueOf(maxDelay / 20))));
            String activeStatus = plugin.getLocaleManager().getMessage(active ? "menu.dynamic.status-active" : "menu.dynamic.status-inactive");
            dynamicLore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(
                plugin.getLocaleManager().getMessage("menu.dynamic.status", "status", activeStatus)));
            dynamicLore.add(Component.empty());
            dynamicLore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(
                plugin.getLocaleManager().getMessage("menu.dynamic.footer")));
            
            dynamicMeta.lore(dynamicLore);
            dynamicInfo.setItemMeta(dynamicMeta);
        }
        gui.setItem(11, dynamicInfo);
    }

    private String extractColorCode(String rarity) {
        int idx = rarity.indexOf("&");
        if (idx >= 0 && idx + 1 < rarity.length()) {
            return rarity.substring(idx, idx + 2);
        }
        return "&f";
    }
}

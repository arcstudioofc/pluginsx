package com.spawnerx.managers;

import com.spawnerx.SpawnerX;
import com.spawnerx.utils.SpawnerUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gerenciador de spawners
 * Responsável por criar, modificar e gerenciar spawners
 */
public class SpawnerManager {

    private static final int FALLBACK_BASE_DELAY = 20;

    private final SpawnerX plugin;
    private final NamespacedKey entityKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey stackKey;
    private final NamespacedKey levelKey;
    private final NamespacedKey baseSpawnCountKey;
    private final NamespacedKey baseMaxNearbyKey;
    private final NamespacedKey baseDelayKey;
    private final NamespacedKey baseMinDelayKey;
    private final NamespacedKey baseMaxDelayKey;
    private final NamespacedKey hologramKey;

    private static final double HOLOGRAM_Y_OFFSET = 1.2;

    public SpawnerManager(SpawnerX plugin) {
        this.plugin = plugin;
        this.entityKey = new NamespacedKey(plugin, "spawner_entity");
        this.ownerKey = new NamespacedKey(plugin, "spawner_owner");
        this.stackKey = new NamespacedKey(plugin, "spawner_stack");
        this.levelKey = new NamespacedKey(plugin, "spawner_level");
        this.baseSpawnCountKey = new NamespacedKey(plugin, "spawner_base_spawn_count");
        this.baseMaxNearbyKey = new NamespacedKey(plugin, "spawner_base_max_nearby");
        this.baseDelayKey = new NamespacedKey(plugin, "spawner_base_delay");
        this.baseMinDelayKey = new NamespacedKey(plugin, "spawner_base_min_delay");
        this.baseMaxDelayKey = new NamespacedKey(plugin, "spawner_base_max_delay");
        this.hologramKey = new NamespacedKey(plugin, "spawner_hologram_id");
    }

    /**
     * Cria um item de spawner
     * @param entityType Tipo da entidade
     * @return ItemStack do spawner
     */
    public ItemStack createSpawner(EntityType entityType) {
        return createSpawner(entityType, 1, 0);
    }

    /**
     * Cria um item de spawner com quantidade específica no stack (NBT)
     * @param entityType Tipo da entidade
     * @param stackSize Tamanho do stack
     * @return ItemStack do spawner
     */
    public ItemStack createSpawner(EntityType entityType, int stackSize) {
        return createSpawner(entityType, stackSize, 0);
    }

    /**
     * Cria um item de spawner com nível de upgrade.
     */
    public ItemStack createSpawner(EntityType entityType, int stackSize, int level) {
        int safeStack = Math.max(1, stackSize);
        int safeLevel = Math.max(0, level);

        ItemStack spawner = new ItemStack(Material.SPAWNER);
        ItemMeta meta = spawner.getItemMeta();

        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(entityKey, PersistentDataType.STRING, entityType.name());
            container.set(stackKey, PersistentDataType.INTEGER, safeStack);
            container.set(levelKey, PersistentDataType.INTEGER, safeLevel);

            String displayName = formatSpawnerDisplayName(entityType, safeStack, safeLevel);
            meta.displayName(deserializeNoItalic(displayName));

            String entityName = SpawnerUtils.getEntityDisplayName(entityType);
            String rarity = plugin.getConfigManager().getRarity(entityType.name());
            String rarityClean = rarity.replaceAll("&[0-9a-fk-or]", "");
            String rarityColor = extractColorCode(rarity);

            List<String> loreTemplate = plugin.getConfigManager().getSpawnerLore();
            List<Component> lore = loreTemplate.stream()
                .map(line -> deserializeNoItalic(replacePlaceholders(
                    line, entityName, rarityClean, rarityColor, safeStack, safeLevel)))
                .collect(Collectors.toCollection(ArrayList::new));

            String upgradeLoreLine = plugin.getConfigManager().getUpgradeLevelLoreLine(safeLevel);
            if (safeLevel > 0 && upgradeLoreLine != null && !upgradeLoreLine.isBlank()) {
                lore.add(deserializeNoItalic(
                    replacePlaceholders(upgradeLoreLine, entityName, rarityClean, rarityColor, safeStack, safeLevel)));
            }

            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ITEM_SPECIFICS);
            spawner.setItemMeta(meta);
        }

        return spawner;
    }

    /**
     * Método de compatibilidade para chamadas antigas.
     */
    public void applyStackToSpawner(CreatureSpawner spawner, int stackSize) {
        applySpawnerStats(spawner, stackSize, getSpawnerLevel(spawner));
    }

    /**
     * Aplica os atributos de spawn combinando stack + upgrade.
     */
    public void applySpawnerStats(CreatureSpawner spawner, int stackSize, int level) {
        applySpawnerStatsInternal(spawner, stackSize, level, true);
    }

    /**
     * Aplica os atributos de spawn combinando stack + upgrade.
     * Quando applyCurrentDelay=false, mantém o delay atual do ciclo sem reset.
     */
    private boolean applySpawnerStatsInternal(CreatureSpawner spawner, int stackSize, int level, boolean applyCurrentDelay) {
        if (spawner == null) {
            return false;
        }

        PersistentDataContainer container = spawner.getPersistentDataContainer();
        int safeStack = Math.max(1, stackSize);
        int safeLevel = Math.max(0, level);
        container.set(levelKey, PersistentDataType.INTEGER, safeLevel);

        boolean metadataChanged = false;
        int defaultBaseSpawn = plugin.getConfigManager().getSpawnerBaseSpawnCount();
        int defaultBaseMaxNearby = plugin.getConfigManager().getSpawnerBaseMaxNearbyEntities();
        int defaultBaseMinDelay = plugin.getConfigManager().getSpawnerBaseMinSpawnDelay();
        int defaultBaseMaxDelay = plugin.getConfigManager().getSpawnerBaseMaxSpawnDelay();

        int baseSpawn;
        if (!container.has(baseSpawnCountKey, PersistentDataType.INTEGER)) {
            baseSpawn = defaultBaseSpawn;
            container.set(baseSpawnCountKey, PersistentDataType.INTEGER, baseSpawn);
            metadataChanged = true;
        } else {
            baseSpawn = container.getOrDefault(baseSpawnCountKey, PersistentDataType.INTEGER, defaultBaseSpawn);
            if (baseSpawn <= 0) {
                baseSpawn = defaultBaseSpawn;
                container.set(baseSpawnCountKey, PersistentDataType.INTEGER, baseSpawn);
                metadataChanged = true;
            }
        }

        int baseMaxNearby;
        if (!container.has(baseMaxNearbyKey, PersistentDataType.INTEGER)) {
            baseMaxNearby = defaultBaseMaxNearby;
            container.set(baseMaxNearbyKey, PersistentDataType.INTEGER, baseMaxNearby);
            metadataChanged = true;
        } else {
            baseMaxNearby = container.getOrDefault(baseMaxNearbyKey, PersistentDataType.INTEGER, defaultBaseMaxNearby);
            if (baseMaxNearby <= 0) {
                baseMaxNearby = defaultBaseMaxNearby;
                container.set(baseMaxNearbyKey, PersistentDataType.INTEGER, baseMaxNearby);
                metadataChanged = true;
            }
        }

        int baseMinDelay;
        if (!container.has(baseMinDelayKey, PersistentDataType.INTEGER)) {
            baseMinDelay = defaultBaseMinDelay;
            container.set(baseMinDelayKey, PersistentDataType.INTEGER, baseMinDelay);
            metadataChanged = true;
        } else {
            baseMinDelay = container.getOrDefault(baseMinDelayKey, PersistentDataType.INTEGER, defaultBaseMinDelay);
            if (baseMinDelay <= 0) {
                baseMinDelay = defaultBaseMinDelay;
                container.set(baseMinDelayKey, PersistentDataType.INTEGER, baseMinDelay);
                metadataChanged = true;
            }
        }

        int baseMaxDelay;
        if (!container.has(baseMaxDelayKey, PersistentDataType.INTEGER)) {
            baseMaxDelay = Math.max(baseMinDelay, defaultBaseMaxDelay);
            container.set(baseMaxDelayKey, PersistentDataType.INTEGER, baseMaxDelay);
            metadataChanged = true;
        } else {
            baseMaxDelay = container.getOrDefault(baseMaxDelayKey, PersistentDataType.INTEGER, defaultBaseMaxDelay);
            if (baseMaxDelay < baseMinDelay) {
                baseMaxDelay = Math.max(baseMinDelay, defaultBaseMaxDelay);
                container.set(baseMaxDelayKey, PersistentDataType.INTEGER, baseMaxDelay);
                metadataChanged = true;
            }
        }

        int currentDelay = spawner.getDelay();
        int defaultBaseDelay = currentDelay > 0
            ? clamp(currentDelay, baseMinDelay, baseMaxDelay)
            : Math.max(FALLBACK_BASE_DELAY, baseMinDelay);
        int baseDelay;
        if (!container.has(baseDelayKey, PersistentDataType.INTEGER)) {
            baseDelay = defaultBaseDelay;
            container.set(baseDelayKey, PersistentDataType.INTEGER, baseDelay);
            metadataChanged = true;
        } else {
            baseDelay = container.getOrDefault(baseDelayKey, PersistentDataType.INTEGER, defaultBaseDelay);
            if (baseDelay < baseMinDelay || baseDelay > baseMaxDelay) {
                baseDelay = clamp(baseDelay, baseMinDelay, baseMaxDelay);
                container.set(baseDelayKey, PersistentDataType.INTEGER, baseDelay);
                metadataChanged = true;
            }
        }

        boolean chainedMode = plugin.getConfigManager().isStackSpawnChained();
        int chainDelayFloor = chainedMode ? plugin.getConfigManager().getStackChainMinDelayTicks() : 1;
        double spawnMultiplier = chainedMode ? 1.0D : safeStack;
        double maxNearbyMultiplier = safeStack;
        double delayDivider = chainedMode ? safeStack : 1.0D;

        ConfigManager.UpgradeLevel upgradeLevel = plugin.getConfigManager().getUpgradeLevel(safeLevel);
        if (upgradeLevel != null && safeLevel > 0) {
            double bonusFactor = Math.max(1.0D, 1.0D + upgradeLevel.boost());
            ConfigManager.BoostType boostType = plugin.getConfigManager().getUpgradeBoostType();

            switch (boostType) {
                case MULTIPLIER -> delayDivider *= bonusFactor;
                case ADDITIVE -> {
                    spawnMultiplier *= bonusFactor;
                    maxNearbyMultiplier *= bonusFactor;
                }
                case HYBRID -> {
                    spawnMultiplier *= bonusFactor;
                    maxNearbyMultiplier *= bonusFactor;
                    delayDivider *= bonusFactor;
                }
            }
        }

        int newSpawn = Math.max(1, (int) Math.round(baseSpawn * spawnMultiplier));
        int newMaxNearby = Math.max(1, (int) Math.round(baseMaxNearby * maxNearbyMultiplier));
        int newDelay = Math.max(chainDelayFloor, (int) Math.round(baseDelay / delayDivider));
        int newMinDelay = Math.max(chainDelayFloor, (int) Math.round(baseMinDelay / delayDivider));
        int newMaxDelay = Math.max(newMinDelay, Math.max(chainDelayFloor, (int) Math.round(baseMaxDelay / delayDivider)));

        spawner.setSpawnCount(newSpawn);
        spawner.setMaxNearbyEntities(newMaxNearby);
        spawner.setMinSpawnDelay(newMinDelay);
        spawner.setMaxSpawnDelay(newMaxDelay);
        if (applyCurrentDelay) {
            spawner.setDelay(newDelay);
        }
        return metadataChanged;
    }

    /**
     * Reconcília atributos de spawn para spawners legados/desalinhados.
     * Retorna true quando algum valor foi alterado.
     */
    public boolean reconcileSpawnerStats(CreatureSpawner spawner) {
        return reconcileSpawnerStats(spawner, false);
    }

    /**
     * Reconcília atributos de spawn para spawners legados/desalinhados.
     * Retorna true quando algum valor foi alterado.
     */
    public boolean reconcileSpawnerStats(CreatureSpawner spawner, boolean applyCurrentDelay) {
        if (spawner == null) {
            return false;
        }

        PersistentDataContainer container = spawner.getPersistentDataContainer();
        boolean changed = false;

        int stack = container.getOrDefault(stackKey, PersistentDataType.INTEGER, 1);
        if (stack <= 0) {
            stack = 1;
        }
        if (!container.has(stackKey, PersistentDataType.INTEGER) ||
            container.getOrDefault(stackKey, PersistentDataType.INTEGER, 1) != stack) {
            container.set(stackKey, PersistentDataType.INTEGER, stack);
            changed = true;
        }

        int level = container.getOrDefault(levelKey, PersistentDataType.INTEGER, 0);
        if (level < 0) {
            level = 0;
        }
        if (!container.has(levelKey, PersistentDataType.INTEGER) ||
            container.getOrDefault(levelKey, PersistentDataType.INTEGER, 0) != level) {
            container.set(levelKey, PersistentDataType.INTEGER, level);
            changed = true;
        }

        int beforeSpawn = spawner.getSpawnCount();
        int beforeMaxNearby = spawner.getMaxNearbyEntities();
        int beforeMinDelay = spawner.getMinSpawnDelay();
        int beforeMaxDelay = spawner.getMaxSpawnDelay();

        boolean metadataChanged = applySpawnerStatsInternal(spawner, stack, level, applyCurrentDelay);

        return changed
            || metadataChanged
            || beforeSpawn != spawner.getSpawnCount()
            || beforeMaxNearby != spawner.getMaxNearbyEntities()
            || beforeMinDelay != spawner.getMinSpawnDelay()
            || beforeMaxDelay != spawner.getMaxSpawnDelay();
    }

    /**
     * Reconcília todos os spawners gerenciados pelo SpawnerX em um chunk.
     * Retorna a quantidade de spawners atualizados.
     */
    public int reconcileChunkSpawners(Chunk chunk, boolean applyCurrentDelay) {
        if (chunk == null) {
            return 0;
        }

        int updated = 0;
        for (BlockState state : chunk.getTileEntities()) {
            if (!(state instanceof CreatureSpawner spawner)) {
                continue;
            }

            PersistentDataContainer container = spawner.getPersistentDataContainer();
            if (!container.has(stackKey, PersistentDataType.INTEGER)) {
                continue;
            }

            boolean changed = reconcileSpawnerStats(spawner, applyCurrentDelay);
            if (changed) {
                spawner.update();
                updated++;
            }
        }

        return updated;
    }

    /**
     * Atualiza ou cria o holograma do spawner
     */
    public void updateSpawnerHologram(Block block, EntityType entityType, int stackSize) {
        int level = getSpawnerLevel(block);
        updateSpawnerHologram(block, entityType, stackSize, level);
    }

    /**
     * Atualiza ou cria o holograma do spawner com nível.
     */
    public void updateSpawnerHologram(Block block, EntityType entityType, int stackSize, int level) {
        if (block == null || block.getType() != Material.SPAWNER) return;

        CreatureSpawner spawner = (CreatureSpawner) block.getState();
        PersistentDataContainer container = spawner.getPersistentDataContainer();

        Component text = LegacyComponentSerializer.legacyAmpersand()
            .deserialize(formatSpawnerDisplayName(entityType, stackSize, level));

        TextDisplay display = getExistingHologram(block.getWorld(), container);
        Location location = block.getLocation().add(0.5, HOLOGRAM_Y_OFFSET, 0.5);

        if (display == null || display.isDead()) {
            display = spawnHologram(block.getWorld(), location, text);
            container.set(hologramKey, PersistentDataType.STRING, display.getUniqueId().toString());
        } else {
            display.text(text);
            display.teleport(location);
        }

        spawner.update();
    }

    /**
     * Remove o holograma do spawner, se existir
     */
    public void removeSpawnerHologram(Block block) {
        if (block == null || block.getType() != Material.SPAWNER) return;

        CreatureSpawner spawner = (CreatureSpawner) block.getState();
        PersistentDataContainer container = spawner.getPersistentDataContainer();
        TextDisplay display = getExistingHologram(block.getWorld(), container);

        if (display != null) {
            display.remove();
        }

        container.remove(hologramKey);
        spawner.update();
    }

    /**
     * Formata o nome do spawner com prefixo de stack
     */
    public String formatSpawnerDisplayName(EntityType entityType, int stackSize) {
        return formatSpawnerDisplayName(entityType, stackSize, 0);
    }

    /**
     * Formata o nome do spawner com prefixo de stack e sufixo de upgrade.
     */
    public String formatSpawnerDisplayName(EntityType entityType, int stackSize, int level) {
        int safeStack = Math.max(1, stackSize);
        int safeLevel = Math.max(0, level);

        String entityName = SpawnerUtils.getEntityDisplayName(entityType);
        String rarity = plugin.getConfigManager().getRarity(entityType.name());
        String rarityClean = rarity.replaceAll("&[0-9a-fk-or]", "");
        String rarityColor = extractColorCode(rarity);

        String baseName = replacePlaceholders(plugin.getConfigManager().getSpawnerDisplayName(),
            entityName, rarityClean, rarityColor, safeStack, safeLevel);

        String suffix = plugin.getConfigManager().getUpgradeLevelNameSuffix(safeLevel);
        if (suffix != null && !suffix.isBlank()) {
            baseName = baseName + suffix;
        }

        return safeStack + "x " + baseName;
    }

    /**
     * Obtém o tipo de entidade do spawner (via NBT ou BlockState)
     * @param item ItemStack do spawner
     * @return EntityType ou null
     */
    public EntityType getSpawnerEntity(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(entityKey, PersistentDataType.STRING)) {
            try {
                return EntityType.valueOf(container.get(entityKey, PersistentDataType.STRING));
            } catch (Exception ignored) {}
        }

        if (meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.hasBlockState()) {
            try {
                if (blockStateMeta.getBlockState() instanceof CreatureSpawner spawnerState) {
                    return spawnerState.getSpawnedType();
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    /**
     * Verifica se um item é um spawner válido do plugin
     * @param item ItemStack
     * @return true se for um spawner válido
     */
    public boolean isValidSpawner(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            return container.has(entityKey, PersistentDataType.STRING);
        }

        return false;
    }

    /**
     * Detecta itens legados que ainda possuem BlockState serializado.
     */
    public boolean isLegacySpawnerWithBlockState(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockStateMeta)) {
            return false;
        }

        return blockStateMeta.hasBlockState();
    }

    /**
     * Recria o spawner sem BlockState serializado para remover warning do tooltip.
     */
    public ItemStack sanitizeSpawnerItem(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) {
            return item;
        }

        if (!isLegacySpawnerWithBlockState(item)) {
            return item;
        }

        EntityType entityType = getSpawnerEntity(item);
        if (entityType == null) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        // Nunca sanitizar item aleatório de terceiros: exige assinatura SpawnerX.
        PersistentDataContainer container = meta.getPersistentDataContainer();
        boolean looksLikeSpawnerX = container.has(entityKey, PersistentDataType.STRING)
            || container.has(stackKey, PersistentDataType.INTEGER)
            || container.has(levelKey, PersistentDataType.INTEGER);
        if (!looksLikeSpawnerX) {
            return item;
        }

        int stackSize = Math.max(1, getSpawnerStack(item));
        int level = Math.max(0, getSpawnerLevel(item));
        int amount = Math.max(1, item.getAmount());

        ItemStack sanitized = createSpawner(entityType, stackSize, level);
        sanitized.setAmount(amount);
        return sanitized;
    }

    /**
     * Obtém o stack salvo no item do spawner (NBT)
     * @param item ItemStack do spawner
     * @return stack salvo ou 1
     */
    public int getSpawnerStack(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) {
            return 1;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 1;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.getOrDefault(stackKey, PersistentDataType.INTEGER, 1);
    }

    public int getSpawnerLevel(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.getOrDefault(levelKey, PersistentDataType.INTEGER, 0);
    }

    public int getSpawnerLevel(CreatureSpawner spawner) {
        if (spawner == null) {
            return 0;
        }
        return spawner.getPersistentDataContainer().getOrDefault(levelKey, PersistentDataType.INTEGER, 0);
    }

    public int getSpawnerLevel(Block block) {
        if (block == null || block.getType() != Material.SPAWNER) {
            return 0;
        }
        return getSpawnerLevel((CreatureSpawner) block.getState());
    }

    public void setSpawnerLevel(CreatureSpawner spawner, int level) {
        if (spawner == null) {
            return;
        }
        int safeLevel = Math.max(0, level);
        spawner.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, safeLevel);
    }

    public NamespacedKey getEntityKey() {
        return entityKey;
    }

    public NamespacedKey getOwnerKey() {
        return ownerKey;
    }

    public NamespacedKey getStackKey() {
        return stackKey;
    }

    public NamespacedKey getLevelKey() {
        return levelKey;
    }

    private String replacePlaceholders(String template, String entityName, String rarity,
                                       String rarityColor, int stackSize, int level) {
        return template
            .replace("{type}", entityName)
            .replace("{rarity}", rarity)
            .replace("{rarity_color}", rarityColor)
            .replace("{stack_size}", String.valueOf(stackSize))
            .replace("{level}", String.valueOf(level));
    }

    private int getOrStoreBase(PersistentDataContainer container, NamespacedKey key, int current) {
        if (!container.has(key, PersistentDataType.INTEGER)) {
            container.set(key, PersistentDataType.INTEGER, current);
            return current;
        }
        return container.getOrDefault(key, PersistentDataType.INTEGER, current);
    }

    private int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private TextDisplay getExistingHologram(World world, PersistentDataContainer container) {
        String id = container.get(hologramKey, PersistentDataType.STRING);
        if (id == null || id.isBlank()) return null;
        try {
            UUID uuid = UUID.fromString(id);
            org.bukkit.entity.Entity entity = world.getEntity(uuid);
            if (entity instanceof TextDisplay) {
                return (TextDisplay) entity;
            }
        } catch (IllegalArgumentException ignored) {}
        return null;
    }

    private TextDisplay spawnHologram(World world, Location location, Component text) {
        return world.spawn(location, TextDisplay.class, display -> {
            display.text(text);
            display.setBillboard(Billboard.CENTER);
            display.setGravity(false);
        });
    }

    private String extractColorCode(String rarity) {
        int idx = rarity.indexOf("&");
        if (idx >= 0 && idx + 1 < rarity.length()) {
            return rarity.substring(idx, idx + 2);
        }
        return "&f";
    }

    private Component deserializeNoItalic(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
            .deserialize(text)
            .decoration(TextDecoration.ITALIC, false);
    }
}

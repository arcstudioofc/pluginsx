package com.spawnerx.managers;

import com.spawnerx.SpawnerX;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Gerenciador de configuração do plugin
 * Responsável por carregar e fornecer acesso às configurações
 */
public class ConfigManager {

    private static final int INTERNAL_LICENSE_REQUEST_TIMEOUT_MS = 5000;

    public enum BoostType {
        MULTIPLIER,
        ADDITIVE,
        HYBRID
    }

    public enum StackSpawnMode {
        CHAINED,
        SIMULTANEOUS
    }

    public enum StackSpawnOrder {
        SEQUENTIAL,
        RANDOM,
        RANDOM_CYCLE
    }

    public record UpgradeLevel(
        String id,
        int level,
        double boost,
        Material costMaterial,
        int costAmount,
        int maxStackSize,
        String nameSuffix,
        String loreLine
    ) {}

    public record RarityDefinition(String displayName, String color, int tier) {}

    private final SpawnerX plugin;
    private FileConfiguration config;
    private FileConfiguration shopConfig;
    private java.io.File shopFile;

    private final Map<Integer, UpgradeLevel> upgradeLevels = new TreeMap<>();
    private final Set<Material> upgradeMaterials = new HashSet<>();

    private boolean upgradeActive;
    private boolean upgradeRequirePermission;
    private String upgradePermissionNode = "spawnerx.spawner.upgrade";
    private BoostType upgradeBoostType = BoostType.HYBRID;
    private int upgradeMaxLevel;
    private StackSpawnMode stackSpawnMode = StackSpawnMode.CHAINED;
    private StackSpawnOrder stackSpawnOrder = StackSpawnOrder.RANDOM_CYCLE;
    private int stackChainMinDelayTicks = 5;

    private final Map<String, RarityDefinition> rarityDefinitions = new HashMap<>();
    private final Map<String, String> mobRarityMap = new HashMap<>();

    public ConfigManager(SpawnerX plugin) {
        this.plugin = plugin;
    }

    /**
     * Carrega a configuração do plugin
     */
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        loadShopConfig();
        migrateConfig();
        parseUpgradeConfig();
        parseStackSpawnConfig();
        parseRarityConfig();
    }

    private void loadShopConfig() {
        if (shopFile == null) {
            shopFile = new java.io.File(plugin.getDataFolder(), "shop.yml");
        }
        if (!shopFile.exists()) {
            plugin.saveResource("shop.yml", false);
        }
        shopConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(shopFile);
        java.io.InputStream defaultStream = plugin.getResource("shop.yml");
        if (defaultStream != null) {
            org.bukkit.configuration.file.YamlConfiguration defaults = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(defaultStream, java.nio.charset.StandardCharsets.UTF_8));
            shopConfig.setDefaults(defaults);
            shopConfig.options().copyDefaults(true);
            saveShopConfig();
        }
    }

    private void saveShopConfig() {
        try {
            if (shopConfig != null && shopFile != null) {
                shopConfig.save(shopFile);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao salvar shop.yml: " + e.getMessage());
        }
    }

    /**
     * Migra configurações antigas para o novo formato
     */
    private void migrateConfig() {
        boolean changed = false;
        boolean shopChanged = false;

        // Migração: required-tool -> tools
        if (config.contains("break.required-tool")) {
            String oldTool = config.getString("break.required-tool");
            List<String> tools = config.getStringList("break.tools");
            if (tools.isEmpty() && oldTool != null && !oldTool.isBlank()) {
                tools = new ArrayList<>();
                tools.add(oldTool);
                config.set("break.tools", tools);
            }
            config.set("break.required-tool", null);
            changed = true;
        }

        // Migração: require-silk-touch -> silk-touch
        if (config.contains("break.require-silk-touch")) {
            config.set("break.silk-touch", config.getBoolean("break.require-silk-touch"));
            config.set("break.require-silk-touch", null);
            changed = true;
        }

        // Migração: license.code (legado) -> license.key (canônico)
        String legacyLicenseCode = config.getString("license.code", "");
        String currentLicenseKey = config.getString("license.key", "");
        boolean hasLegacyCode = legacyLicenseCode != null && !legacyLicenseCode.isBlank();
        boolean hasCurrentKey = currentLicenseKey != null && !currentLicenseKey.isBlank();
        if (hasLegacyCode && !hasCurrentKey) {
            config.set("license.key", legacyLicenseCode.trim());
            changed = true;
        }
        if (config.contains("license.code")) {
            config.set("license.code", null);
            changed = true;
        }
        if (config.contains("license.request-timeout-ms")) {
            config.set("license.request-timeout-ms", null);
            changed = true;
        }

        // Migração: spawner.rarities -> rarities (correção de indentação)
        if (config.contains("spawner.rarities") && !config.contains("rarities")) {
            Object raritiesSection = config.get("spawner.rarities");
            if (raritiesSection != null) {
                config.set("rarities", raritiesSection);
                config.set("spawner.rarities", null);
                changed = true;
            }
        }

        // Migração: se shop ainda está em config.yml, copiar para shop.yml
        if (config.contains("spawner.shop") && (shopConfig == null || !shopConfig.contains("spawner.shop"))) {
            Object shopSection = config.get("spawner.shop");
            if (shopSection != null && shopConfig != null) {
                shopConfig.set("spawner.shop", shopSection);
                shopChanged = true;
            }
        }

        // Migração: spawner.shop.items -> spawner.shop.categories.DEFAULT.items (shop.yml)
        if (shopConfig != null && shopConfig.contains("spawner.shop.items")
            && !shopConfig.contains("spawner.shop.categories")) {
            ConfigurationSection oldItems = shopConfig.getConfigurationSection("spawner.shop.items");
            if (oldItems != null) {
                String basePath = "spawner.shop.categories.DEFAULT";
                if (!shopConfig.contains(basePath + ".title")) {
                    shopConfig.set(basePath + ".title", "&eDefault Spawners");
                }
                if (!shopConfig.contains(basePath + ".icon-head")) {
                    shopConfig.set(basePath + ".icon-head", "");
                }
                for (String key : oldItems.getKeys(false)) {
                    int price = oldItems.getInt(key, -1);
                    if (price > 0) {
                        shopConfig.set(basePath + ".items." + key + ".price", price);
                    }
                }
                shopConfig.set("spawner.shop.items", null);
                shopChanged = true;
            }
        }

        if (changed) {
            plugin.saveConfig();
            plugin.getLogger().info("Configurações migradas para o novo formato (prompt.md)");
        }
        if (shopChanged) {
            saveShopConfig();
        }
    }

    private void parseUpgradeConfig() {
        upgradeLevels.clear();
        upgradeMaterials.clear();

        upgradeActive = config.getBoolean("spawner.upgrade.active", false);
        upgradeRequirePermission = config.getBoolean("spawner.upgrade.require-permission", false);
        upgradePermissionNode = config.getString("spawner.upgrade.permission-node", "spawnerx.spawner.upgrade");
        if (upgradePermissionNode == null || upgradePermissionNode.isBlank()) {
            upgradePermissionNode = "spawnerx.spawner.upgrade";
        }

        upgradeBoostType = parseBoostType(config.getString("spawner.upgrade.boost-type", BoostType.HYBRID.name()));
        upgradeMaxLevel = Math.max(0, config.getInt("spawner.upgrade.max-level", 0));

        ConfigurationSection levelsSection = config.getConfigurationSection("spawner.upgrade.levels");
        if (levelsSection == null) {
            return;
        }

        Set<Integer> seenLevels = new HashSet<>();
        for (String id : levelsSection.getKeys(false)) {
            String path = "spawner.upgrade.levels." + id;
            int level = config.getInt(path + ".level", -1);
            if (level <= 0) {
                plugin.getLogger().warning("Upgrade '" + id + "' ignorado: level inválido (" + level + ")");
                continue;
            }
            if (!seenLevels.add(level)) {
                plugin.getLogger().warning("Upgrade '" + id + "' ignorado: level duplicado (" + level + ")");
                continue;
            }

            double boost = config.getDouble(path + ".boost", 0.0D);
            if (boost < 0) {
                plugin.getLogger().warning("Upgrade '" + id + "' possui boost negativo; usando 0.0");
                boost = 0.0D;
            }

            String materialName = config.getString(path + ".cost.material", "");
            Material material = parseMaterial(materialName);
            if (material == null) {
                plugin.getLogger().warning("Upgrade '" + id + "' ignorado: material inválido '" + materialName + "'");
                continue;
            }

            int costAmount = config.getInt(path + ".cost.amount", 0);
            if (costAmount <= 0) {
                plugin.getLogger().warning("Upgrade '" + id + "' ignorado: cost.amount inválido (" + costAmount + ")");
                continue;
            }

            int maxStack = config.getInt(path + ".max-stack-size", -1);
            if (maxStack <= 0) {
                maxStack = -1;
            }

            String nameSuffix = config.getString(path + ".display.name-suffix", "");
            String loreLine = config.getString(path + ".display.lore-line", "");

            UpgradeLevel parsed = new UpgradeLevel(id, level, boost, material, costAmount, maxStack, nameSuffix, loreLine);
            upgradeLevels.put(level, parsed);
            upgradeMaterials.add(material);
        }

        if (!upgradeLevels.isEmpty() && upgradeMaxLevel <= 0) {
            upgradeMaxLevel = upgradeLevels.keySet().stream().max(Integer::compareTo).orElse(0);
        }
    }

    private BoostType parseBoostType(String raw) {
        if (raw == null || raw.isBlank()) {
            return BoostType.HYBRID;
        }
        try {
            return BoostType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("boost-type inválido '" + raw + "'. Usando HYBRID.");
            return BoostType.HYBRID;
        }
    }

    private void parseStackSpawnConfig() {
        stackSpawnMode = parseStackSpawnMode(config.getString("spawner.stack-spawn.mode", StackSpawnMode.CHAINED.name()));
        stackSpawnOrder = parseStackSpawnOrder(
            config.getString("spawner.stack-spawn.order", StackSpawnOrder.RANDOM_CYCLE.name()));
        stackChainMinDelayTicks = Math.max(1, config.getInt("spawner.stack-spawn.chain-min-delay-ticks", 5));
    }

    private StackSpawnMode parseStackSpawnMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return StackSpawnMode.CHAINED;
        }
        try {
            return StackSpawnMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("stack-spawn.mode inválido '" + raw + "'. Usando CHAINED.");
            return StackSpawnMode.CHAINED;
        }
    }

    private StackSpawnOrder parseStackSpawnOrder(String raw) {
        if (raw == null || raw.isBlank()) {
            return StackSpawnOrder.RANDOM_CYCLE;
        }
        try {
            return StackSpawnOrder.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("stack-spawn.order inválido '" + raw + "'. Usando RANDOM_CYCLE.");
            return StackSpawnOrder.RANDOM_CYCLE;
        }
    }

    private Material parseMaterial(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return null;
        }
        Material material = Material.matchMaterial(materialName.trim());
        if (material != null) {
            return material;
        }
        try {
            return Material.valueOf(materialName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void parseRarityConfig() {
        rarityDefinitions.clear();
        mobRarityMap.clear();

        ConfigurationSection raritySettings = config.getConfigurationSection("rarity-settings");
        if (raritySettings != null) {
            for (String rarityId : raritySettings.getKeys(false)) {
                String path = "rarity-settings." + rarityId;
                String displayName = config.getString(path + ".display-name", rarityId);
                String color = config.getString(path + ".color", "&f");
                int tier = config.getInt(path + ".tier", 0);
                rarityDefinitions.put(rarityId.toLowerCase(Locale.ROOT), new RarityDefinition(displayName, color, tier));
            }
        }

        ConfigurationSection mobRarities = config.getConfigurationSection("mob-rarities");
        if (mobRarities != null) {
            for (String entity : mobRarities.getKeys(false)) {
                String rarityId = mobRarities.getString(entity);
                if (rarityId == null || rarityId.isBlank()) {
                    continue;
                }
                mobRarityMap.put(entity.toUpperCase(Locale.ROOT), rarityId.toLowerCase(Locale.ROOT));
            }
        }
    }

    /**
     * Obtém o locale configurado
     * @return String do locale (ex: pt_BR)
     */
    public String getLocale() {
        String locale = config.getString("locale", "pt_BR");
        return locale.replace("-", "_");
    }

    /**
     * Obtém a chave de licença salva.
     * Usa fallback para license.code em arquivos legados.
     */
    public String getLicenseKey() {
        String key = config.getString("license.key", "");
        if (key == null || key.isBlank()) {
            key = config.getString("license.code", "");
        }
        return key == null ? "" : key.trim();
    }

    /**
     * Persiste a chave de licença no config.yml.
     */
    public void setLicenseKey(String key) {
        String normalized = key == null ? "" : key.trim();
        config.set("license.key", normalized);
        config.set("license.code", null);
        plugin.saveConfig();
    }

    /**
     * Timeout da requisição de validação da licença em milissegundos.
     */
    public int getLicenseRequestTimeoutMs() {
        return INTERNAL_LICENSE_REQUEST_TIMEOUT_MS;
    }

    /**
     * Obtém a lista de ferramentas válidas para quebrar spawners
     * @return Lista de Materials
     */
    public List<Material> getValidTools() {
        List<String> toolNames = config.getStringList("break.tools");
        if (toolNames.isEmpty()) {
            List<Material> defaultTools = new ArrayList<>();
            defaultTools.add(Material.DIAMOND_PICKAXE);
            return defaultTools;
        }
        return toolNames.stream().map(name -> {
            try {
                return Material.valueOf(name);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }).filter(java.util.Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * Verifica se Toque Suave é necessário para quebrar spawners
     * @return true se necessário
     */
    public boolean requiresSilkTouch() {
        return config.getBoolean("break.silk-touch", true);
    }

    /**
     * Verifica se o item deve ir direto para o inventário ao quebrar
     * @return true se deve ir para o inventário
     */
    public boolean isBreakDropToInventory() {
        return config.getBoolean("break.drop-to-inventory", true);
    }

    /**
     * Obtém a chance de drop ao quebrar
     * @return Porcentagem de chance (0-100)
     */
    public double getBreakDropChance() {
        return config.getDouble("break.drop-chance", 50.0);
    }

    /**
     * Verifica se spawners podem dropar ao explodir
     * @return true se permitido
     */
    public boolean isExplosionDropAllowed() {
        return config.getBoolean("explosion.allow-drop", true);
    }

    /**
     * Verifica se o item deve ir direto para o inventário na explosão
     * @return true se deve ir para o inventário
     */
    public boolean isExplosionDropToInventory() {
        return config.getBoolean("explosion.drop-to-inventory", true);
    }

    /**
     * Obtém a chance de drop em explosão
     * @return Porcentagem de chance (0-100)
     */
    public double getExplosionDropChance() {
        return config.getDouble("explosion.drop-chance", 50.0);
    }

    /**
     * Verifica se permite quebrar sem requisitos
     * @return true se permitido
     */
    public boolean isAllowBreakWithoutRequirements() {
        return config.getBoolean("break.allow-break-without-requirements", true);
    }

    /**
     * Verifica se o empilhamento está ativado
     * @return true se ativado
     */
    public boolean isStackingEnabled() {
        return config.getBoolean("spawner.stacking-enabled", true);
    }

    /**
     * Obtém o tamanho máximo do stack
     * @return Tamanho máximo
     */
    public int getMaxStackSize() {
        return config.getInt("spawner.max-stack-size", 64);
    }

    /**
     * Obtém o formato do nome de exibição do spawner
     * @return String do formato
     */
    public String getSpawnerDisplayName() {
        return config.getString("spawner.display-name", "&8[{rarity_color}&l{rarity}&8] &e{type} Spawner");
    }

    /**
     * Obtém a lore do spawner
     * @return Lista de strings da lore
     */
    public java.util.List<String> getSpawnerLore() {
        return config.getStringList("spawner.lore");
    }

    /**
     * Distância para considerar spawner ativo (player próximo)
     * @return Distância em blocos
     */
    public int getSpawnerActiveDistance() {
        return config.getInt("spawner.distance-active", 64);
    }

    /**
     * Valor base de spawn-count para spawners nível 0.
     */
    public int getSpawnerBaseSpawnCount() {
        return Math.max(1, config.getInt("spawner.base-stats.spawn-count", 6));
    }

    /**
     * Valor base de max-nearby-entities para spawners nível 0.
     */
    public int getSpawnerBaseMaxNearbyEntities() {
        return Math.max(1, config.getInt("spawner.base-stats.max-nearby-entities", 12));
    }

    /**
     * Delay mínimo base de spawn para spawners nível 0.
     */
    public int getSpawnerBaseMinSpawnDelay() {
        return Math.max(1, config.getInt("spawner.base-stats.min-spawn-delay", 140));
    }

    /**
     * Delay máximo base de spawn para spawners nível 0.
     */
    public int getSpawnerBaseMaxSpawnDelay() {
        int minDelay = getSpawnerBaseMinSpawnDelay();
        int configured = config.getInt("spawner.base-stats.max-spawn-delay", 500);
        return Math.max(minDelay, configured);
    }

    public StackSpawnMode getStackSpawnMode() {
        return stackSpawnMode;
    }

    public StackSpawnOrder getStackSpawnOrder() {
        return stackSpawnOrder;
    }

    public int getStackChainMinDelayTicks() {
        return stackChainMinDelayTicks;
    }

    public boolean isStackSpawnChained() {
        return stackSpawnMode == StackSpawnMode.CHAINED;
    }

    public boolean isUpgradeActive() {
        return upgradeActive && !upgradeLevels.isEmpty();
    }

    public boolean isUpgradePermissionRequired() {
        return upgradeRequirePermission;
    }

    public String getUpgradePermissionNode() {
        return upgradePermissionNode;
    }

    public BoostType getUpgradeBoostType() {
        return upgradeBoostType;
    }

    public int getUpgradeMaxLevel() {
        return upgradeMaxLevel;
    }

    public UpgradeLevel getUpgradeLevel(int level) {
        return upgradeLevels.get(level);
    }

    public UpgradeLevel getNextUpgradeLevel(int currentLevel) {
        return upgradeLevels.get(currentLevel + 1);
    }

    public int getLevelMaxStackOrDefault(int level) {
        UpgradeLevel upgradeLevel = upgradeLevels.get(level);
        if (upgradeLevel != null && upgradeLevel.maxStackSize() > 0) {
            return upgradeLevel.maxStackSize();
        }
        return getMaxStackSize();
    }

    public String getUpgradeLevelNameSuffix(int level) {
        UpgradeLevel upgradeLevel = upgradeLevels.get(level);
        return upgradeLevel != null ? upgradeLevel.nameSuffix() : "";
    }

    public String getUpgradeLevelLoreLine(int level) {
        UpgradeLevel upgradeLevel = upgradeLevels.get(level);
        return upgradeLevel != null ? upgradeLevel.loreLine() : "";
    }

    public Set<Material> getUpgradeMaterials() {
        return Collections.unmodifiableSet(upgradeMaterials);
    }

    public boolean isUpgradeMaterial(Material material) {
        return material != null && upgradeMaterials.contains(material);
    }

    /**
     * Verifica se o shop de spawners está ativado
     */
    public boolean isShopEnabled() {
        return shopConfig != null && shopConfig.getBoolean("spawner.shop.enabled", false);
    }

    /**
     * Obtém a moeda do shop
     */
    public String getShopCurrency() {
        return shopConfig != null ? shopConfig.getString("spawner.shop.currency", "XP") : "XP";
    }

    /**
     * Obtém o material primário do filler do shop
     */
    public String getShopFillerPrimary() {
        if (shopConfig == null) return "BLUE_STAINED_GLASS_PANE";
        return shopConfig.getString("spawner.shop.navigation.filler-primary",
            shopConfig.getString("spawner.shop.navigation.filler", "BLUE_STAINED_GLASS_PANE"));
    }

    /**
     * Obtém o material secundário do filler do shop
     */
    public String getShopFillerSecondary() {
        if (shopConfig == null) return "LIGHT_BLUE_STAINED_GLASS_PANE";
        return shopConfig.getString("spawner.shop.navigation.filler-secondary",
            shopConfig.getString("spawner.shop.navigation.filler", "LIGHT_BLUE_STAINED_GLASS_PANE"));
    }

    /**
     * Obtém o material de destaque do filler do shop
     */
    public String getShopFillerAccent() {
        return shopConfig != null ? shopConfig.getString("spawner.shop.navigation.filler-accent", "SEA_LANTERN")
            : "SEA_LANTERN";
    }

    /**
     * Obtém o item do topo do shop
     */
    public String getShopTitleItem() {
        return shopConfig != null ? shopConfig.getString("spawner.shop.navigation.title-item", "SPAWNER") : "SPAWNER";
    }

    /**
     * Obtém uma cabeça de navegação do shop
     */
    public String getShopNavHead(String key) {
        return shopConfig != null ? shopConfig.getString("spawner.shop.navigation." + key, "") : "";
    }

    /**
     * Verifica se vendas públicas estão ativas
     */
    public boolean isShopPublicSalesEnabled() {
        return shopConfig == null || shopConfig.getBoolean("spawner.shop.public-sales.enabled", true);
    }

    /**
     * Limite de anúncios ativos por jogador
     */
    public int getShopPublicSalesMaxActivePerPlayer() {
        if (shopConfig == null) {
            return 5;
        }
        return Math.max(1, shopConfig.getInt("spawner.shop.public-sales.max-active-per-player", 5));
    }

    /**
     * Janela de expiração dos anúncios em horas
     */
    public int getShopPublicSalesExpirationHours() {
        if (shopConfig == null) {
            return 48;
        }
        return Math.max(1, shopConfig.getInt("spawner.shop.public-sales.expiration-hours", 48));
    }

    /**
     * Intervalo da limpeza automática de anúncios expirados
     */
    public int getShopPublicSalesAutoCleanupMinutes() {
        if (shopConfig == null) {
            return 5;
        }
        return Math.max(1, shopConfig.getInt("spawner.shop.public-sales.auto-cleanup-minutes", 5));
    }

    /**
     * Slot do botão de vendas públicas na tela principal da loja
     */
    public int getShopPublicSalesMenuSlot() {
        if (shopConfig == null) {
            return 40;
        }
        int slot = shopConfig.getInt("spawner.shop.public-sales.menu-slot", 40);
        if (slot < 0 || slot > 53) {
            return 40;
        }
        return slot;
    }

    /**
     * Obtém a seção de categorias do shop
     */
    public org.bukkit.configuration.ConfigurationSection getShopCategoriesSection() {
        return shopConfig != null ? shopConfig.getConfigurationSection("spawner.shop.categories") : null;
    }

    /**
     * Verifica se o sistema de trade esta ativo.
     */
    public boolean isTradeEnabled() {
        return config.getBoolean("trade.enabled", true);
    }

    /**
     * Timeout para convite de trade em segundos.
     */
    public int getTradeInviteTimeoutSeconds() {
        return Math.max(5, config.getInt("trade.invite-timeout-seconds", 30));
    }

    /**
     * Timeout para sessao de trade em segundos.
     */
    public int getTradeSessionTimeoutSeconds() {
        return Math.max(10, config.getInt("trade.session-timeout-seconds", 120));
    }

    /**
     * Duracao da contagem antes da troca.
     */
    public int getTradeCountdownSeconds() {
        return Math.max(1, config.getInt("trade.countdown-seconds", 3));
    }

    /**
     * Habilita logs de trade no console.
     */
    public boolean isTradeLoggingEnabled() {
        return config.getBoolean("trade.logging.enabled", true);
    }

    /**
     * Material de fundo da GUI de trade.
     */
    public String getTradeGuiFillerMaterial() {
        return config.getString("trade.gui.filler-material", "GRAY_STAINED_GLASS_PANE");
    }

    /**
     * Material da divisoria da GUI de trade.
     */
    public String getTradeGuiDividerMaterial() {
        return config.getString("trade.gui.divider-material", "BLACK_STAINED_GLASS_PANE");
    }

    /**
     * Material base do tema da GUI de trade.
     */
    public String getTradeGuiThemeBaseFillerMaterial() {
        return config.getString(
            "trade.gui.theme.base-filler-material",
            config.getString("trade.gui.filler-material", "LIGHT_BLUE_STAINED_GLASS_PANE")
        );
    }

    /**
     * Material de moldura do tema da GUI de trade.
     */
    public String getTradeGuiThemeFrameFillerMaterial() {
        return config.getString("trade.gui.theme.frame-filler-material", "CYAN_STAINED_GLASS_PANE");
    }

    /**
     * Material de divisoria do tema da GUI de trade.
     */
    public String getTradeGuiThemeDividerMaterial() {
        return config.getString(
            "trade.gui.theme.divider-material",
            config.getString("trade.gui.divider-material", "BLUE_STAINED_GLASS_PANE")
        );
    }

    /**
     * Material de fundo quando ambos confirmam.
     */
    public String getTradeGuiThemeConfirmedFillerMaterial() {
        return config.getString("trade.gui.theme.confirmed-filler-material", "LIME_STAINED_GLASS_PANE");
    }

    /**
     * Material da divisoria quando ambos confirmam.
     */
    public String getTradeGuiThemeConfirmedDividerMaterial() {
        return config.getString("trade.gui.theme.confirmed-divider-material", "GREEN_STAINED_GLASS_PANE");
    }

    /**
     * Exibe cabecas de jogadores na GUI de trade.
     */
    public boolean isTradeGuiShowPlayerHeads() {
        return config.getBoolean("trade.gui.widgets.show-player-heads", true);
    }

    /**
     * Exibe indicador de distancia na GUI de trade.
     */
    public boolean isTradeGuiShowDistanceIndicator() {
        return config.getBoolean("trade.gui.widgets.show-distance-indicator", true);
    }

    /**
     * Exibe indicador de countdown na GUI de trade.
     */
    public boolean isTradeGuiShowCountdownIndicator() {
        return config.getBoolean("trade.gui.widgets.show-countdown-indicator", true);
    }

    /**
     * Habilita efeitos sonoros e visuais da trade.
     */
    public boolean isTradeEffectsEnabled() {
        return config.getBoolean("trade.effects.enabled", true);
    }

    /**
     * Volume base para efeitos sonoros de trade.
     */
    public float getTradeEffectsSoundVolume() {
        double raw = config.getDouble("trade.effects.sound-volume", 1.0D);
        return (float) Math.max(0.0D, Math.min(2.0D, raw));
    }

    /**
     * Densidade base para particulas de trade.
     */
    public double getTradeEffectsParticleDensity() {
        double raw = config.getDouble("trade.effects.particle-density", 1.0D);
        return Math.max(0.0D, Math.min(3.0D, raw));
    }

    /**
     * Habilita verificacao obrigatoria de distancia para trade.
     */
    public boolean isTradeDistanceCheckEnabled() {
        return config.getBoolean("trade.distance-check.enabled", true);
    }

    /**
     * Distancia maxima entre os jogadores para trade.
     */
    public int getTradeMaxDistanceBlocks() {
        return Math.max(1, config.getInt("trade.distance-check.max-distance-blocks", 8));
    }

    /**
     * Obtém a raridade de um tipo de entidade
     * @param entityType Tipo da entidade
     * @return String formatada da raridade
     */
    public String getRarity(String entityType) {
        String normalized = entityType.toUpperCase(Locale.ROOT);

        if (!rarityDefinitions.isEmpty() && !mobRarityMap.isEmpty()) {
            String rarityId = mobRarityMap.getOrDefault(normalized, mobRarityMap.get("DEFAULT"));
            if (rarityId != null) {
                RarityDefinition definition = rarityDefinitions.get(rarityId.toLowerCase(Locale.ROOT));
                if (definition != null) {
                    return definition.color() + definition.displayName();
                }
            }
        }

        return config.getString("rarities." + normalized, config.getString("rarities.DEFAULT", "&fComum"));
    }

    /**
     * Obtém a configuração bruta
     * @return FileConfiguration
     */
    public FileConfiguration getConfig() {
        return config;
    }
}

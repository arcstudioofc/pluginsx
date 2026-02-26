package com.spawnerx;

import com.spawnerx.commands.SpawnerXCommand;
import com.spawnerx.listeners.*;
import com.spawnerx.managers.ConfigManager;
import com.spawnerx.managers.LicenseManager;
import com.spawnerx.managers.LocaleManager;
import com.spawnerx.managers.AdminSpawnerMenuManager;
import com.spawnerx.managers.PublicSalesManager;
import com.spawnerx.managers.SpawnerManager;
import com.spawnerx.managers.SpawnerShopManager;
import com.spawnerx.managers.TradeManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Classe principal do plugin SpawnerX
 * Gerencia o ciclo de vida do plugin e inicialização de componentes
 */
public class SpawnerX extends JavaPlugin {
    
    private static SpawnerX instance;
    private ConfigManager configManager;
    private LocaleManager localeManager;
    private SpawnerManager spawnerManager;
    private SpawnerShopManager spawnerShopManager;
    private AdminSpawnerMenuManager adminSpawnerMenuManager;
    private PublicSalesManager publicSalesManager;
    private TradeManager tradeManager;
    private LicenseManager licenseManager;
    
    @Override
    public void onEnable() {
        instance = this;

        this.configManager = new ConfigManager(this);
        this.localeManager = new LocaleManager(this);
        this.spawnerManager = new SpawnerManager(this);
        this.publicSalesManager = new PublicSalesManager(this);
        this.spawnerShopManager = new SpawnerShopManager(this);
        this.adminSpawnerMenuManager = new AdminSpawnerMenuManager(this);

        configManager.loadConfig();
        localeManager.loadLocale();
        this.tradeManager = new TradeManager(this);
        this.licenseManager = new LicenseManager(this);
        publicSalesManager.reloadSettings();

        SpawnerXCommand commandExecutor = new SpawnerXCommand(this);
        getCommand("spawnerx").setExecutor(commandExecutor);
        getCommand("spawnerx").setTabCompleter(commandExecutor);

        getServer().getPluginManager().registerEvents(new SpawnerBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnerUpgradeListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnerExplosionListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnerPlaceListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnerInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnerStackListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnerDistanceListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnerShopListener(this), this);
        getServer().getPluginManager().registerEvents(new AdminSpawnerMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerPayoutListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnerItemSanitizerListener(this), this);
        getServer().getPluginManager().registerEvents(new LicenseAdminFeedbackListener(this), this);
        getServer().getPluginManager().registerEvents(new TradeListener(this), this);

        getLogger().info(" ");
        getLogger().info("   _____                                   __   __");
        getLogger().info("  / ____|                                  \\ \\ / /");
        getLogger().info(" | (___  _ __   __ ___      ___ __   ___ _ _\\ V / ");
        getLogger().info("  \\___ \\| '_ \\ / _` \\ \\ /\\ / / '_ \\ / _ \\ '__> <  ");
        getLogger().info("  ____) | |_) | (_| |\\ V  V /| | | |  __/ | / . \\ ");
        getLogger().info(" |_____/| .__/ \\__,_| \\_/\\_/ |_| |_|\\___|_|/_/ \\_\\");
        getLogger().info("        | |                                       ");
        getLogger().info("        |_|                                       ");
        getLogger().info(" ");
        getLogger().info(" SpawnerX v" + getDescription().getVersion());

        licenseManager.validateSavedLicense();
    }

    
    @Override
    public void onDisable() {
        if (publicSalesManager != null) {
            publicSalesManager.shutdown();
        }
        if (tradeManager != null) {
            tradeManager.shutdown();
        }
        getLogger().info("SpawnerX desabilitado!");
    }
    
    /**
     * Obtém a instância do plugin
     * @return Instância do SpawnerX
     */
    public static SpawnerX getInstance() {
        return instance;
    }
    
    /**
     * Obtém o gerenciador de configuração
     * @return ConfigManager
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    /**
     * Obtém o gerenciador de locale
     * @return LocaleManager
     */
    public LocaleManager getLocaleManager() {
        return localeManager;
    }
    
    /**
     * Obtém o gerenciador de spawners
     * @return SpawnerManager
     */
    public SpawnerManager getSpawnerManager() {
        return spawnerManager;
    }

    /**
     * Obtém o gerenciador do shop de spawners
     */
    public SpawnerShopManager getSpawnerShopManager() {
        return spawnerShopManager;
    }

    /**
     * Obtém o gerenciador do menu administrativo de spawners.
     */
    public AdminSpawnerMenuManager getAdminSpawnerMenuManager() {
        return adminSpawnerMenuManager;
    }

    /**
     * Obtém o gerenciador de vendas públicas
     */
    public PublicSalesManager getPublicSalesManager() {
        return publicSalesManager;
    }

    /**
     * Obtém o gerenciador de trades entre jogadores.
     */
    public TradeManager getTradeManager() {
        return tradeManager;
    }

    /**
     * Obtém o gerenciador de licença.
     */
    public LicenseManager getLicenseManager() {
        return licenseManager;
    }

    /**
     * Verifica se a licença está validada.
     */
    public boolean isLicenseValid() {
        return licenseManager != null && licenseManager.isValid();
    }

    /**
     * Verifica se o plugin está em modo bloqueado por licença.
     */
    public boolean isLicenseLocked() {
        return !isLicenseValid();
    }

    /**
     * Sincroniza serviços dependentes da licença.
     */
    public void onLicenseStatusChanged(LicenseManager.LicenseStatus status, String reason) {
        if (publicSalesManager == null) {
            return;
        }

        if (status == LicenseManager.LicenseStatus.VALID) {
            publicSalesManager.reloadSettings();
        } else {
            publicSalesManager.stopCleanupTask();
        }
    }
    
    /**
     * Recarrega todas as configurações do plugin
     */
    public void reload() {
        configManager.loadConfig();
        localeManager.loadLocale();
        if (licenseManager != null) {
            licenseManager.validateSavedLicense();
            if (tradeManager != null) {
                tradeManager.reloadSettings();
            }
            return;
        }
        publicSalesManager.reloadSettings();
        if (tradeManager != null) {
            tradeManager.reloadSettings();
        }
    }
}

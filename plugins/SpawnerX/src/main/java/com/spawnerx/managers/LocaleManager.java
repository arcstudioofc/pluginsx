package com.spawnerx.managers;

import com.spawnerx.SpawnerX;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Gerenciador de mensagens localizadas
 * Responsável por carregar e fornecer mensagens traduzidas
 */
public class LocaleManager {
    
    private final SpawnerX plugin;
    private FileConfiguration localeConfig;
    private FileConfiguration fallbackConfig;
    
    public LocaleManager(SpawnerX plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Carrega o arquivo de locale baseado na configuração
     */
    public void loadLocale() {
        saveAllLocaleResources();
        // Carregar fallback (en_US)
        File fallbackFile = new File(plugin.getDataFolder(), "locale/en_US.yml");
        if (!fallbackFile.exists()) {
            plugin.saveResource("locale/en_US.yml", false);
        }
        this.fallbackConfig = YamlConfiguration.loadConfiguration(fallbackFile);

        String locale = plugin.getConfigManager().getLocale();
        File localeFile = new File(plugin.getDataFolder(), "locale/" + locale + ".yml");
        
        // Criar diretório se não existir
        if (!localeFile.getParentFile().exists()) {
            localeFile.getParentFile().mkdirs();
        }
        
        // Salvar arquivo se não existir
        if (!localeFile.exists()) {
            try {
                plugin.saveResource("locale/" + locale + ".yml", false);
            } catch (Exception ignored) {}
        }
        
        // Carregar configuração principal
        if (localeFile.exists()) {
            this.localeConfig = YamlConfiguration.loadConfiguration(localeFile);
        } else {
            this.localeConfig = fallbackConfig;
        }
    }

    private void saveAllLocaleResources() {
        try {
            File pluginFile = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            JarFile jar = new JarFile(pluginFile);
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("locale/") && name.endsWith(".yml")) {
                    File outFile = new File(plugin.getDataFolder(), name);
                    if (!outFile.exists()) {
                        plugin.saveResource(name, false);
                    }
                }
            }
            jar.close();
        } catch (Exception e) {
            plugin.getLogger().warning("Falha ao copiar locales do JAR: " + e.getMessage());
        }
    }
    
    /**
     * Obtém uma mensagem traduzida
     * @param path Caminho da mensagem no arquivo de locale
     * @return Mensagem formatada com cores
     */
    public String getMessage(String path) {
        String message = localeConfig.getString(path);
        if (message == null) {
            message = fallbackConfig.getString(path, path);
        }
        
        // Substituir prefix se presente (exceto se o próprio path for prefix)
        if (!path.equals("prefix")) {
            String prefix = getRawMessage("prefix");
            message = message.replace("{prefix}", prefix);
        }
        
        return colorize(message);
    }

    /**
     * Obtém a mensagem sem colorir ou processar prefixo
     */
    private String getRawMessage(String path) {
        String message = localeConfig.getString(path);
        if (message == null) {
            message = fallbackConfig.getString(path, path);
        }
        return message;
    }
    
    /**
     * Obtém uma mensagem traduzida com substituições
     * @param path Caminho da mensagem
     * @param replacements Array de pares [placeholder, valor]
     * @return Mensagem formatada
     */
    public String getMessage(String path, String... replacements) {
        String message = getRawMessage(path);
        
        // Substituir prefix se presente
        if (!path.equals("prefix")) {
            String prefix = getRawMessage("prefix");
            message = message.replace("{prefix}", prefix);
        }
        
        // Substituir placeholders customizados
        for (int i = 0; i < replacements.length - 1; i += 2) {
            message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        
        return colorize(message);
    }
    
    /**
     * Converte códigos de cor para ChatColor
     * @param text Texto com códigos &
     * @return Texto colorido
     */
    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
    
    /**
     * Obtém a configuração de locale
     * @return FileConfiguration
     */
    public FileConfiguration getLocaleConfig() {
        return localeConfig;
    }
}

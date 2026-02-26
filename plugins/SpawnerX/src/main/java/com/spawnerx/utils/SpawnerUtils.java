package com.spawnerx.utils;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Classe utilitária para operações com spawners
 */
public class SpawnerUtils {
    
    private static final Map<EntityType, String> ENTITY_NAMES = new HashMap<>();
    
    static {
        // Mapeamento de nomes amigáveis para entidades
        ENTITY_NAMES.put(EntityType.PIG, "Porco");
        ENTITY_NAMES.put(EntityType.COW, "Vaca");
        ENTITY_NAMES.put(EntityType.SHEEP, "Ovelha");
        ENTITY_NAMES.put(EntityType.CHICKEN, "Galinha");
        ENTITY_NAMES.put(EntityType.ZOMBIE, "Zumbi");
        ENTITY_NAMES.put(EntityType.SKELETON, "Esqueleto");
        ENTITY_NAMES.put(EntityType.SPIDER, "Aranha");
        ENTITY_NAMES.put(EntityType.CREEPER, "Creeper");
        ENTITY_NAMES.put(EntityType.ENDERMAN, "Enderman");
        ENTITY_NAMES.put(EntityType.BLAZE, "Blaze");
        ENTITY_NAMES.put(EntityType.IRON_GOLEM, "Golem de Ferro");
        ENTITY_NAMES.put(EntityType.WITHER_SKELETON, "Esqueleto Wither");
        ENTITY_NAMES.put(EntityType.CAVE_SPIDER, "Aranha da Caverna");
        ENTITY_NAMES.put(EntityType.SILVERFISH, "Traça");
        ENTITY_NAMES.put(EntityType.SLIME, "Slime");
        ENTITY_NAMES.put(EntityType.MAGMA_CUBE, "Cubo de Magma");
        ENTITY_NAMES.put(EntityType.GHAST, "Ghast");
        ENTITY_NAMES.put(EntityType.WITCH, "Bruxa");
        ENTITY_NAMES.put(EntityType.GUARDIAN, "Guardião");
        ENTITY_NAMES.put(EntityType.ELDER_GUARDIAN, "Guardião Ancião");
        ENTITY_NAMES.put(EntityType.SHULKER, "Shulker");
        ENTITY_NAMES.put(EntityType.PHANTOM, "Phantom");
        ENTITY_NAMES.put(EntityType.DROWNED, "Afogado");
        ENTITY_NAMES.put(EntityType.HUSK, "Husk");
        ENTITY_NAMES.put(EntityType.STRAY, "Stray");
        ENTITY_NAMES.put(EntityType.VEX, "Vex");
        ENTITY_NAMES.put(EntityType.VINDICATOR, "Vindicador");
        ENTITY_NAMES.put(EntityType.EVOKER, "Evocador");
        ENTITY_NAMES.put(EntityType.PILLAGER, "Saqueador");
        ENTITY_NAMES.put(EntityType.RAVAGER, "Devastador");
        ENTITY_NAMES.put(EntityType.PIGLIN, "Piglin");
        ENTITY_NAMES.put(EntityType.PIGLIN_BRUTE, "Piglin Bruto");
        ENTITY_NAMES.put(EntityType.HOGLIN, "Hoglin");
        ENTITY_NAMES.put(EntityType.ZOGLIN, "Zoglin");
        ENTITY_NAMES.put(EntityType.ZOMBIFIED_PIGLIN, "Piglin Zumbi");
        ENTITY_NAMES.put(EntityType.WARDEN, "Warden");
    }
    
    /**
     * Obtém o nome de exibição amigável de uma entidade
     * @param entityType Tipo da entidade
     * @return Nome formatado
     */
    public static String getEntityDisplayName(EntityType entityType) {
        return ENTITY_NAMES.getOrDefault(entityType, formatEntityName(entityType.name()));
    }
    
    /**
     * Formata o nome de uma entidade (fallback)
     * @param name Nome da entidade
     * @return Nome formatado
     */
    private static String formatEntityName(String name) {
        String[] words = name.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        
        for (String word : words) {
            if (result.length() > 0) {
                result.append(" ");
            }
            result.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1));
        }
        
        return result.toString();
    }
    
    /**
     * Tenta obter um EntityType a partir de uma string
     * @param name Nome da entidade
     * @return EntityType ou null se inválido
     */
    public static EntityType getEntityType(String name) {
        try {
            return EntityType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Verifica se o EntityType é um mob válido para spawner
     */
    public static boolean isMobEntityType(EntityType type) {
        if (type == null || !type.isSpawnable()) return false;
        
        // Verifica se a classe da entidade herda de LivingEntity
        Class<?> entityClass = type.getEntityClass();
        return entityClass != null && LivingEntity.class.isAssignableFrom(entityClass);
    }
}

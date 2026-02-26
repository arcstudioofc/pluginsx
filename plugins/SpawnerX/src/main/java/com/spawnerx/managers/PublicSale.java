package com.spawnerx.managers;

import org.bukkit.entity.EntityType;

import java.util.UUID;

/**
 * Representa um anuncio de venda publica de spawner.
 */
public record PublicSale(
    String id,
    UUID sellerUuid,
    String sellerName,
    EntityType entityType,
    int stackSize,
    int level,
    int itemAmount,
    int priceXp,
    long createdAt,
    long expiresAt
) {

    public boolean isExpired(long now) {
        return now >= expiresAt;
    }
}

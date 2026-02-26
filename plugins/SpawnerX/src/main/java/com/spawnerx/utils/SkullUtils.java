package com.spawnerx.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Utilitário para criar cabeças com textura base64.
 */
public final class SkullUtils {

    private SkullUtils() {}

    public static ItemStack createSkull(String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (base64 == null || base64.isBlank()) {
            return head;
        }
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return head;
        }

        applyBase64(meta, base64);
        head.setItemMeta(meta);
        return head;
    }

    private static void applyBase64(SkullMeta meta, String base64) {
        if (applyPaperProfile(meta, base64)) {
            return;
        }
        try {
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Object profile = gameProfileClass
                .getConstructor(UUID.class, String.class)
                .newInstance(UUID.randomUUID(), null);
            Method getProperties = gameProfileClass.getMethod("getProperties");
            Object properties = getProperties.invoke(profile);
            Object property = propertyClass
                .getConstructor(String.class, String.class)
                .newInstance("textures", base64);
            Method put = properties.getClass().getMethod("put", Object.class, Object.class);
            put.invoke(properties, "textures", property);

            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (Exception ignored) {
        }
    }

    private static boolean applyPaperProfile(SkullMeta meta, String base64) {
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), null);
            profile.setProperty(new ProfileProperty("textures", base64));

            try {
                meta.setPlayerProfile(profile);
                return true;
            } catch (NoSuchMethodError ignored) {
            } catch (Exception ignored) {
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}

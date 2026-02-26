package com.spawnerx.utils;

import org.bukkit.entity.Player;

/**
 * Utilitário para manipulação de XP total.
 */
public final class ExperienceUtils {

    private ExperienceUtils() {}

    public static int getTotalExperience(Player player) {
        int level = player.getLevel();
        float progress = player.getExp();
        int expToLevel = getTotalExpToLevel(level);
        int expToNext = getExpToNextLevel(level);
        return expToLevel + Math.round(progress * expToNext);
    }

    public static void setTotalExperience(Player player, int totalExp) {
        int clamped = Math.max(0, totalExp);
        int level = 0;
        while (getTotalExpToLevel(level + 1) <= clamped) {
            level++;
        }
        int expIntoLevel = clamped - getTotalExpToLevel(level);
        int expToNext = getExpToNextLevel(level);
        float progress = expToNext == 0 ? 0.0f : (float) expIntoLevel / (float) expToNext;

        player.setLevel(level);
        player.setExp(progress);
        player.setTotalExperience(clamped);
    }

    private static int getExpToNextLevel(int level) {
        if (level >= 32) {
            return 9 * level - 158;
        }
        if (level >= 17) {
            return 5 * level - 38;
        }
        return 2 * level + 7;
    }

    private static int getTotalExpToLevel(int level) {
        if (level >= 32) {
            return (int) (4.5 * level * level - 162.5 * level + 2220);
        }
        if (level >= 17) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        }
        return level * level + 6 * level;
    }
}

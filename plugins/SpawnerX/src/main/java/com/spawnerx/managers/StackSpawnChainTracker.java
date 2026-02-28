package com.spawnerx.managers;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rastreia o avanço da cadeia de spawn por posição de spawner.
 */
public class StackSpawnChainTracker {

    private final Map<SpawnerLocationKey, ChainState> states = new ConcurrentHashMap<>();

    public int peekNextIndex(Location location, int stackSize, ConfigManager.StackSpawnOrder order) {
        int safeStack = Math.max(1, stackSize);
        if (safeStack <= 1) {
            return 1;
        }

        SpawnerLocationKey key = toKey(location);
        if (key == null) {
            return 1;
        }

        ChainState state = states.computeIfAbsent(key, ignored -> new ChainState(safeStack));
        state.ensureStackSize(safeStack);

        ConfigManager.StackSpawnOrder safeOrder = normalizeOrder(order);
        return switch (safeOrder) {
            case SEQUENTIAL -> state.peekSequential();
            case RANDOM -> state.peekRandom();
            case RANDOM_CYCLE -> state.peekRandomCycle();
        };
    }

    public void advanceIfNeeded(Location location, int stackSize, ConfigManager.StackSpawnOrder order, long currentTick) {
        int safeStack = Math.max(1, stackSize);
        if (safeStack <= 1) {
            clear(location);
            return;
        }

        SpawnerLocationKey key = toKey(location);
        if (key == null) {
            return;
        }

        ChainState state = states.computeIfAbsent(key, ignored -> new ChainState(safeStack));
        state.ensureStackSize(safeStack);
        if (state.lastProcessedTick == currentTick) {
            return;
        }

        state.lastProcessedTick = currentTick;
        ConfigManager.StackSpawnOrder safeOrder = normalizeOrder(order);
        switch (safeOrder) {
            case SEQUENTIAL -> state.advanceSequential();
            case RANDOM -> state.advanceRandom();
            case RANDOM_CYCLE -> state.advanceRandomCycle();
        }
    }

    public void clear(Location location) {
        SpawnerLocationKey key = toKey(location);
        if (key == null) {
            return;
        }
        states.remove(key);
    }

    public void clearAll() {
        states.clear();
    }

    private ConfigManager.StackSpawnOrder normalizeOrder(ConfigManager.StackSpawnOrder order) {
        return order == null ? ConfigManager.StackSpawnOrder.RANDOM_CYCLE : order;
    }

    private SpawnerLocationKey toKey(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return new SpawnerLocationKey(
            location.getWorld().getUID(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        );
    }

    private int randomIndex(int max) {
        return ThreadLocalRandom.current().nextInt(Math.max(1, max)) + 1;
    }

    private final class ChainState {
        private int stackSize;
        private long lastProcessedTick = Long.MIN_VALUE;
        private int sequentialCursor = 1;
        private int randomNextIndex = 1;
        private final List<Integer> randomCycleOrder = new ArrayList<>();
        private int randomCycleCursor = 0;

        private ChainState(int stackSize) {
            resetForStack(Math.max(1, stackSize));
        }

        private void ensureStackSize(int stackSize) {
            int safeStack = Math.max(1, stackSize);
            if (this.stackSize != safeStack) {
                resetForStack(safeStack);
            }
        }

        private void resetForStack(int stackSize) {
            this.stackSize = stackSize;
            this.lastProcessedTick = Long.MIN_VALUE;
            this.sequentialCursor = 1;
            this.randomNextIndex = randomIndex(stackSize);
            reshuffleRandomCycle();
        }

        private int peekSequential() {
            sequentialCursor = normalizeIndex(sequentialCursor);
            return sequentialCursor;
        }

        private void advanceSequential() {
            int current = peekSequential();
            sequentialCursor = (current % stackSize) + 1;
        }

        private int peekRandom() {
            randomNextIndex = normalizeIndex(randomNextIndex);
            return randomNextIndex;
        }

        private void advanceRandom() {
            randomNextIndex = randomIndex(stackSize);
        }

        private int peekRandomCycle() {
            ensureRandomCycleOrder();
            return randomCycleOrder.get(randomCycleCursor);
        }

        private void advanceRandomCycle() {
            ensureRandomCycleOrder();
            randomCycleCursor++;
            if (randomCycleCursor >= randomCycleOrder.size()) {
                reshuffleRandomCycle();
            }
        }

        private void ensureRandomCycleOrder() {
            if (randomCycleOrder.isEmpty()) {
                reshuffleRandomCycle();
            }
            if (randomCycleCursor < 0 || randomCycleCursor >= randomCycleOrder.size()) {
                randomCycleCursor = 0;
            }
        }

        private void reshuffleRandomCycle() {
            randomCycleOrder.clear();
            for (int i = 1; i <= stackSize; i++) {
                randomCycleOrder.add(i);
            }
            Collections.shuffle(randomCycleOrder);
            randomCycleCursor = 0;
        }

        private int normalizeIndex(int value) {
            if (value < 1 || value > stackSize) {
                return 1;
            }
            return value;
        }
    }

    private record SpawnerLocationKey(UUID worldId, int x, int y, int z) {}
}

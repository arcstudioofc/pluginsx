package com.spawnerx.managers;

import com.spawnerx.SpawnerX;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Gerencia convites, sessoes e GUI de trade entre jogadores.
 */
public class TradeManager {

    private static final int GUI_SIZE = 54;
    private static final int SLOT_CONFIRM_LEFT = 38;
    private static final int SLOT_CONFIRM_RIGHT = 42;
    private static final int SLOT_EMBLEM = 4;
    private static final int SLOT_STATUS_LEFT = 36;
    private static final int SLOT_STATUS_RIGHT = 44;
    private static final int SLOT_COUNTDOWN = 49;
    private static final int SLOT_DISTANCE = 53;
    private static final int SLOT_LEFT_HEAD = 1;
    private static final int SLOT_RIGHT_HEAD = 7;

    private static final int[] LEFT_OFFER_SLOTS = {
        10, 11, 12,
        19, 20, 21,
        28, 29, 30
    };

    private static final int[] RIGHT_OFFER_SLOTS = {
        14, 15, 16,
        23, 24, 25,
        32, 33, 34
    };

    private static final int[] LEFT_HEADER_SLOTS = {0, 1, 2};
    private static final int[] RIGHT_HEADER_SLOTS = {6, 7, 8};
    private static final int[] DIVIDER_SLOTS = {13, 22, 31, 40};
    private static final int[] FRAME_SLOTS = {
        3, 5,
        9, 17, 18, 26, 27, 35,
        37, 39, 41, 43,
        45, 46, 47, 48, 50, 51, 52
    };
    private static final Set<Integer> LEFT_SLOT_SET = toSet(LEFT_OFFER_SLOTS);
    private static final Set<Integer> RIGHT_SLOT_SET = toSet(RIGHT_OFFER_SLOTS);

    private final SpawnerX plugin;
    private final Map<UUID, TradeInvite> invitesByTarget = new HashMap<>();
    private final Map<UUID, UUID> inviteTargetBySender = new HashMap<>();
    private final Map<UUID, TradeSession> sessionsByPlayer = new HashMap<>();
    private final Map<UUID, TradeSession> sessionsById = new HashMap<>();

    private boolean enabled;
    private int inviteTimeoutSeconds;
    private int sessionTimeoutSeconds;
    private int countdownSeconds;
    private boolean loggingEnabled;
    private Material themeBaseFillerMaterial;
    private Material themeFrameFillerMaterial;
    private Material themeDividerMaterial;
    private Material themeConfirmedFillerMaterial;
    private Material themeConfirmedDividerMaterial;
    private boolean showPlayerHeads;
    private boolean showDistanceIndicator;
    private boolean showCountdownIndicator;
    private boolean effectsEnabled;
    private float effectsSoundVolume;
    private double effectsParticleDensity;
    private boolean distanceCheckEnabled;
    private int maxDistanceBlocks;

    public TradeManager(SpawnerX plugin) {
        this.plugin = plugin;
        reloadSettings();
    }

    public void reloadSettings() {
        enabled = plugin.getConfigManager().isTradeEnabled();
        inviteTimeoutSeconds = plugin.getConfigManager().getTradeInviteTimeoutSeconds();
        sessionTimeoutSeconds = plugin.getConfigManager().getTradeSessionTimeoutSeconds();
        countdownSeconds = plugin.getConfigManager().getTradeCountdownSeconds();
        loggingEnabled = plugin.getConfigManager().isTradeLoggingEnabled();
        themeBaseFillerMaterial = parseMaterial(
            plugin.getConfigManager().getTradeGuiThemeBaseFillerMaterial(),
            Material.LIGHT_BLUE_STAINED_GLASS_PANE
        );
        themeFrameFillerMaterial = parseMaterial(
            plugin.getConfigManager().getTradeGuiThemeFrameFillerMaterial(),
            Material.CYAN_STAINED_GLASS_PANE
        );
        themeDividerMaterial = parseMaterial(
            plugin.getConfigManager().getTradeGuiThemeDividerMaterial(),
            Material.BLUE_STAINED_GLASS_PANE
        );
        themeConfirmedFillerMaterial = parseMaterial(
            plugin.getConfigManager().getTradeGuiThemeConfirmedFillerMaterial(),
            Material.LIME_STAINED_GLASS_PANE
        );
        themeConfirmedDividerMaterial = parseMaterial(
            plugin.getConfigManager().getTradeGuiThemeConfirmedDividerMaterial(),
            Material.GREEN_STAINED_GLASS_PANE
        );
        showPlayerHeads = plugin.getConfigManager().isTradeGuiShowPlayerHeads();
        showDistanceIndicator = plugin.getConfigManager().isTradeGuiShowDistanceIndicator();
        showCountdownIndicator = plugin.getConfigManager().isTradeGuiShowCountdownIndicator();
        effectsEnabled = plugin.getConfigManager().isTradeEffectsEnabled();
        effectsSoundVolume = plugin.getConfigManager().getTradeEffectsSoundVolume();
        effectsParticleDensity = plugin.getConfigManager().getTradeEffectsParticleDensity();
        distanceCheckEnabled = plugin.getConfigManager().isTradeDistanceCheckEnabled();
        maxDistanceBlocks = plugin.getConfigManager().getTradeMaxDistanceBlocks();

        if (!enabled) {
            List<TradeSession> active = new ArrayList<>(sessionsById.values());
            for (TradeSession session : active) {
                cancelSession(session, CancelReason.DISABLED, null, true);
            }
        }
    }

    public void shutdown() {
        for (TradeInvite invite : new ArrayList<>(invitesByTarget.values())) {
            removeInvite(invite, true);
        }

        List<TradeSession> active = new ArrayList<>(sessionsById.values());
        for (TradeSession session : active) {
            cancelSession(session, CancelReason.SHUTDOWN, null, false);
        }
    }

    public boolean isTradeInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof TradeHolder;
    }

    public InviteStatus sendInvite(Player sender, Player target) {
        if (plugin.isLicenseLocked() || !enabled) {
            return InviteStatus.DISABLED;
        }
        if (sender == null || target == null || !target.isOnline()) {
            return InviteStatus.TARGET_OFFLINE;
        }

        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        if (senderId.equals(targetId)) {
            return InviteStatus.SELF_TARGET;
        }
        if (isPlayerInSession(senderId)) {
            return InviteStatus.SENDER_BUSY;
        }
        if (isPlayerInSession(targetId)) {
            return InviteStatus.TARGET_BUSY;
        }
        if (hasPendingInvite(senderId)) {
            return InviteStatus.SENDER_BUSY;
        }
        if (hasPendingInvite(targetId)) {
            return InviteStatus.TARGET_BUSY;
        }
        if (distanceCheckEnabled && !arePlayersWithinTradeDistance(sender, target)) {
            return InviteStatus.TOO_FAR;
        }

        long expiresAt = System.currentTimeMillis() + (inviteTimeoutSeconds * 1000L);
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin,
            () -> expireInvite(targetId),
            Math.max(20L, inviteTimeoutSeconds * 20L));

        TradeInvite invite = new TradeInvite(senderId, targetId, expiresAt, timeoutTask);
        invitesByTarget.put(targetId, invite);
        inviteTargetBySender.put(senderId, targetId);

        sender.sendMessage(plugin.getLocaleManager().getMessage("trade.invite.sent", "player", target.getName()));
        target.sendMessage(plugin.getLocaleManager().getMessage("trade.invite.received", "player", sender.getName()));
        sendInviteButtons(target);

        if (loggingEnabled) {
            plugin.getLogger().info("[Trade] Invite " + sender.getName() + " -> " + target.getName());
        }

        return InviteStatus.SUCCESS;
    }

    public RespondStatus acceptInvite(Player target) {
        if (plugin.isLicenseLocked() || !enabled) {
            return RespondStatus.DISABLED;
        }

        TradeInvite invite = invitesByTarget.get(target.getUniqueId());
        if (invite == null) {
            return RespondStatus.NO_PENDING;
        }

        Player sender = Bukkit.getPlayer(invite.senderId());
        if (sender == null || !sender.isOnline()) {
            removeInvite(invite, true);
            return RespondStatus.SENDER_OFFLINE;
        }
        if (isPlayerInSession(target.getUniqueId()) || isPlayerInSession(sender.getUniqueId())) {
            removeInvite(invite, true);
            return RespondStatus.PLAYER_BUSY;
        }
        if (distanceCheckEnabled && !arePlayersWithinTradeDistance(sender, target)) {
            return RespondStatus.TOO_FAR;
        }

        removeInvite(invite, true);
        createSession(sender, target);
        return RespondStatus.SUCCESS;
    }

    public RespondStatus denyInvite(Player target) {
        if (plugin.isLicenseLocked() || !enabled) {
            return RespondStatus.DISABLED;
        }

        TradeInvite invite = invitesByTarget.get(target.getUniqueId());
        if (invite == null) {
            return RespondStatus.NO_PENDING;
        }

        Player sender = Bukkit.getPlayer(invite.senderId());
        removeInvite(invite, true);

        String senderName = resolvePlayerName(invite.senderId());
        target.sendMessage(plugin.getLocaleManager().getMessage("trade.invite.denied-target", "player", senderName));
        if (sender != null) {
            sender.sendMessage(plugin.getLocaleManager().getMessage("trade.invite.denied-sender", "player", target.getName()));
        }

        if (loggingEnabled) {
            plugin.getLogger().info("[Trade] Invite denied by " + target.getName() + " from " + senderName);
        }

        return RespondStatus.SUCCESS;
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        TradeSession session = getSessionByInventory(top);
        if (session == null) {
            event.setCancelled(true);
            return;
        }

        if (plugin.isLicenseLocked() || !enabled) {
            event.setCancelled(true);
            cancelSession(session, CancelReason.DISABLED, player, true);
            return;
        }

        Side side = resolveSide(session, player.getUniqueId());
        if (side == Side.NONE) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }

        boolean clickedTop = event.getClickedInventory().equals(top);
        if (clickedTop) {
            handleTopInventoryClick(event, session, side, player);
            return;
        }

        handleBottomInventoryClick(event, session, side, player);
    }

    public void handleInventoryDrag(InventoryDragEvent event) {
        TradeSession session = getSessionByInventory(event.getView().getTopInventory());
        if (session != null) {
            event.setCancelled(true);
        }
    }

    public void handleInventoryClose(InventoryCloseEvent event) {
        TradeSession session = getSessionByInventory(event.getInventory());
        if (session == null) {
            return;
        }

        if (session.closing) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        cancelSession(session, CancelReason.CLOSED, player, true);
    }

    public void handlePlayerQuit(Player player) {
        TradeSession session = sessionsByPlayer.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        cancelSession(session, CancelReason.PLAYER_LEFT, player, true);
    }

    private void handleTopInventoryClick(InventoryClickEvent event, TradeSession session, Side side, Player player) {
        int rawSlot = event.getRawSlot();
        event.setCancelled(true);

        if (rawSlot < 0 || rawSlot >= GUI_SIZE) {
            return;
        }

        int ownConfirm = side == Side.LEFT ? SLOT_CONFIRM_LEFT : SLOT_CONFIRM_RIGHT;
        int otherConfirm = side == Side.LEFT ? SLOT_CONFIRM_RIGHT : SLOT_CONFIRM_LEFT;

        if (rawSlot == ownConfirm) {
            toggleConfirm(session, side, player);
            return;
        }

        if (rawSlot == otherConfirm) {
            return;
        }

        if (!isOwnOfferSlot(side, rawSlot)) {
            return;
        }

        if (session.leftConfirmed || session.rightConfirmed) {
            cancelSession(session, CancelReason.ITEMS_CHANGED, player, true);
            return;
        }

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR
            || event.getAction() == InventoryAction.CLONE_STACK
            || event.getAction() == InventoryAction.UNKNOWN) {
            return;
        }

        if (isPlacementAction(event.getAction())) {
            ItemStack incoming = resolveIncomingTopItem(event);
            if (!isAir(incoming) && !isAllowedTradeItem(incoming)) {
                player.sendMessage(plugin.getLocaleManager().getMessage("trade.gui.only-spawner"));
                return;
            }
        }

        event.setCancelled(false);
    }

    private void handleBottomInventoryClick(InventoryClickEvent event, TradeSession session, Side side, Player player) {
        if (!event.isShiftClick()) {
            return;
        }

        event.setCancelled(true);

        ItemStack current = event.getCurrentItem();
        if (!isAllowedTradeItem(current)) {
            player.sendMessage(plugin.getLocaleManager().getMessage("trade.gui.only-spawner"));
            return;
        }

        if (session.leftConfirmed || session.rightConfirmed) {
            cancelSession(session, CancelReason.ITEMS_CHANGED, player, true);
            return;
        }

        int moved = moveItemToOwnOfferSlots(session.inventory, side, current.clone());
        if (moved <= 0) {
            return;
        }

        int remaining = current.getAmount() - moved;
        if (remaining <= 0) {
            event.getClickedInventory().setItem(event.getSlot(), new ItemStack(Material.AIR));
            return;
        }

        current.setAmount(remaining);
        event.getClickedInventory().setItem(event.getSlot(), current);
    }

    private int moveItemToOwnOfferSlots(Inventory inventory, Side side, ItemStack source) {
        if (source == null || source.getType() == Material.AIR || source.getAmount() <= 0) {
            return 0;
        }

        int remaining = source.getAmount();
        int[] slots = side == Side.LEFT ? LEFT_OFFER_SLOTS : RIGHT_OFFER_SLOTS;

        for (int slot : slots) {
            if (remaining <= 0) {
                break;
            }

            ItemStack existing = inventory.getItem(slot);
            if (existing == null || existing.getType() == Material.AIR) {
                ItemStack moved = source.clone();
                int toMove = Math.min(remaining, moved.getMaxStackSize());
                moved.setAmount(toMove);
                inventory.setItem(slot, moved);
                remaining -= toMove;
                continue;
            }

            if (!existing.isSimilar(source)) {
                continue;
            }

            int max = existing.getMaxStackSize();
            if (existing.getAmount() >= max) {
                continue;
            }

            int space = max - existing.getAmount();
            int toAdd = Math.min(space, remaining);
            existing.setAmount(existing.getAmount() + toAdd);
            remaining -= toAdd;
        }

        return source.getAmount() - remaining;
    }

    private void toggleConfirm(TradeSession session, Side side, Player actor) {
        if (distanceCheckEnabled && !arePlayersWithinTradeDistance(session)) {
            actor.sendMessage(plugin.getLocaleManager().getMessage(
                "trade.too-far",
                "distance", String.valueOf(maxDistanceBlocks)
            ));
            if (effectsEnabled) {
                playSound(actor, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            }
            return;
        }

        boolean unconfirmed = false;
        if (side == Side.LEFT) {
            if (session.leftConfirmed) {
                session.leftConfirmed = false;
                unconfirmed = true;
            } else {
                session.leftConfirmed = true;
            }
        } else {
            if (session.rightConfirmed) {
                session.rightConfirmed = false;
                unconfirmed = true;
            } else {
                session.rightConfirmed = true;
            }
        }

        if (unconfirmed) {
            if (session.countdownRunning) {
                stopCountdown(session);
            }
            refreshVisualState(session);
            if (effectsEnabled) {
                playSound(actor, Sound.UI_BUTTON_CLICK, 0.6f, 0.85f);
            }
            return;
        }

        refreshVisualState(session);
        playSingleConfirmEffect(actor);

        if (session.leftConfirmed && session.rightConfirmed) {
            playDoubleConfirmEffects(session);
            startCountdown(session);
        }
    }

    private void startCountdown(TradeSession session) {
        stopCountdown(session);

        session.leftSnapshot = snapshotSlots(session.inventory, LEFT_OFFER_SLOTS);
        session.rightSnapshot = snapshotSlots(session.inventory, RIGHT_OFFER_SLOTS);

        final int[] remaining = {Math.max(1, countdownSeconds)};
        session.countdownRunning = true;
        session.countdownRemaining = remaining[0];
        refreshVisualState(session);

        session.countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!sessionsById.containsKey(session.id)) {
                stopCountdown(session);
                return;
            }

            CancelReason invalidReason = validateSessionState(session);
            if (invalidReason != null) {
                stopCountdown(session);
                cancelSession(session, invalidReason, null, true);
                return;
            }

            if (remaining[0] <= 0) {
                stopCountdown(session);
                executeTrade(session);
                return;
            }

            session.countdownRemaining = remaining[0];
            refreshVisualState(session);
            notifyBoth(session, "trade.countdown", "seconds", String.valueOf(remaining[0]));
            playCountdownEffects(session, remaining[0]);
            remaining[0]--;
        }, 0L, 20L);
    }

    private void executeTrade(TradeSession session) {
        if (session.closing) {
            return;
        }

        CancelReason invalidReason = validateSessionState(session);
        if (invalidReason != null) {
            cancelSession(session, invalidReason, null, true);
            return;
        }

        session.closing = true;
        session.executed = true;

        stopTimeout(session);
        stopCountdown(session);
        stopVisualRefresh(session);
        removeSession(session);

        List<ItemStack> leftItems = extractItems(session.inventory, LEFT_OFFER_SLOTS);
        List<ItemStack> rightItems = extractItems(session.inventory, RIGHT_OFFER_SLOTS);
        clearOfferSlots(session.inventory);

        Player leftPlayer = Bukkit.getPlayer(session.leftId);
        Player rightPlayer = Bukkit.getPlayer(session.rightId);

        giveItems(rightPlayer, leftItems);
        giveItems(leftPlayer, rightItems);

        closeSessionInventory(session);

        if (leftPlayer != null) {
            leftPlayer.sendMessage(plugin.getLocaleManager().getMessage("trade.success", "player", session.rightName));
        }
        if (rightPlayer != null) {
            rightPlayer.sendMessage(plugin.getLocaleManager().getMessage("trade.success", "player", session.leftName));
        }
        playSuccessEffects(session);

        if (loggingEnabled) {
            plugin.getLogger().info("[Trade] Completed " + session.leftName + " <-> " + session.rightName
                + " | left-items=" + summarizeItems(leftItems)
                + " | right-items=" + summarizeItems(rightItems));
        }
    }

    private CancelReason validateSessionState(TradeSession session) {
        if (!enabled || plugin.isLicenseLocked()) {
            return CancelReason.DISABLED;
        }
        if (!session.leftConfirmed || !session.rightConfirmed) {
            return CancelReason.UNCONFIRMED;
        }

        Player left = Bukkit.getPlayer(session.leftId);
        Player right = Bukkit.getPlayer(session.rightId);
        if (left == null || !left.isOnline() || right == null || !right.isOnline()) {
            return CancelReason.PLAYER_LEFT;
        }

        Inventory leftTop = left.getOpenInventory().getTopInventory();
        Inventory rightTop = right.getOpenInventory().getTopInventory();
        if (leftTop != session.inventory || rightTop != session.inventory) {
            return CancelReason.CLOSED;
        }

        if (distanceCheckEnabled && !arePlayersWithinTradeDistance(left, right)) {
            return CancelReason.TOO_FAR;
        }

        if (!snapshotMatches(session.leftSnapshot, session.inventory, LEFT_OFFER_SLOTS)
            || !snapshotMatches(session.rightSnapshot, session.inventory, RIGHT_OFFER_SLOTS)) {
            return CancelReason.ITEMS_CHANGED;
        }

        return null;
    }

    private void cancelSession(TradeSession session, CancelReason reason, Player actor, boolean notifyPlayers) {
        if (session == null || session.closing) {
            return;
        }

        session.closing = true;
        stopTimeout(session);
        stopCountdown(session);
        stopVisualRefresh(session);
        removeSession(session);

        List<ItemStack> leftItems = extractItems(session.inventory, LEFT_OFFER_SLOTS);
        List<ItemStack> rightItems = extractItems(session.inventory, RIGHT_OFFER_SLOTS);
        clearOfferSlots(session.inventory);

        Player left = resolvePlayer(session.leftId, actor);
        Player right = resolvePlayer(session.rightId, actor);

        giveItems(left, leftItems);
        giveItems(right, rightItems);

        closeSessionInventory(session);

        if (notifyPlayers && !session.executed) {
            String reasonKey = "trade.cancel." + reason.localeKey;

            if (left != null && left.isOnline()) {
                if (reason == CancelReason.TOO_FAR) {
                    left.sendMessage(plugin.getLocaleManager().getMessage(
                        reasonKey,
                        "distance", String.valueOf(maxDistanceBlocks)
                    ));
                } else {
                    left.sendMessage(plugin.getLocaleManager().getMessage(reasonKey));
                }
            }
            if (right != null && right.isOnline()) {
                if (reason == CancelReason.TOO_FAR) {
                    right.sendMessage(plugin.getLocaleManager().getMessage(
                        reasonKey,
                        "distance", String.valueOf(maxDistanceBlocks)
                    ));
                } else {
                    right.sendMessage(plugin.getLocaleManager().getMessage(reasonKey));
                }
            }
        }

        if (!session.executed) {
            playCancelEffects(session);
        }

        if (loggingEnabled) {
            plugin.getLogger().info("[Trade] Cancelled " + session.leftName + " <-> " + session.rightName
                + " | reason=" + reason.name());
        }
    }

    private void createSession(Player left, Player right) {
        UUID id = UUID.randomUUID();

        String title = plugin.getLocaleManager().getMessage("trade.gui.title",
            "player_a", left.getName(),
            "player_b", right.getName());

        Inventory inventory = Bukkit.createInventory(
            new TradeHolder(id),
            GUI_SIZE,
            deserializeNoItalic(title)
        );

        TradeSession session = new TradeSession(id, left.getUniqueId(), right.getUniqueId(), left.getName(), right.getName(), inventory);

        refreshVisualState(session);

        sessionsById.put(id, session);
        sessionsByPlayer.put(session.leftId, session);
        sessionsByPlayer.put(session.rightId, session);

        session.timeoutTask = Bukkit.getScheduler().runTaskLater(plugin,
            () -> cancelSession(session, CancelReason.TIMEOUT, null, true),
            Math.max(20L, sessionTimeoutSeconds * 20L));
        startVisualRefresh(session);

        left.openInventory(inventory);
        right.openInventory(inventory);

        left.sendMessage(plugin.getLocaleManager().getMessage("trade.session.started", "player", right.getName()));
        right.sendMessage(plugin.getLocaleManager().getMessage("trade.session.started", "player", left.getName()));

        if (loggingEnabled) {
            plugin.getLogger().info("[Trade] Session started " + left.getName() + " <-> " + right.getName());
        }
    }

    private void startVisualRefresh(TradeSession session) {
        stopVisualRefresh(session);
        session.visualTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (session.closing || !sessionsById.containsKey(session.id)) {
                stopVisualRefresh(session);
                return;
            }
            refreshVisualState(session);
        }, 0L, 10L);
    }

    private void refreshVisualState(TradeSession session) {
        if (session == null || session.closing) {
            return;
        }

        renderBaseLayout(session);
        renderPlayerCards(session);
        updateConfirmButtons(session);
        int remaining = session.countdownRunning ? session.countdownRemaining : -1;
        renderCountdownWidget(session, remaining);
        renderDistanceWidget(session);
    }

    private void renderBaseLayout(TradeSession session) {
        Inventory inventory = session.inventory;
        boolean bothConfirmed = session.leftConfirmed && session.rightConfirmed;

        Material baseMaterial = bothConfirmed ? themeConfirmedFillerMaterial : themeBaseFillerMaterial;
        Material frameMaterial = bothConfirmed ? themeConfirmedFillerMaterial : themeFrameFillerMaterial;
        Material dividerMaterial = bothConfirmed ? themeConfirmedDividerMaterial : themeDividerMaterial;

        ItemStack base = createStaticItem(baseMaterial);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isOfferSlot(slot) || slot == SLOT_CONFIRM_LEFT || slot == SLOT_CONFIRM_RIGHT) {
                continue;
            }
            inventory.setItem(slot, base);
        }

        ItemStack frame = createStaticItem(frameMaterial);
        for (int slot : FRAME_SLOTS) {
            if (isOfferSlot(slot) || slot == SLOT_CONFIRM_LEFT || slot == SLOT_CONFIRM_RIGHT) {
                continue;
            }
            inventory.setItem(slot, frame);
        }

        ItemStack divider = createStaticItem(dividerMaterial);
        for (int slot : DIVIDER_SLOTS) {
            inventory.setItem(slot, divider);
        }
    }

    private void renderPlayerCards(TradeSession session) {
        for (int slot : LEFT_HEADER_SLOTS) {
            session.inventory.setItem(slot, createStaticItem(getActiveFrameMaterial(session)));
        }
        for (int slot : RIGHT_HEADER_SLOTS) {
            session.inventory.setItem(slot, createStaticItem(getActiveFrameMaterial(session)));
        }

        Player left = Bukkit.getPlayer(session.leftId);
        Player right = Bukkit.getPlayer(session.rightId);

        session.inventory.setItem(
            SLOT_LEFT_HEAD,
            createPlayerCardItem(session.leftId, left, session.leftName, "trade.gui.header.left")
        );
        session.inventory.setItem(
            SLOT_RIGHT_HEAD,
            createPlayerCardItem(session.rightId, right, session.rightName, "trade.gui.header.right")
        );
        session.inventory.setItem(SLOT_EMBLEM, createEmblemItem(session.leftConfirmed && session.rightConfirmed));
    }

    private void renderStatusWidgets(TradeSession session) {
        session.inventory.setItem(SLOT_STATUS_LEFT, createStatusItem(session.leftName, session.leftConfirmed));
        session.inventory.setItem(SLOT_STATUS_RIGHT, createStatusItem(session.rightName, session.rightConfirmed));
    }

    private void renderCountdownWidget(TradeSession session, int seconds) {
        if (!showCountdownIndicator) {
            session.inventory.setItem(SLOT_COUNTDOWN, createStaticItem(getActiveFrameMaterial(session)));
            return;
        }

        boolean running = session.leftConfirmed && session.rightConfirmed && seconds > 0;
        String key = running ? "trade.gui.countdown.running" : "trade.gui.countdown.idle";
        String text = running
            ? plugin.getLocaleManager().getMessage(key, "seconds", String.valueOf(seconds))
            : plugin.getLocaleManager().getMessage(key);
        Material material = running ? Material.CLOCK : Material.REPEATER;

        ItemStack indicator = createNamedItem(material, text);
        if (running) {
            indicator.setAmount(Math.max(1, Math.min(64, seconds)));
        }

        session.inventory.setItem(SLOT_COUNTDOWN, indicator);
    }

    private void renderDistanceWidget(TradeSession session) {
        if (!showDistanceIndicator) {
            session.inventory.setItem(SLOT_DISTANCE, createStaticItem(getActiveFrameMaterial(session)));
            return;
        }

        DistanceSnapshot snapshot = getDistanceSnapshot(session);
        boolean tooFar = distanceCheckEnabled && !snapshot.withinRange;

        String label = plugin.getLocaleManager().getMessage(
            "trade.gui.distance.label",
            "distance", snapshot.displayDistance,
            "max", distanceCheckEnabled ? String.valueOf(maxDistanceBlocks) : "-",
            "status", plugin.getLocaleManager().getMessage(tooFar ? "trade.gui.distance.too-far" : "trade.gui.distance.ok")
        );
        String stateLine = plugin.getLocaleManager().getMessage(tooFar ? "trade.gui.distance.too-far" : "trade.gui.distance.ok");

        Material material = tooFar ? Material.BARRIER : Material.ENDER_PEARL;
        session.inventory.setItem(SLOT_DISTANCE, createNamedItem(material, label, List.of(stateLine)));
    }

    private void updateConfirmButtons(TradeSession session) {
        session.inventory.setItem(SLOT_CONFIRM_LEFT, createConfirmButton(session.leftName, session.leftConfirmed));
        session.inventory.setItem(SLOT_CONFIRM_RIGHT, createConfirmButton(session.rightName, session.rightConfirmed));
        renderStatusWidgets(session);
    }

    private ItemStack createStaticItem(Material material) {
        return createNamedItem(material, " ");
    }

    private ItemStack createNamedItem(Material material, String displayName) {
        return createNamedItem(material, displayName, List.of());
    }

    private ItemStack createNamedItem(Material material, String displayName, List<String> rawLore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(deserializeNoItalic(displayName == null ? " " : displayName));
        List<Component> lore = new ArrayList<>();
        for (String raw : rawLore) {
            for (String line : splitRawLines(raw)) {
                lore.add(deserializeNoItalic(line));
            }
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPlayerCardItem(UUID playerId, Player onlinePlayer, String fallbackName, String headerKey) {
        String displayName = plugin.getLocaleManager().getMessage(headerKey, "player", fallbackName);
        if (!showPlayerHeads) {
            return createNamedItem(Material.NAME_TAG, displayName);
        }

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta baseMeta = item.getItemMeta();
        if (!(baseMeta instanceof SkullMeta meta)) {
            return createNamedItem(Material.NAME_TAG, displayName);
        }

        OfflinePlayer owner = onlinePlayer != null ? onlinePlayer : Bukkit.getOfflinePlayer(playerId);
        meta.setOwningPlayer(owner);
        meta.displayName(deserializeNoItalic(displayName));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEmblemItem(boolean bothConfirmed) {
        Material material = bothConfirmed ? Material.SEA_LANTERN : Material.HEART_OF_THE_SEA;
        String display = plugin.getLocaleManager().getMessage("trade.gui.emblem.name");
        return createNamedItem(material, display);
    }

    private ItemStack createStatusItem(String ownerName, boolean confirmed) {
        Material material = confirmed ? Material.LIME_DYE : Material.ORANGE_DYE;
        String key = confirmed ? "trade.gui.status.ready" : "trade.gui.status.waiting";
        String label = plugin.getLocaleManager().getMessage(key, "player", ownerName);
        String ownerLine = plugin.getLocaleManager().getMessage(
            confirmed ? "trade.gui.confirm.green-name" : "trade.gui.confirm.red-name",
            "player", ownerName
        );
        return createNamedItem(material, label, List.of(ownerLine));
    }

    private Material getActiveFrameMaterial(TradeSession session) {
        return session.leftConfirmed && session.rightConfirmed
            ? themeConfirmedFillerMaterial
            : themeFrameFillerMaterial;
    }

    private ItemStack createConfirmButton(String ownerName, boolean confirmed) {
        ItemStack item = new ItemStack(confirmed ? Material.LIME_WOOL : Material.RED_WOOL);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String nameKey = confirmed ? "trade.gui.confirm.green-name" : "trade.gui.confirm.red-name";
        String loreKey = confirmed ? "trade.gui.confirm.green-lore" : "trade.gui.confirm.red-lore";

        meta.displayName(deserializeNoItalic(plugin.getLocaleManager().getMessage(nameKey, "player", ownerName)));

        String rawLore = plugin.getLocaleManager().getMessage(loreKey, "player", ownerName);
        List<Component> lore = new ArrayList<>();
        for (String line : splitRawLines(rawLore)) {
            lore.add(deserializeNoItalic(line));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (confirmed) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        }

        item.setItemMeta(meta);
        return item;
    }

    private void playSingleConfirmEffect(Player player) {
        if (player == null || !effectsEnabled) {
            return;
        }

        Location base = player.getLocation().add(0, 1.0, 0);
        playSound(player, Sound.UI_BUTTON_CLICK, 0.7f, 1.25f);
        spawnParticle(player, Particle.END_ROD, base, 8, 0.28, 0.35, 0.28, 0.01);
    }

    private void playDoubleConfirmEffects(TradeSession session) {
        if (!effectsEnabled) {
            return;
        }

        forEachOnlinePlayer(session, player -> {
            Location base = player.getLocation().add(0, 1.0, 0);
            playSound(player, Sound.BLOCK_AMETHYST_CLUSTER_HIT, 0.8f, 1.35f);
            spawnParticle(player, Particle.GLOW, base, 12, 0.35, 0.45, 0.35, 0.01);
        });
    }

    private void playCountdownEffects(TradeSession session, int seconds) {
        if (!effectsEnabled) {
            return;
        }

        float pitch = Math.min(2.0f, 1.0f + ((countdownSeconds - seconds) * 0.15f));
        forEachOnlinePlayer(session, player -> {
            Location base = player.getLocation().add(0, 1.0, 0);
            playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.65f, pitch);
            spawnParticle(player, Particle.ENCHANT, base, 6, 0.2, 0.25, 0.2, 0.0);
        });
    }

    private void playSuccessEffects(TradeSession session) {
        if (!effectsEnabled) {
            return;
        }

        forEachOnlinePlayer(session, player -> {
            Location base = player.getLocation().add(0, 1.0, 0);
            playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.15f);
            spawnParticle(player, Particle.FIREWORK, base, 12, 0.4, 0.5, 0.4, 0.02);
            spawnParticle(player, Particle.GLOW, base, 10, 0.35, 0.45, 0.35, 0.01);
        });
    }

    private void playCancelEffects(TradeSession session) {
        if (!effectsEnabled) {
            return;
        }

        forEachOnlinePlayer(session, player -> {
            Location base = player.getLocation().add(0, 1.0, 0);
            playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.65f, 0.85f);
            spawnParticle(player, Particle.SMOKE, base, 8, 0.28, 0.32, 0.28, 0.01);
        });
    }

    private void playSound(Player player, Sound sound, float baseVolume, float pitch) {
        float scaled = Math.max(0.0f, baseVolume * effectsSoundVolume);
        if (scaled <= 0.0f) {
            return;
        }
        player.playSound(player.getLocation(), sound, scaled, pitch);
    }

    private void spawnParticle(
        Player player,
        Particle particle,
        Location origin,
        int baseCount,
        double offsetX,
        double offsetY,
        double offsetZ,
        double extra
    ) {
        int scaled = (int) Math.round(baseCount * effectsParticleDensity);
        if (scaled <= 0) {
            return;
        }
        player.spawnParticle(particle, origin, scaled, offsetX, offsetY, offsetZ, extra);
    }

    private void forEachOnlinePlayer(TradeSession session, Consumer<Player> consumer) {
        Player left = Bukkit.getPlayer(session.leftId);
        if (left != null && left.isOnline()) {
            consumer.accept(left);
        }

        Player right = Bukkit.getPlayer(session.rightId);
        if (right != null && right.isOnline()) {
            consumer.accept(right);
        }
    }

    private void sendInviteButtons(Player target) {
        Component accept = deserializeNoItalic(plugin.getLocaleManager().getMessage("trade.invite.accept-button"))
            .clickEvent(ClickEvent.runCommand("/spawnerx trade accept"))
            .hoverEvent(HoverEvent.showText(deserializeNoItalic(plugin.getLocaleManager().getMessage("trade.invite.accept-hover"))));

        Component deny = deserializeNoItalic(plugin.getLocaleManager().getMessage("trade.invite.deny-button"))
            .clickEvent(ClickEvent.runCommand("/spawnerx trade deny"))
            .hoverEvent(HoverEvent.showText(deserializeNoItalic(plugin.getLocaleManager().getMessage("trade.invite.deny-hover"))));

        target.sendMessage(Component.empty().append(accept).append(Component.text(" ")).append(deny));
    }

    private void notifyBoth(TradeSession session, String path, String... replacements) {
        Player left = Bukkit.getPlayer(session.leftId);
        Player right = Bukkit.getPlayer(session.rightId);

        if (left != null) {
            left.sendMessage(plugin.getLocaleManager().getMessage(path, replacements));
        }
        if (right != null) {
            right.sendMessage(plugin.getLocaleManager().getMessage(path, replacements));
        }
    }

    private TradeSession getSessionByInventory(Inventory inventory) {
        if (!(inventory.getHolder() instanceof TradeHolder holder)) {
            return null;
        }
        return sessionsById.get(holder.getSessionId());
    }

    private boolean isPlayerInSession(UUID playerId) {
        return sessionsByPlayer.containsKey(playerId);
    }

    private boolean hasPendingInvite(UUID playerId) {
        return invitesByTarget.containsKey(playerId) || inviteTargetBySender.containsKey(playerId);
    }

    private DistanceSnapshot getDistanceSnapshot(TradeSession session) {
        Player left = Bukkit.getPlayer(session.leftId);
        Player right = Bukkit.getPlayer(session.rightId);
        Double distance = computeDistance(left, right);
        if (distance == null) {
            return new DistanceSnapshot(-1D, !distanceCheckEnabled, "-");
        }

        boolean within = !distanceCheckEnabled || distance <= maxDistanceBlocks;
        return new DistanceSnapshot(distance, within, formatDistance(distance));
    }

    private boolean arePlayersWithinTradeDistance(TradeSession session) {
        return getDistanceSnapshot(session).withinRange;
    }

    private boolean arePlayersWithinTradeDistance(Player first, Player second) {
        if (!distanceCheckEnabled) {
            return true;
        }
        Double distance = computeDistance(first, second);
        return distance != null && distance <= maxDistanceBlocks;
    }

    private Double computeDistance(Player first, Player second) {
        if (first == null || second == null) {
            return null;
        }
        if (!first.isOnline() || !second.isOnline()) {
            return null;
        }
        if (!first.getWorld().equals(second.getWorld())) {
            return null;
        }
        return first.getLocation().distance(second.getLocation());
    }

    private String formatDistance(double distance) {
        return String.format(Locale.US, "%.1f", distance);
    }

    private void expireInvite(UUID targetId) {
        TradeInvite invite = invitesByTarget.get(targetId);
        if (invite == null) {
            return;
        }

        removeInvite(invite, false);

        Player sender = Bukkit.getPlayer(invite.senderId());
        Player target = Bukkit.getPlayer(invite.targetId());

        if (sender != null) {
            sender.sendMessage(plugin.getLocaleManager().getMessage(
                "trade.invite.expired-sender",
                "player", resolvePlayerName(invite.targetId())
            ));
        }
        if (target != null) {
            target.sendMessage(plugin.getLocaleManager().getMessage(
                "trade.invite.expired-target",
                "player", resolvePlayerName(invite.senderId())
            ));
        }

        if (loggingEnabled) {
            plugin.getLogger().info("[Trade] Invite expired "
                + resolvePlayerName(invite.senderId()) + " -> " + resolvePlayerName(invite.targetId()));
        }
    }

    private void removeInvite(TradeInvite invite, boolean cancelTimeout) {
        invitesByTarget.remove(invite.targetId(), invite);
        inviteTargetBySender.remove(invite.senderId(), invite.targetId());

        if (cancelTimeout && invite.timeoutTask() != null) {
            invite.timeoutTask().cancel();
        }
    }

    private void closeSessionInventory(TradeSession session) {
        List<HumanEntity> viewers = new ArrayList<>(session.inventory.getViewers());
        for (HumanEntity viewer : viewers) {
            if (viewer instanceof Player player) {
                player.closeInventory();
            }
        }
    }

    private void removeSession(TradeSession session) {
        sessionsById.remove(session.id, session);
        sessionsByPlayer.remove(session.leftId, session);
        sessionsByPlayer.remove(session.rightId, session);
    }

    private void stopTimeout(TradeSession session) {
        if (session.timeoutTask != null) {
            session.timeoutTask.cancel();
            session.timeoutTask = null;
        }
    }

    private void stopCountdown(TradeSession session) {
        if (session.countdownTask != null) {
            session.countdownTask.cancel();
            session.countdownTask = null;
        }
        session.countdownRunning = false;
        session.countdownRemaining = -1;
    }

    private void stopVisualRefresh(TradeSession session) {
        if (session.visualTask != null) {
            session.visualTask.cancel();
            session.visualTask = null;
        }
    }

    private List<ItemStack> snapshotSlots(Inventory inventory, int[] slots) {
        List<ItemStack> snapshot = new ArrayList<>(slots.length);
        for (int slot : slots) {
            ItemStack item = inventory.getItem(slot);
            snapshot.add(item == null ? null : item.clone());
        }
        return snapshot;
    }

    private boolean snapshotMatches(List<ItemStack> snapshot, Inventory inventory, int[] slots) {
        if (snapshot == null || snapshot.size() != slots.length) {
            return false;
        }

        for (int i = 0; i < slots.length; i++) {
            ItemStack expected = snapshot.get(i);
            ItemStack current = inventory.getItem(slots[i]);
            if (!itemsEqual(expected, current)) {
                return false;
            }
        }
        return true;
    }

    private List<ItemStack> extractItems(Inventory inventory, int[] slots) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : slots) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            items.add(item.clone());
        }
        return items;
    }

    private void clearOfferSlots(Inventory inventory) {
        for (int slot : LEFT_OFFER_SLOTS) {
            inventory.setItem(slot, new ItemStack(Material.AIR));
        }
        for (int slot : RIGHT_OFFER_SLOTS) {
            inventory.setItem(slot, new ItemStack(Material.AIR));
        }
    }

    private void giveItems(Player player, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        if (player == null) {
            if (plugin.getServer().getWorlds().isEmpty()) {
                return;
            }
            for (ItemStack item : items) {
                if (item == null || item.getType() == Material.AIR) {
                    continue;
                }
                plugin.getServer().getWorlds().get(0).dropItemNaturally(
                    plugin.getServer().getWorlds().get(0).getSpawnLocation(),
                    item.clone()
                );
            }
            return;
        }

        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
            if (!leftovers.isEmpty()) {
                for (ItemStack leftover : leftovers.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
        }
    }

    private Player resolvePlayer(UUID playerId, Player fallback) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online;
        }

        if (fallback != null && fallback.getUniqueId().equals(playerId)) {
            return fallback;
        }

        return null;
    }

    private String resolvePlayerName(UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        if (offline.getName() != null && !offline.getName().isBlank()) {
            return offline.getName();
        }

        return playerId.toString();
    }

    private boolean isAllowedTradeItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        ItemStack toCheck = item;
        if (plugin.getSpawnerManager().isLegacySpawnerWithBlockState(item)) {
            toCheck = plugin.getSpawnerManager().sanitizeSpawnerItem(item);
        }

        return plugin.getSpawnerManager().isValidSpawner(toCheck);
    }

    private boolean isOwnOfferSlot(Side side, int slot) {
        if (side == Side.LEFT) {
            return LEFT_SLOT_SET.contains(slot);
        }
        return RIGHT_SLOT_SET.contains(slot);
    }

    private boolean isOfferSlot(int slot) {
        return LEFT_SLOT_SET.contains(slot) || RIGHT_SLOT_SET.contains(slot);
    }

    private boolean isPlacementAction(InventoryAction action) {
        return action == InventoryAction.PLACE_ALL
            || action == InventoryAction.PLACE_ONE
            || action == InventoryAction.PLACE_SOME
            || action == InventoryAction.SWAP_WITH_CURSOR
            || action == InventoryAction.HOTBAR_SWAP
            || action == InventoryAction.HOTBAR_MOVE_AND_READD;
    }

    private ItemStack resolveIncomingTopItem(InventoryClickEvent event) {
        InventoryAction action = event.getAction();
        if (action == InventoryAction.PLACE_ALL
            || action == InventoryAction.PLACE_ONE
            || action == InventoryAction.PLACE_SOME
            || action == InventoryAction.SWAP_WITH_CURSOR) {
            return event.getCursor();
        }

        if (action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD) {
            if (event.getWhoClicked() instanceof Player player) {
                int hotbarButton = event.getHotbarButton();
                if (hotbarButton >= 0 && hotbarButton < 9) {
                    return player.getInventory().getItem(hotbarButton);
                }
            }
        }

        return null;
    }

    private boolean itemsEqual(ItemStack first, ItemStack second) {
        if (isAir(first) && isAir(second)) {
            return true;
        }
        if (isAir(first) || isAir(second)) {
            return false;
        }
        return first.getAmount() == second.getAmount() && first.isSimilar(second);
    }

    private boolean isAir(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    private Side resolveSide(TradeSession session, UUID playerId) {
        if (session.leftId.equals(playerId)) {
            return Side.LEFT;
        }
        if (session.rightId.equals(playerId)) {
            return Side.RIGHT;
        }
        return Side.NONE;
    }

    private Material parseMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material parsed = Material.matchMaterial(raw.trim());
        if (parsed != null) {
            return parsed;
        }
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private String summarizeItems(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return "none";
        }

        List<String> parts = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            parts.add(item.getAmount() + "x" + item.getType().name());
        }

        return parts.isEmpty() ? "none" : String.join(",", parts);
    }

    private List<String> splitRawLines(String raw) {
        List<String> lines = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return lines;
        }
        for (String line : raw.split("\\\\n")) {
            lines.add(line);
        }
        return lines;
    }

    private Component deserializeNoItalic(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
            .deserialize(text == null ? "" : text)
            .decoration(TextDecoration.ITALIC, false);
    }

    private static Set<Integer> toSet(int[] slots) {
        Set<Integer> set = new HashSet<>();
        for (int slot : slots) {
            set.add(slot);
        }
        return set;
    }

    public enum InviteStatus {
        SUCCESS,
        DISABLED,
        SELF_TARGET,
        TARGET_OFFLINE,
        TOO_FAR,
        SENDER_BUSY,
        TARGET_BUSY
    }

    public enum RespondStatus {
        SUCCESS,
        DISABLED,
        NO_PENDING,
        SENDER_OFFLINE,
        TOO_FAR,
        PLAYER_BUSY
    }

    public enum CancelReason {
        CLOSED("closed"),
        PLAYER_LEFT("player-left"),
        UNCONFIRMED("unconfirmed"),
        ITEMS_CHANGED("items-changed"),
        TOO_FAR("too-far"),
        TIMEOUT("timeout"),
        DISABLED("disabled"),
        SHUTDOWN("shutdown");

        private final String localeKey;

        CancelReason(String localeKey) {
            this.localeKey = localeKey;
        }
    }

    private enum Side {
        LEFT,
        RIGHT,
        NONE
    }

    private record DistanceSnapshot(double distance, boolean withinRange, String displayDistance) {
    }

    private record TradeInvite(UUID senderId, UUID targetId, long expiresAt, BukkitTask timeoutTask) {
    }

    private static final class TradeSession {
        private final UUID id;
        private final UUID leftId;
        private final UUID rightId;
        private final String leftName;
        private final String rightName;
        private final Inventory inventory;

        private boolean leftConfirmed;
        private boolean rightConfirmed;
        private boolean closing;
        private boolean executed;

        private List<ItemStack> leftSnapshot;
        private List<ItemStack> rightSnapshot;

        private boolean countdownRunning;
        private int countdownRemaining = -1;

        private BukkitTask timeoutTask;
        private BukkitTask countdownTask;
        private BukkitTask visualTask;

        private TradeSession(UUID id, UUID leftId, UUID rightId, String leftName, String rightName, Inventory inventory) {
            this.id = id;
            this.leftId = leftId;
            this.rightId = rightId;
            this.leftName = leftName;
            this.rightName = rightName;
            this.inventory = inventory;
        }
    }
}

package me.fluxmarket.module.sell.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.module.sell.SellProgressManager;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SellProgressGui implements FluxGui {

    private static final int SLOT_CLOSE = 45;
    private static final int SLOT_INFO  = 49;

    // 3x2 grid centered in rows 0-3 of 9-wide inventory
    // Row 1: slots 10, 12, 14    Row 2: slots 19, 21, 23
    private static final int[] CATEGORY_SLOTS = {10, 12, 14, 19, 21, 23};

    // Default categories — these should match config keys
    private static final String[] CATEGORIES = {"blocks", "ores", "farming", "mob_drops", "nether", "misc"};

    private static final Material[] CATEGORY_ICONS = {
            Material.GRASS_BLOCK,
            Material.DIAMOND_ORE,
            Material.WHEAT,
            Material.ROTTEN_FLESH,
            Material.NETHERRACK,
            Material.CHEST
    };

    private final FluxMarket plugin;
    private final Player player;
    private final Inventory inventory;
    private final SellProgressManager progressManager;

    public SellProgressGui(FluxMarket plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.progressManager = plugin.getSellProgressManager();
        inventory = Bukkit.createInventory(null, 54,
                FormatUtils.comp("&8» &eSell Progression"));
        populate();
    }

    private void populate() {
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);

        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) inventory.setItem(i, filler);

        // Close button
        inventory.setItem(SLOT_CLOSE, ItemUtils.named(Material.BARRIER, "&cClose",
                "&7Return to previous menu"));

        // Info item
        inventory.setItem(SLOT_INFO, ItemUtils.named(Material.BOOK, "&eYour Sell Progression",
                "&7Click a category for details",
                "&7Earn money in each category",
                "&7to unlock better sell multipliers!"));

        // Overall best multiplier for center info
        double bestMult = 1.0;
        for (String cat : CATEGORIES) {
            double m = progressManager.getMultiplier(player.getUniqueId(), cat);
            if (m > bestMult) bestMult = m;
        }
        double maxPossible = progressManager.getMultiplierForTier(20);

        // Bottom row center override
        inventory.setItem(49, ItemUtils.named(Material.BOOK, "&eYour Sell Progression",
                "&7Active multiplier: &a" + String.format("%.2f", bestMult) + "x",
                "&7Max achievable: &6" + String.format("%.2f", maxPossible) + "x",
                "&7Click a category for details"));

        // Category panels
        for (int i = 0; i < CATEGORIES.length && i < CATEGORY_SLOTS.length; i++) {
            String cat = CATEGORIES[i];
            Material icon = getCategoryIcon(cat, i);
            inventory.setItem(CATEGORY_SLOTS[i], buildCategoryItem(cat, icon));
        }
    }

    private Material getCategoryIcon(String category, int index) {
        // Try to get icon from shop registry
        if (plugin.getShopModule() != null) {
            var shopCat = plugin.getShopModule().getRegistry().getCategories().get(category);
            if (shopCat != null) return shopCat.icon();
        }
        return index < CATEGORY_ICONS.length ? CATEGORY_ICONS[index] : Material.CHEST;
    }

    private ItemStack buildCategoryItem(String category, Material icon) {
        int tier = progressManager.getTier(player.getUniqueId(), category);
        double totalEarned = progressManager.getTotalEarned(player.getUniqueId(), category);
        double multiplier = progressManager.getMultiplierForTier(tier);

        double currentThreshold = progressManager.getThresholdForTier(tier);
        double nextThreshold = tier < 20 ? progressManager.getThresholdForTier(tier + 1) : currentThreshold;
        double progressInTier = totalEarned - currentThreshold;
        double tierRange = nextThreshold - currentThreshold;

        double percent = tier >= 20 ? 100.0
                : (tierRange > 0 ? Math.min(100.0, (progressInTier / tierRange) * 100.0) : 100.0);
        int filled = (int) Math.round(percent / 10.0);
        filled = Math.max(0, Math.min(10, filled));

        String bar = "&a" + "█".repeat(filled) + "&8" + "░".repeat(10 - filled);

        double remaining = tier >= 20 ? 0 : Math.max(0, nextThreshold - totalEarned);

        String displayName = FormatUtils.formatMaterialName(category.replace("_", " "));

        List<String> lore = new ArrayList<>();
        lore.add("&7Progress: " + bar + " &f" + String.format("%.1f", percent) + "%");
        lore.add("&7Earned: &a$" + FormatUtils.formatMoney(totalEarned));
        if (tier < 20) {
            lore.add("&7Next tier: &e$" + FormatUtils.formatMoney(remaining) + " more");
        } else {
            lore.add("&7Next tier: &6MAX TIER");
        }
        lore.add("&7Multiplier: &a" + String.format("%.2f", multiplier) + "x");

        return ItemUtils.named(icon,
                "&6" + displayName + " &8\u2014 Tier " + tier + "/20",
                lore.toArray(new String[0]));
    }

    public void open() {
        player.openInventory(inventory);
        GuiListener.open(player.getUniqueId(), this);
        // Load fresh data from DB async, then repopulate on main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            progressManager.loadPlayerSync(player.getUniqueId());
            plugin.getServer().getScheduler().runTask(plugin, this::populate);
        });
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        int slot = event.getSlot();

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        // Check if clicking a category slot
        for (int i = 0; i < CATEGORY_SLOTS.length; i++) {
            if (slot == CATEGORY_SLOTS[i] && i < CATEGORIES.length) {
                sendCategoryDetails(CATEGORIES[i]);
                return;
            }
        }
    }

    private void sendCategoryDetails(String category) {
        String prefix = plugin.getConfigManager().getPrefix();
        int tier = progressManager.getTier(player.getUniqueId(), category);
        double total = progressManager.getTotalEarned(player.getUniqueId(), category);
        double mult = progressManager.getMultiplierForTier(tier);
        String displayName = FormatUtils.formatMaterialName(category.replace("_", " "));

        player.sendMessage(FormatUtils.color(prefix + "&6" + displayName
                + " &8\u2014 &eTier " + tier + "/20"));
        player.sendMessage(FormatUtils.color("  &7Total Earned: &a$" + FormatUtils.formatMoney(total)));
        player.sendMessage(FormatUtils.color("  &7Multiplier:   &a" + String.format("%.2f", mult) + "x"));
    }
}

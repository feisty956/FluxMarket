package me.fluxmarket.module.shop.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.module.flux.FluxEngine;
import me.fluxmarket.module.shop.ShopCategory;
import me.fluxmarket.module.shop.ShopItem;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ShopItemsGui implements FluxGui {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_PREV = 46;
    private static final int SLOT_NEXT = 52;

    private final FluxMarket plugin;
    private final Player player;
    private final ShopCategory category;
    private final Inventory inventory;
    private int page = 0;

    public ShopItemsGui(FluxMarket plugin, Player player, ShopCategory category) {
        this.plugin = plugin;
        this.player = player;
        this.category = category;
        inventory = Bukkit.createInventory(null, 54,
                FormatUtils.comp("&8» &6" + category.displayName()));
        populate();
    }

    private void populate() {
        // Clear
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);
        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) inventory.setItem(i, filler);

        // Items
        List<ShopItem> items = category.items();
        int start = page * PAGE_SIZE;
        FluxEngine engine = plugin.getFluxModule() != null ? plugin.getFluxModule().getEngine() : null;
        String playerUuid = player.getUniqueId().toString();

        for (int i = 0; i < PAGE_SIZE && (start + i) < items.size(); i++) {
            ShopItem shopItem = items.get(start + i);
            Material mat;
            try { mat = Material.valueOf(shopItem.material()); }
            catch (IllegalArgumentException e) { continue; }

            double base = shopItem.basePrice();
            double buyPrice  = shopItem.hasBuyOverride()  ? shopItem.buyPriceOverride()
                             : engine != null ? engine.getBuyPrice(shopItem.material(), base, playerUuid) : base;
            double sellPrice = shopItem.hasSellOverride() ? shopItem.sellPriceOverride()
                             : engine != null ? engine.getSellPrice(shopItem.material(), base, playerUuid) : base;
            double pct   = engine != null ? engine.getPercentChange(shopItem.material(), base) : 0.0;
            String trend = FormatUtils.trendIndicator(pct);

            List<String> lore = new ArrayList<>();
            // Price line — always first so it's immediately visible
            if (shopItem.buyEnabled() && shopItem.sellEnabled()) {
                lore.add("&7Buy: &a$" + FormatUtils.formatMoney(buyPrice)
                        + "  &7Sell: &c$" + FormatUtils.formatMoney(sellPrice));
            } else if (shopItem.buyEnabled()) {
                lore.add("&7Buy: &a$" + FormatUtils.formatMoney(buyPrice));
            } else if (shopItem.sellEnabled()) {
                lore.add("&7Sell: &c$" + FormatUtils.formatMoney(sellPrice));
            }
            lore.add("&7Trend: " + trend + " &7" + FormatUtils.formatPercent(pct));
            // Price history sparkline (last 8 snapshots)
            if (engine != null) {
                double[] history = engine.getPriceHistory(shopItem.material(), 8);
                boolean hasHistory = java.util.Arrays.stream(history).anyMatch(v -> v > 0);
                if (hasHistory) {
                    double minH = java.util.Arrays.stream(history).filter(v -> v > 0).min().orElse(0);
                    double maxH = java.util.Arrays.stream(history).filter(v -> v > 0).max().orElse(0);
                    lore.add("&7Chart:  &f" + FormatUtils.sparkline(history, minH, maxH));
                }
            }
            lore.add("");
            if (shopItem.buyEnabled())  lore.add("&eLeft-click &7to buy");
            if (shopItem.sellEnabled()) lore.add("&eRight-click &7to sell");

            inventory.setItem(i, ItemUtils.named(mat,
                    "&f" + FormatUtils.formatMaterialName(shopItem.material()),
                    lore.toArray(new String[0])));
        }

        // Navigation
        inventory.setItem(SLOT_BACK, ItemUtils.named(Material.ARROW, "&7Back"));
        if (page > 0) inventory.setItem(SLOT_PREV, ItemUtils.named(Material.ARROW, "&7Previous Page"));
        if ((page + 1) * PAGE_SIZE < items.size())
            inventory.setItem(SLOT_NEXT, ItemUtils.named(Material.ARROW, "&7Next Page"));
    }

    public void open() {
        player.openInventory(inventory);
        GuiListener.open(player.getUniqueId(), this);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        int slot = event.getSlot();

        if (slot == SLOT_BACK) { new ShopCategoryGui(plugin, player).open(); return; }
        if (slot == SLOT_PREV && page > 0) { page--; populate(); return; }
        if (slot == SLOT_NEXT) { page++; populate(); return; }
        if (slot >= PAGE_SIZE) return;

        int idx = page * PAGE_SIZE + slot;
        List<ShopItem> items = category.items();
        if (idx >= items.size()) return;
        ShopItem shopItem = items.get(idx);

        boolean isLeft  = event.getClick() == ClickType.LEFT  || event.getClick() == ClickType.SHIFT_LEFT;
        boolean isRight = event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT;

        if (isLeft && shopItem.buyEnabled()) {
            new ShopConfirmGui(plugin, player, shopItem, true).open();
        } else if (isRight && shopItem.sellEnabled()) {
            new ShopConfirmGui(plugin, player, shopItem, false).open();
        }
    }
}

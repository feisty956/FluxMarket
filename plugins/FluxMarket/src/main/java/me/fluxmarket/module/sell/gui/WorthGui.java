package me.fluxmarket.module.sell.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.module.shop.ShopCategory;
import me.fluxmarket.module.shop.ShopItem;
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
import java.util.Comparator;
import java.util.List;

public class WorthGui implements FluxGui {

    public enum SortMode { SELL_HIGH, SELL_LOW, NAME_AZ, NAME_ZA }

    private static final int PAGE_SIZE  = 45;
    private static final int SLOT_PREV  = 45;
    private static final int SLOT_SORT  = 46;
    private static final int SLOT_INFO  = 49;
    private static final int SLOT_NEXT  = 53;

    private final FluxMarket plugin;
    private final Player player;
    private final Inventory inventory;
    private final List<ShopItem> allItems = new ArrayList<>();
    private List<ShopItem> sortedItems;
    private int page = 0;
    private SortMode sort = SortMode.SELL_HIGH;

    public WorthGui(FluxMarket plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        inventory = Bukkit.createInventory(null, 54,
                FormatUtils.comp("&8» &aWorth Guide"));

        // Build item list from all shop categories
        if (plugin.getShopModule() != null) {
            for (ShopCategory cat : plugin.getShopModule().getRegistry().getCategories().values()) {
                allItems.addAll(cat.items());
            }
        }

        applySort();
        populate();
    }

    private void applySort() {
        sortedItems = new ArrayList<>(allItems);
        sortedItems.sort(switch (sort) {
            case SELL_HIGH -> Comparator.comparingDouble(this::effectiveSellPrice).reversed();
            case SELL_LOW  -> Comparator.comparingDouble(this::effectiveSellPrice);
            case NAME_AZ   -> Comparator.comparing(i -> FormatUtils.formatMaterialName(i.material()));
            case NAME_ZA   -> Comparator.<ShopItem, String>comparing(
                    i -> FormatUtils.formatMaterialName(i.material())).reversed();
        });
    }

    private double effectiveSellPrice(ShopItem item) {
        if (!item.sellEnabled()) return -1;
        if (item.hasSellOverride()) return item.sellPriceOverride();
        if (plugin.getFluxModule() != null) {
            return plugin.getFluxModule().getEngine().getSellPrice(item.material(), item.basePrice(), "");
        }
        return item.basePrice();
    }

    private double effectiveBuyPrice(ShopItem item) {
        if (!item.buyEnabled()) return -1;
        if (item.hasBuyOverride()) return item.buyPriceOverride();
        if (plugin.getFluxModule() != null) {
            return plugin.getFluxModule().getEngine().getBuyPrice(item.material(), item.basePrice(), "");
        }
        return item.basePrice();
    }

    private void populate() {
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);
        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) inventory.setItem(i, filler);

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && (start + i) < sortedItems.size(); i++) {
            inventory.setItem(i, buildEntryItem(sortedItems.get(start + i)));
        }

        if (page > 0) {
            inventory.setItem(SLOT_PREV, ItemUtils.named(Material.ARROW, "&7Previous Page"));
        }
        if ((page + 1) * PAGE_SIZE < sortedItems.size()) {
            inventory.setItem(SLOT_NEXT, ItemUtils.named(Material.ARROW, "&7Next Page"));
        }

        inventory.setItem(SLOT_SORT, ItemUtils.named(Material.HOPPER,
                "&eSort: &f" + sortName(), "&7Click to cycle sort mode"));

        inventory.setItem(SLOT_INFO, ItemUtils.named(Material.EMERALD,
                "&aWorth Guide",
                "&7Items available: &f" + sortedItems.size(),
                "&7Showing all sellable &7/ buyable items"));
    }

    private ItemStack buildEntryItem(ShopItem item) {
        Material mat;
        try { mat = Material.valueOf(item.material()); }
        catch (IllegalArgumentException e) { mat = Material.PAPER; }

        List<String> lore = new ArrayList<>();

        if (item.sellEnabled()) {
            double sellPrice = effectiveSellPrice(item);
            lore.add("&7Sell: &c$" + FormatUtils.formatMoney(sellPrice));
        }
        if (item.buyEnabled()) {
            double buyPrice = effectiveBuyPrice(item);
            lore.add("&7Buy: &a$" + FormatUtils.formatMoney(buyPrice));
        }
        if (plugin.getFluxModule() != null && item.sellEnabled()) {
            double base = item.basePrice();
            double current = effectiveSellPrice(item);
            double pct = base > 0 ? ((current - base) / base) * 100.0 : 0.0;
            lore.add("&7Trend: " + FormatUtils.trendIndicator(pct)
                    + " &8(" + FormatUtils.formatPercent(pct) + "&8)");
        }

        return ItemUtils.named(mat, "&f" + FormatUtils.formatMaterialName(item.material()),
                lore.toArray(new String[0]));
    }

    private String sortName() {
        return switch (sort) {
            case SELL_HIGH -> "Sell Price ↓";
            case SELL_LOW  -> "Sell Price ↑";
            case NAME_AZ   -> "Name A→Z";
            case NAME_ZA   -> "Name Z→A";
        };
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

        if (slot == SLOT_PREV && page > 0) { page--; populate(); return; }
        if (slot == SLOT_NEXT && (page + 1) * PAGE_SIZE < sortedItems.size()) { page++; populate(); return; }
        if (slot == SLOT_SORT) {
            sort = SortMode.values()[(sort.ordinal() + 1) % SortMode.values().length];
            page = 0;
            applySort();
            populate();
        }
    }
}

package me.fluxmarket.module.auction.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.module.auction.AuctionItem;
import me.fluxmarket.module.auction.AuctionManager;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AuctionFilteredResultsGui implements FluxGui {

    private static final int PAGE_SIZE  = 45;
    private static final int SLOT_PREV  = 45;
    private static final int SLOT_INFO  = 46;
    private static final int SLOT_BACK  = 49;
    private static final int SLOT_NEXT  = 53;

    private final FluxMarket plugin;
    private final Player player;
    private final String nameFilter;
    private final Double minPrice;
    private final Double maxPrice;
    private final String sellerFilter;
    private final AuctionMainGui mainGui;

    private final Inventory inventory;
    private int page = 0;
    private List<AuctionItem> filteredItems;

    public AuctionFilteredResultsGui(FluxMarket plugin, Player player,
                                     String nameFilter, Double minPrice, Double maxPrice,
                                     String sellerFilter, AuctionMainGui mainGui) {
        this.plugin        = plugin;
        this.player        = player;
        this.nameFilter    = nameFilter;
        this.minPrice      = minPrice;
        this.maxPrice      = maxPrice;
        this.sellerFilter  = sellerFilter;
        this.mainGui       = mainGui;

        inventory = Bukkit.createInventory(null, 54, FormatUtils.comp("&8» &6AH Search Results"));
        buildFilteredList();
        populate();
    }

    private void buildFilteredList() {
        AuctionManager mgr = plugin.getAuctionModule().getManager();
        filteredItems = mgr.getAll().stream()
                .filter(a -> !a.isExpired())
                .filter(a -> nameFilter == null
                        || a.getItemDisplayName().toLowerCase().contains(nameFilter.toLowerCase()))
                .filter(a -> minPrice == null || a.getEffectivePrice() >= minPrice)
                .filter(a -> maxPrice == null || a.getEffectivePrice() <= maxPrice)
                .filter(a -> sellerFilter == null
                        || a.getSellerName().toLowerCase().contains(sellerFilter.toLowerCase()))
                .sorted(Comparator.comparingDouble(AuctionItem::getEffectivePrice))
                .collect(Collectors.toList());
    }

    private void populate() {
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);
        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) inventory.setItem(i, filler);

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && (start + i) < filteredItems.size(); i++) {
            AuctionItem ai = filteredItems.get(start + i);
            inventory.setItem(i, buildListingItem(ai));
        }

        if (page > 0)
            inventory.setItem(SLOT_PREV, ItemUtils.named(Material.ARROW, "&7Previous Page"));
        if ((page + 1) * PAGE_SIZE < filteredItems.size())
            inventory.setItem(SLOT_NEXT, ItemUtils.named(Material.ARROW, "&7Next Page"));

        // Info slot: show active filter summary
        inventory.setItem(SLOT_INFO, ItemUtils.named(Material.SPYGLASS,
                "&6Search Filters",
                "&7Name: &f"   + (nameFilter   != null ? nameFilter   : "any"),
                "&7Min: &f"    + (minPrice     != null ? "$" + FormatUtils.formatMoney(minPrice)  : "none"),
                "&7Max: &f"    + (maxPrice     != null ? "$" + FormatUtils.formatMoney(maxPrice)  : "none"),
                "&7Seller: &f" + (sellerFilter != null ? sellerFilter : "any"),
                "&8Showing &7" + filteredItems.size() + " &8result(s)"));

        inventory.setItem(SLOT_BACK, ItemUtils.named(Material.BARRIER, "&cBack to Search"));
    }

    private ItemStack buildListingItem(AuctionItem ai) {
        ItemStack display = ai.getItem();
        var meta = display.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(FormatUtils.comp("&7Seller: &f" + ai.getSellerName()));
        if (ai.isBid()) {
            lore.add(FormatUtils.comp("&7Start Price: &e" + FormatUtils.formatMoney(ai.getPrice())));
            if (ai.getCurrentBid() > 0) {
                lore.add(FormatUtils.comp("&7Top Bid: &a" + FormatUtils.formatMoney(ai.getCurrentBid())
                        + " &8(" + ai.getHighestBidderName() + ")"));
            }
            lore.add(FormatUtils.comp("&7Type: &bAuction"));
        } else {
            lore.add(FormatUtils.comp("&7Price: &a" + FormatUtils.formatMoney(ai.getPrice())));
            lore.add(FormatUtils.comp("&7Type: &eBuy Now"));
        }
        lore.add(FormatUtils.comp("&7Expires in: &f" + FormatUtils.formatDuration(ai.getRemainingMillis())));
        lore.add(FormatUtils.comp(""));
        if (!ai.getSellerUuid().equals(player.getUniqueId())) {
            lore.add(FormatUtils.comp(ai.isBid() ? "&eLeft-click &7- Bid" : "&eLeft-click &7- Buy"));
        }
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
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

        if (slot == SLOT_PREV && page > 0) {
            page--;
            populate();
            return;
        }
        if (slot == SLOT_NEXT) {
            page++;
            populate();
            return;
        }
        if (slot == SLOT_BACK) {
            new AuctionAdvancedSearchGui(plugin, player, mainGui,
                    nameFilter, minPrice, maxPrice, sellerFilter).open();
            return;
        }
        if (slot >= PAGE_SIZE) return;

        int idx = page * PAGE_SIZE + slot;
        if (idx >= filteredItems.size()) return;
        AuctionItem ai = filteredItems.get(idx);

        if (ai.getSellerUuid().equals(player.getUniqueId())) return;

        if (ai.isBid()) {
            new AuctionBidGui(plugin, player, ai, mainGui).open();
            return;
        }

        if (!plugin.getEconomyProvider().has(player, ai.getPrice())) {
            player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cNot enough money! Required: &f" + FormatUtils.formatMoney(ai.getPrice())));
            return;
        }
        new AuctionBuyConfirmGui(plugin, player, ai, mainGui).open();
    }
}

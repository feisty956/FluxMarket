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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AuctionMainGui implements FluxGui {

    public enum SortMode { NEWEST, PRICE_ASC, PRICE_DESC, EXPIRING }

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV           = 45;
    private static final int SLOT_SORT           = 46;
    private static final int SLOT_SEARCH         = 48;
    private static final int SLOT_MY             = 50;
    private static final int SLOT_ADV_SEARCH     = 51;
    private static final int SLOT_MAILBOX        = 52;
    private static final int SLOT_NEXT           = 53;

    private final FluxMarket plugin;
    private final Player player;
    private final Inventory inventory;
    private int page = 0;
    private SortMode sort = SortMode.NEWEST;
    private String filter = null;
    private List<AuctionItem> currentItems;

    public AuctionMainGui(FluxMarket plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        inventory = Bukkit.createInventory(null, 54,
                FormatUtils.comp("&8» &6Auction House"));
        refresh();
    }

    public void refresh() {
        buildItemList();
        populate();
    }

    private void buildItemList() {
        AuctionManager mgr = plugin.getAuctionModule().getManager();
        currentItems = mgr.getAll().stream()
                .filter(a -> !a.isExpired())
                .filter(a -> filter == null || a.getItemDisplayName().toLowerCase().contains(filter.toLowerCase()))
                .sorted(switch (sort) {
                    case NEWEST -> Comparator.comparingLong(AuctionItem::getListedAt).reversed();
                    case PRICE_ASC -> Comparator.comparingDouble(AuctionItem::getEffectivePrice);
                    case PRICE_DESC -> Comparator.comparingDouble(AuctionItem::getEffectivePrice).reversed();
                    case EXPIRING -> Comparator.comparingLong(AuctionItem::getExpiresAt);
                })
                .collect(Collectors.toList());
    }

    private void populate() {
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);
        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) inventory.setItem(i, filler);

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && (start + i) < currentItems.size(); i++) {
            AuctionItem ai = currentItems.get(start + i);
            inventory.setItem(i, buildListingItem(ai));
        }

        if (page > 0) inventory.setItem(SLOT_PREV, ItemUtils.named(Material.ARROW, "&7Previous Page"));
        if ((page + 1) * PAGE_SIZE < currentItems.size()) {
            inventory.setItem(SLOT_NEXT, ItemUtils.named(Material.ARROW, "&7Next Page"));
        }

        inventory.setItem(SLOT_SORT, ItemUtils.named(Material.HOPPER, "&eSort: &f" + sortName(),
                "&7Click to cycle"));
        inventory.setItem(SLOT_SEARCH, ItemUtils.named(Material.OAK_SIGN,
                "&eSearch" + (filter != null ? ": &f" + filter : ""),
                "&7Click to search",
                "&7Right-click to reset"));
        inventory.setItem(SLOT_MY, ItemUtils.named(Material.CHEST, "&eMy Listings",
                "&7Click to manage",
                "&7your listings"));
        inventory.setItem(SLOT_ADV_SEARCH, ItemUtils.named(Material.SPYGLASS,
                "&8[&6AH&8] &7Advanced Search",
                "&7Filter by name, price range,",
                "&7and seller"));

        int mailboxCount = plugin.getAuctionModule().getDao().countMailbox(player.getUniqueId());
        inventory.setItem(SLOT_MAILBOX, ItemUtils.named(
                mailboxCount > 0 ? Material.CHEST_MINECART : Material.MINECART,
                "&eMailbox" + (mailboxCount > 0 ? " &c(" + mailboxCount + ")" : ""),
                "&7Claim expired items and",
                "&7earnings here"));
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

    private String sortName() {
        return switch (sort) {
            case NEWEST -> "Newest";
            case PRICE_ASC -> "Price Asc";
            case PRICE_DESC -> "Price Desc";
            case EXPIRING -> "Expiring";
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
        if (slot == SLOT_SORT) {
            sort = SortMode.values()[(sort.ordinal() + 1) % SortMode.values().length];
            refresh();
            return;
        }
        if (slot == SLOT_SEARCH) {
            if (event.getClick() == ClickType.RIGHT) {
                filter = null;
                refresh();
            } else {
                new AuctionSearchGui(plugin, player, this).open();
            }
            return;
        }
        if (slot == SLOT_MY) {
            new AuctionMyListingsGui(plugin, player, this).open();
            return;
        }
        if (slot == SLOT_ADV_SEARCH) {
            new AuctionAdvancedSearchGui(plugin, player, this).open();
            return;
        }
        if (slot == SLOT_MAILBOX) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                var entries = plugin.getAuctionModule().getDao().getMailbox(player.getUniqueId());
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> new AuctionMailboxGui(plugin, player, entries).open());
            });
            return;
        }
        if (slot >= PAGE_SIZE) return;

        int idx = page * PAGE_SIZE + slot;
        if (idx >= currentItems.size()) return;
        AuctionItem ai = currentItems.get(idx);

        if (ai.getSellerUuid().equals(player.getUniqueId())) return;

        if (ai.isBid()) {
            new AuctionBidGui(plugin, player, ai, this).open();
            return;
        }

        if (!plugin.getEconomyProvider().has(player, ai.getPrice())) {
            player.sendMessage(plugin.getConfigManager().getPrefix()
                    + "&cNot enough money! Required: &f" + FormatUtils.formatMoney(ai.getPrice()));
            return;
        }
        new AuctionBuyConfirmGui(plugin, player, ai, this).open();
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }
}

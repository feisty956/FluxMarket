package me.fluxmarket.module.auction;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class AuctionItem {

    private final UUID uuid;
    private final UUID sellerUuid;
    private final String sellerName;
    private final ItemStack item;
    private final String itemDisplayName;
    private final boolean bid; // true = auction (bid), false = BIN (fixed price)
    private double price;       // BIN price or start bid
    private double currentBid;
    private UUID highestBidder;
    private String highestBidderName;
    private final long listedAt;
    private long expiresAt;

    public AuctionItem(UUID uuid, UUID sellerUuid, String sellerName, ItemStack item,
                       double price, boolean bid, long durationMillis) {
        this.uuid = uuid;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.item = item.clone();
        this.itemDisplayName = getItemName(item);
        this.bid = bid;
        this.price = price;
        this.currentBid = 0;
        this.listedAt = System.currentTimeMillis();
        this.expiresAt = listedAt + durationMillis;
    }

    // DB reconstruction constructor
    public AuctionItem(UUID uuid, UUID sellerUuid, String sellerName, ItemStack item,
                       String itemDisplayName, double price, boolean bid, double currentBid,
                       UUID highestBidder, String highestBidderName, long listedAt, long expiresAt) {
        this.uuid = uuid;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.item = item;
        this.itemDisplayName = itemDisplayName;
        this.bid = bid;
        this.price = price;
        this.currentBid = currentBid;
        this.highestBidder = highestBidder;
        this.highestBidderName = highestBidderName;
        this.listedAt = listedAt;
        this.expiresAt = expiresAt;
    }

    private String getItemName(ItemStack item) {
        var meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().serialize(meta.displayName());
        }
        String n = item.getType().name().toLowerCase().replace('_', ' ');
        if (n.isEmpty()) return "Unknown Item";
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    public boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
    public long getRemainingMillis() { return Math.max(0, expiresAt - System.currentTimeMillis()); }

    public void placeBid(double amount, UUID bidder, String bidderName) {
        this.currentBid = amount;
        this.highestBidder = bidder;
        this.highestBidderName = bidderName;
    }

    public void extendExpiry(long millis) { expiresAt += millis; }

    /** Returns the stack size of the listed item. */
    public int getQuantity() { return item.getAmount(); }

    // Getters
    public UUID getUuid() { return uuid; }
    public UUID getSellerUuid() { return sellerUuid; }
    public String getSellerName() { return sellerName; }
    public ItemStack getItem() { return item.clone(); }
    public String getItemDisplayName() { return itemDisplayName; }
    public boolean isBid() { return bid; }
    public double getPrice() { return price; }
    public double getCurrentBid() { return currentBid; }
    public double getEffectivePrice() { return bid ? (currentBid > 0 ? currentBid : price) : price; }
    public UUID getHighestBidder() { return highestBidder; }
    public String getHighestBidderName() { return highestBidderName; }
    public long getListedAt() { return listedAt; }
    public long getExpiresAt() { return expiresAt; }
}

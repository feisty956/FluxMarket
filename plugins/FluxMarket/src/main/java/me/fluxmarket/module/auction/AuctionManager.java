package me.fluxmarket.module.auction;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AuctionManager {

    private final FluxMarket plugin;
    private final AuctionDao dao;
    private final Map<UUID, AuctionItem> listings = new ConcurrentHashMap<>();
    private BukkitTask expiryTask;
    private volatile boolean emergencyDisabled = false;

    public AuctionManager(FluxMarket plugin, AuctionDao dao) {
        this.plugin = plugin;
        this.dao = dao;
    }

    public void load() {
        listings.clear();
        List<AuctionItem> loaded = dao.loadAll();
        for (AuctionItem item : loaded) listings.put(item.getUuid(), item);
        plugin.getLogger().info("AuctionManager: loaded " + listings.size() + " listings.");
        emergencyDisabled = plugin.getConfigManager().isAhEmergencyDisable();
        expiryTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkExpiry, 600L, 600L);
    }

    public void stop() {
        if (expiryTask != null) expiryTask.cancel();
    }

    private void checkExpiry() {
        long now = System.currentTimeMillis();
        List<AuctionItem> expired = listings.values().stream()
                .filter(AuctionItem::isExpired).toList();

        for (AuctionItem item : expired) {
            listings.remove(item.getUuid());
            dao.delete(item.getUuid());

            if (item.isBid() && item.getHighestBidder() != null) {
                // Bid sold — item to winner, money to seller
                dao.addMailboxItem(item.getHighestBidder(),
                        ItemUtils.serialize(item.getItem()), 0, "Auction won");
                double net = item.getCurrentBid() * (1.0 - plugin.getConfigManager().getAhSaleTax());
                dao.addMailboxItem(item.getSellerUuid(), null, net, "Auction sold");

                // Record transaction
                String bidderName = item.getHighestBidderName() != null ? item.getHighestBidderName() : "Unknown";
                dao.recordTransaction(item.getHighestBidder().toString(), bidderName,
                        item.getSellerUuid().toString(), item.getSellerName(),
                        item.getItemDisplayName(), item.getCurrentBid(), "BID");

                // Discord webhook
                if (item.getCurrentBid() >= plugin.getConfigManager().getWebhookMinSaleAmount()) {
                    plugin.getWebhookManager().sendSale(item.getItemDisplayName(),
                            item.getCurrentBid(), bidderName, item.getSellerName());
                }

                notify(item.getHighestBidder(),
                        "&aYou won the auction for &f" + item.getItemDisplayName() + "! Check &e/ah &ato claim.");
                notify(item.getSellerUuid(),
                        "&aYour auction for &f" + item.getItemDisplayName() + " &awas sold for &f"
                        + FormatUtils.formatMoney(net) + "&a. Check &e/ah &ato claim.");
            } else {
                // No bids or expired — return item to seller
                dao.addMailboxItem(item.getSellerUuid(),
                        ItemUtils.serialize(item.getItem()), 0, "Auction expired");
                notify(item.getSellerUuid(),
                        "&eYour auction for &f" + item.getItemDisplayName() + " &eexpired — item returned.");
            }
        }
        dao.cleanupExpired(plugin.getConfigManager().getAhExpiredCleanupDays());
    }

    private void notify(UUID uuid, String message) {
        var player = Bukkit.getPlayer(uuid);
        if (player != null) player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix() + message));
    }

    /** List an item. Returns false if at limit or AH is disabled. */
    public boolean listItem(AuctionItem item) {
        if (emergencyDisabled) return false;
        long count = listings.values().stream()
                .filter(a -> a.getSellerUuid().equals(item.getSellerUuid())).count();
        if (count >= plugin.getConfigManager().getAhMaxListings()) return false;
        listings.put(item.getUuid(), item);
        dao.save(item);
        return true;
    }

    /** Cancel a listing. Only seller or admin. */
    public AuctionItem cancelListing(UUID uuid, UUID requester, boolean admin) {
        AuctionItem item = listings.get(uuid);
        if (item == null) return null;
        if (!admin && !item.getSellerUuid().equals(requester)) return null;
        listings.remove(uuid);
        dao.delete(uuid);
        if (item.isBid() && item.getHighestBidder() != null && item.getCurrentBid() > 0) {
            plugin.getEconomyProvider().deposit(
                    Bukkit.getOfflinePlayer(item.getHighestBidder()), item.getCurrentBid());
            notify(item.getHighestBidder(),
                    "&eYour bid for &f" + item.getItemDisplayName() + " &ewas refunded.");
        }
        return item;
    }

    /** Place a bid. */
    public BidResult placeBid(AuctionItem item, UUID bidder, String bidderName, double amount) {
        if (emergencyDisabled) return BidResult.EMERGENCY_DISABLED;
        if (!item.isBid()) return BidResult.NOT_BID_AUCTION;
        if (item.getSellerUuid().equals(bidder)) return BidResult.OWN_LISTING;
        if (amount <= item.getPrice()) return BidResult.TOO_LOW_STARTPRICE;
        if (amount <= item.getCurrentBid()) return BidResult.TOO_LOW;
        if (!plugin.getEconomyProvider().has(Bukkit.getOfflinePlayer(bidder), amount)) return BidResult.NO_MONEY;

        if (item.getHighestBidder() != null && item.getCurrentBid() > 0) {
            plugin.getEconomyProvider().deposit(
                    Bukkit.getOfflinePlayer(item.getHighestBidder()), item.getCurrentBid());
            notify(item.getHighestBidder(),
                    "&cYou were outbid on &f" + item.getItemDisplayName() + "!");
        }

        plugin.getEconomyProvider().withdraw(Bukkit.getOfflinePlayer(bidder), amount);
        item.placeBid(amount, bidder, bidderName);

        int antiSnipe = plugin.getConfigManager().getAhAntiSnipeSeconds();
        if (item.getRemainingMillis() < antiSnipe * 1000L) item.extendExpiry(antiSnipe * 1000L);

        dao.save(item);
        return BidResult.SUCCESS;
    }

    /** Buy BIN listing. */
    public boolean buyNow(AuctionItem item, UUID buyer, String buyerName) {
        if (emergencyDisabled) return false;
        if (item.isBid()) return false;
        if (item.getSellerUuid().equals(buyer)) return false;
        double price = item.getPrice();
        if (!plugin.getEconomyProvider().has(Bukkit.getOfflinePlayer(buyer), price)) return false;

        plugin.getEconomyProvider().withdraw(Bukkit.getOfflinePlayer(buyer), price);
        double afterTax = price * (1.0 - plugin.getConfigManager().getAhSaleTax());
        plugin.getEconomyProvider().deposit(Bukkit.getOfflinePlayer(item.getSellerUuid()), afterTax);

        listings.remove(item.getUuid());
        dao.delete(item.getUuid());

        // Record transaction
        dao.recordTransaction(buyer.toString(), buyerName,
                item.getSellerUuid().toString(), item.getSellerName(),
                item.getItemDisplayName(), price, "BIN");

        // Discord webhook
        if (price >= plugin.getConfigManager().getWebhookMinSaleAmount()) {
            plugin.getWebhookManager().sendSale(item.getItemDisplayName(), price, buyerName, item.getSellerName());
        }

        notify(item.getSellerUuid(),
                "&f" + item.getItemDisplayName() + " &asold to &f" + buyerName
                + " &afor &f" + FormatUtils.formatMoney(afterTax) + " &8(after tax)");
        return true;
    }

    // ─── Emergency Disable ────────────────────────────────────────────────────

    public boolean isEmergencyDisabled() { return emergencyDisabled; }

    public void setEmergencyDisabled(boolean disabled) {
        this.emergencyDisabled = disabled;
        plugin.getLogger().info("AH emergency disable: " + disabled);
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    public Collection<AuctionItem> getAll() { return listings.values(); }

    public List<AuctionItem> getByPlayer(UUID uuid) {
        return listings.values().stream()
                .filter(a -> a.getSellerUuid().equals(uuid)).collect(Collectors.toList());
    }

    public AuctionItem getById(UUID uuid) { return listings.get(uuid); }

    public int countByPlayer(UUID uuid) {
        return (int) listings.values().stream()
                .filter(a -> a.getSellerUuid().equals(uuid)).count();
    }

    public enum BidResult {
        SUCCESS, TOO_LOW, TOO_LOW_STARTPRICE, NO_MONEY, NOT_BID_AUCTION, OWN_LISTING, EMERGENCY_DISABLED
    }
}

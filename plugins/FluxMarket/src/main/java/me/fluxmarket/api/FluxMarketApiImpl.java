package me.fluxmarket.api;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.module.auction.AuctionItem;
import me.fluxmarket.module.orders.Order;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FluxMarketApiImpl implements FluxMarketApi {

    private final FluxMarket plugin;

    public FluxMarketApiImpl(FluxMarket plugin) {
        this.plugin = plugin;
    }

    // ── Prices ─────────────────────────────────────────────────────────────

    @Override
    public double getSellPrice(Material material, UUID playerUuid) {
        if (plugin.getShopModule() == null) return 0;
        var item = plugin.getShopModule().getRegistry().findItem(material.name());
        if (item == null) return 0;
        if (plugin.getFluxModule() != null) {
            return plugin.getFluxModule().getEngine()
                    .getSellPrice(material.name(), item.basePrice(), playerUuid.toString());
        }
        return item.basePrice();
    }

    @Override
    public double getBuyPrice(Material material, UUID playerUuid) {
        if (plugin.getShopModule() == null) return 0;
        var item = plugin.getShopModule().getRegistry().findItem(material.name());
        if (item == null) return 0;
        if (plugin.getFluxModule() != null) {
            return plugin.getFluxModule().getEngine()
                    .getBuyPrice(material.name(), item.basePrice(), playerUuid.toString());
        }
        return item.basePrice();
    }

    @Override
    public double getBasePrice(Material material) {
        if (plugin.getShopModule() == null) return 0;
        var item = plugin.getShopModule().getRegistry().findItem(material.name());
        return item != null ? item.basePrice() : 0;
    }

    // ── Auction House ──────────────────────────────────────────────────────

    @Override
    public List<AhListingInfo> getActiveListings() {
        if (plugin.getAuctionModule() == null) return List.of();
        List<AhListingInfo> result = new ArrayList<>();
        for (AuctionItem item : plugin.getAuctionModule().getManager().getActiveItems()) {
            result.add(toAhInfo(item));
        }
        return result;
    }

    @Override
    public List<AhListingInfo> getActiveListings(Material material) {
        if (plugin.getAuctionModule() == null) return List.of();
        List<AhListingInfo> result = new ArrayList<>();
        for (AuctionItem item : plugin.getAuctionModule().getManager().getActiveItems()) {
            if (item.getItem().getType() == material) result.add(toAhInfo(item));
        }
        return result;
    }

    @Override
    public int getActiveListingCount() {
        if (plugin.getAuctionModule() == null) return 0;
        return plugin.getAuctionModule().getManager().getActiveItems().size();
    }

    private AhListingInfo toAhInfo(AuctionItem item) {
        return new AhListingInfo(
                item.getItemDisplayName(),
                item.getItem().getType(),
                item.getItem().getAmount(),
                item.getPrice(),
                item.getSellerName(),
                item.isBid(),
                item.getExpiresAt()
        );
    }

    // ── Economy ────────────────────────────────────────────────────────────

    @Override
    public double getBalance(OfflinePlayer player) {
        return plugin.getEconomyProvider().getBalance(player);
    }

    @Override
    public double getSellMultiplier(UUID playerUuid) {
        double best = 1.0;
        String[] categories = {"blocks", "ores", "farming", "mob_drops", "nether", "misc"};
        for (String cat : categories) {
            double m = plugin.getSellProgressManager().getMultiplier(playerUuid, cat);
            if (m > best) best = m;
        }
        return best;
    }

    // ── Orders ─────────────────────────────────────────────────────────────

    @Override
    public List<OrderInfo> getActiveOrders(Material material) {
        if (plugin.getOrdersModule() == null) return List.of();
        List<OrderInfo> result = new ArrayList<>();
        for (Order order : plugin.getOrdersModule().getManager().getActiveOrders()) {
            if (order.getMaterial() == material) result.add(toOrderInfo(order));
        }
        return result;
    }

    @Override
    public List<OrderInfo> getAllActiveOrders() {
        if (plugin.getOrdersModule() == null) return List.of();
        List<OrderInfo> result = new ArrayList<>();
        for (Order order : plugin.getOrdersModule().getManager().getActiveOrders()) {
            result.add(toOrderInfo(order));
        }
        return result;
    }

    private OrderInfo toOrderInfo(Order order) {
        return new OrderInfo(
                order.getMaterial(),
                order.getAmountNeeded(),
                order.getAmountDelivered(),
                order.getPriceEach(),
                order.getCreatorName()
        );
    }

    // ── Version ────────────────────────────────────────────────────────────

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }
}

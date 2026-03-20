package me.fluxmarket.api;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.UUID;

/**
 * Public API for FluxMarket — allows other plugins to integrate with
 * the economy system without depending on internal classes.
 *
 * Obtain the instance via Bukkit's ServicesManager:
 * <pre>
 *   RegisteredServiceProvider<FluxMarketApi> rsp =
 *       Bukkit.getServicesManager().getRegistration(FluxMarketApi.class);
 *   if (rsp != null) FluxMarketApi api = rsp.getProvider();
 * </pre>
 */
public interface FluxMarketApi {

    // ── Prices ─────────────────────────────────────────────────────────────

    /** Current dynamic sell price for a material, personalized for a player. */
    double getSellPrice(Material material, UUID playerUuid);

    /** Current dynamic buy price for a material, personalized for a player. */
    double getBuyPrice(Material material, UUID playerUuid);

    /** Base price from config (no dynamic modifier). */
    double getBasePrice(Material material);

    // ── Auction House ──────────────────────────────────────────────────────

    /**
     * Summary of a single AH listing.
     *
     * @param itemName     display name of the item
     * @param material     the item's material
     * @param quantity     number of items in the listing
     * @param price        BIN price or start bid
     * @param sellerName   seller's player name
     * @param isBid        true if auction-style bid listing
     * @param expiresAt    unix timestamp in ms
     */
    record AhListingInfo(String itemName, Material material, int quantity,
                         double price, String sellerName, boolean isBid, long expiresAt) {}

    /** All currently active AH listings. */
    List<AhListingInfo> getActiveListings();

    /** Active AH listings filtered by material. */
    List<AhListingInfo> getActiveListings(Material material);

    /** Number of active AH listings. */
    int getActiveListingCount();

    // ── Economy ────────────────────────────────────────────────────────────

    /** Current balance of a player. */
    double getBalance(OfflinePlayer player);

    /** The player's current sell progression multiplier (best across all categories). */
    double getSellMultiplier(UUID playerUuid);

    // ── Orders ─────────────────────────────────────────────────────────────

    /**
     * Summary of a player order.
     *
     * @param material       what is wanted
     * @param amountNeeded   total needed
     * @param amountDelivered already delivered
     * @param priceEach      price per item
     * @param creatorName    player who placed the order
     */
    record OrderInfo(Material material, int amountNeeded, int amountDelivered,
                     double priceEach, String creatorName) {}

    /** All active orders for a specific material. */
    List<OrderInfo> getActiveOrders(Material material);

    /** All active orders. */
    List<OrderInfo> getAllActiveOrders();

    // ── Version ────────────────────────────────────────────────────────────

    /** Plugin version string. */
    String getVersion();
}

package me.fluxmarket.module.dashboard;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

public class DashboardGui implements FluxGui {

    private static final int SLOT_HEADER         = 4;
    private static final int SLOT_TRANSACTIONS   = 10;
    private static final int SLOT_AH_VOLUME      = 12;
    private static final int SLOT_ACTIVE_LISTINGS = 14;
    private static final int SLOT_ACTIVE_ORDERS  = 16;
    private static final int SLOT_TOP_SELLER_DAY = 19;
    private static final int SLOT_TOP_AH_SELLER  = 21;
    private static final int SLOT_AVG_SELL       = 28;
    private static final int SLOT_SELL_24H       = 30;
    private static final int SLOT_CLOSE          = 49;

    private final FluxMarket plugin;
    private final Player player;
    private final Inventory inventory;

    /** Called from main thread after async data load. */
    private DashboardGui(FluxMarket plugin, Player player, DashboardData data) {
        this.plugin = plugin;
        this.player = player;
        inventory = Bukkit.createInventory(null, 54, FormatUtils.comp("&8» &6Economy Dashboard"));
        populate(data);
    }

    /**
     * Static factory: runs DB queries async, then opens the GUI on the main thread.
     */
    public static void openAsync(FluxMarket plugin, Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            DashboardData data = loadData(plugin);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                DashboardGui gui = new DashboardGui(plugin, player, data);
                gui.open();
            });
        });
    }

    private void open() {
        player.openInventory(inventory);
        GuiListener.open(player.getUniqueId(), this);
    }

    private void populate(DashboardData d) {
        // Header
        inventory.setItem(SLOT_HEADER, ItemUtils.named(Material.PLAYER_HEAD,
                "&6Economy Dashboard",
                "&7Live server economy stats"));

        // Total Transactions
        inventory.setItem(SLOT_TRANSACTIONS, ItemUtils.named(Material.GOLD_INGOT,
                "&eTotal Transactions",
                "&7Count: &f" + d.sellTxCount(),
                "&7Volume: &a" + FormatUtils.formatMoney(d.sellTxVolume())));

        // AH Volume
        inventory.setItem(SLOT_AH_VOLUME, ItemUtils.named(Material.EMERALD,
                "&eAH Volume",
                "&7Total AH sales value:",
                "&a" + FormatUtils.formatMoney(d.ahVolume())));

        // Active Listings
        inventory.setItem(SLOT_ACTIVE_LISTINGS, ItemUtils.named(Material.BOOK,
                "&eActive Listings",
                "&7Currently listed in AH:",
                "&f" + d.activeListings()));

        // Active Orders
        inventory.setItem(SLOT_ACTIVE_ORDERS, ItemUtils.named(Material.DIAMOND,
                "&eActive Orders",
                "&7Open buy orders:",
                "&f" + d.activeOrders()));

        // Top Seller Today
        String topSellerDay = d.topSellerToday().isEmpty() ? "&8No data" : "&f" + d.topSellerToday();
        inventory.setItem(SLOT_TOP_SELLER_DAY, ItemUtils.named(Material.IRON_INGOT,
                "&eTop Seller (Today)",
                "&7Player with most earnings today:",
                topSellerDay,
                "&7Earned: &a" + FormatUtils.formatMoney(d.topSellerTodayAmount())));

        // Top AH Seller
        String topAhSeller = d.topAhSeller().isEmpty() ? "&8No data" : "&f" + d.topAhSeller();
        inventory.setItem(SLOT_TOP_AH_SELLER, ItemUtils.named(Material.CHEST,
                "&eTop AH Seller",
                "&7Player with most AH sales:",
                topAhSeller,
                "&7Volume: &a" + FormatUtils.formatMoney(d.topAhSellerAmount())));

        // Avg Sell Price
        inventory.setItem(SLOT_AVG_SELL, ItemUtils.named(Material.PAPER,
                "&eAvg Sell Price",
                "&7Average earnings per sell transaction:",
                "&a" + FormatUtils.formatMoney(d.avgSellPrice())));

        // Sell Volume 24h
        inventory.setItem(SLOT_SELL_24H, ItemUtils.named(Material.CLOCK,
                "&eSell Volume (24h)",
                "&7Total sell earnings last 24 hours:",
                "&a" + FormatUtils.formatMoney(d.sellVolume24h())));

        // Close
        inventory.setItem(SLOT_CLOSE, ItemUtils.named(Material.BARRIER, "&cClose"));
    }

    // ─── DB Query ─────────────────────────────────────────────────────────────

    private static DashboardData loadData(FluxMarket plugin) {
        long sellTxCount = 0;
        double sellTxVolume = 0;
        double ahVolume = 0;
        long activeListings = 0;
        long activeOrders = 0;
        String topSellerToday = "";
        double topSellerTodayAmount = 0;
        String topAhSeller = "";
        double topAhSellerAmount = 0;
        double avgSellPrice = 0;
        double sellVolume24h = 0;

        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            long now = System.currentTimeMillis();
            long dayStart = now - 86_400_000L; // last 24h

            // Sell: count + volume
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*), COALESCE(SUM(total_price), 0) FROM sell_history")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    sellTxCount = rs.getLong(1);
                    sellTxVolume = rs.getDouble(2);
                }
            }

            // AH Volume (from auction_transactions)
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(price), 0) FROM auction_transactions")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) ahVolume = rs.getDouble(1);
            }

            // Active listings
            if (plugin.getAuctionModule() != null) {
                activeListings = plugin.getAuctionModule().getManager().getAll().size();
            }

            // Active orders
            if (plugin.getOrdersModule() != null) {
                activeOrders = plugin.getOrdersModule().getManager().getActiveOrders().size();
            }

            // Top seller today (by sum of total_price in last 24h)
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT player_uuid, COALESCE(SUM(total_price), 0) as earned " +
                    "FROM sell_history WHERE timestamp >= ? " +
                    "GROUP BY player_uuid ORDER BY earned DESC LIMIT 1")) {
                ps.setLong(1, dayStart);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    topSellerTodayAmount = rs.getDouble("earned");
                    String uuid = rs.getString("player_uuid");
                    // Resolve name from sell_top if possible
                    topSellerToday = resolvePlayerName(conn, uuid);
                }
            }

            // Top AH seller (all time, from auction_transactions)
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT seller_name, COALESCE(SUM(price), 0) as vol " +
                    "FROM auction_transactions GROUP BY seller_uuid ORDER BY vol DESC LIMIT 1")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    topAhSeller = rs.getString("seller_name");
                    topAhSellerAmount = rs.getDouble("vol");
                }
            }

            // Avg sell price per transaction
            if (sellTxCount > 0) avgSellPrice = sellTxVolume / sellTxCount;

            // Sell volume last 24h
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(total_price), 0) FROM sell_history WHERE timestamp >= ?")) {
                ps.setLong(1, dayStart);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) sellVolume24h = rs.getDouble(1);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "DashboardGui data load error", e);
        }

        return new DashboardData(
                sellTxCount, sellTxVolume, ahVolume,
                activeListings, activeOrders,
                topSellerToday, topSellerTodayAmount,
                topAhSeller, topAhSellerAmount,
                avgSellPrice, sellVolume24h);
    }

    private static String resolvePlayerName(Connection conn, String uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT player_name FROM sell_top WHERE player_uuid = ?")) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("player_name");
        } catch (SQLException ignored) {}
        // Fallback: try Bukkit offline player
        try {
            var op = Bukkit.getOfflinePlayer(java.util.UUID.fromString(uuid));
            if (op.getName() != null) return op.getName();
        } catch (Exception ignored) {}
        return uuid;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getSlot() == SLOT_CLOSE) {
            player.closeInventory();
        }
    }

    // ─── Data record ──────────────────────────────────────────────────────────

    private record DashboardData(
            long sellTxCount,
            double sellTxVolume,
            double ahVolume,
            long activeListings,
            long activeOrders,
            String topSellerToday,
            double topSellerTodayAmount,
            String topAhSeller,
            double topAhSellerAmount,
            double avgSellPrice,
            double sellVolume24h
    ) {}
}

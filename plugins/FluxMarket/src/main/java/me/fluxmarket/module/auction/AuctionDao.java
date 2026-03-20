package me.fluxmarket.module.auction;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.util.ItemUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class AuctionDao {

    private final FluxMarket plugin;

    public AuctionDao(FluxMarket plugin) {
        this.plugin = plugin;
    }

    public void save(AuctionItem item) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                    "REPLACE INTO auction_listings (uuid, seller_uuid, seller_name, item_data, item_display_name, " +
                    "price, is_bid, current_bid, highest_bidder, highest_bidder_name, listed_at, expires_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, item.getUuid().toString());
                ps.setString(2, item.getSellerUuid().toString());
                ps.setString(3, item.getSellerName());
                ps.setBytes(4, ItemUtils.serialize(item.getItem()));
                ps.setString(5, item.getItemDisplayName());
                ps.setDouble(6, item.getPrice());
                ps.setBoolean(7, item.isBid());
                ps.setDouble(8, item.getCurrentBid());
                ps.setString(9, item.getHighestBidder() != null ? item.getHighestBidder().toString() : null);
                ps.setString(10, item.getHighestBidderName());
                ps.setLong(11, item.getListedAt());
                ps.setLong(12, item.getExpiresAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "AuctionDao save error", e);
            }
        });
    }

    public void delete(UUID uuid) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                    "DELETE FROM auction_listings WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("AuctionDao delete error: " + e.getMessage());
            }
        });
    }

    public List<AuctionItem> loadAll() {
        List<AuctionItem> items = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT * FROM auction_listings")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                AuctionItem item = fromResultSet(rs);
                if (item != null) items.add(item);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "AuctionDao load error", e);
        }
        return items;
    }

    public List<AuctionItem> loadByPlayer(UUID player) {
        List<AuctionItem> items = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT * FROM auction_listings WHERE seller_uuid = ?")) {
            ps.setString(1, player.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                AuctionItem item = fromResultSet(rs);
                if (item != null) items.add(item);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("AuctionDao loadByPlayer error: " + e.getMessage());
        }
        return items;
    }

    // --- Mailbox ---

    public void addMailboxItem(UUID player, byte[] itemData, double money, String reason) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                    "INSERT INTO auction_mailbox (player_uuid, item_data, money, reason, timestamp) VALUES (?,?,?,?,?)")) {
                ps.setString(1, player.toString());
                ps.setBytes(2, itemData);
                ps.setDouble(3, money);
                ps.setString(4, reason);
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Mailbox insert error: " + e.getMessage());
            }
        });
    }

    public List<MailboxEntry> getMailbox(UUID player) {
        List<MailboxEntry> entries = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT id, item_data, money, reason FROM auction_mailbox WHERE player_uuid = ?")) {
            ps.setString(1, player.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                entries.add(new MailboxEntry(
                        rs.getInt("id"),
                        rs.getBytes("item_data"),
                        rs.getDouble("money"),
                        rs.getString("reason")));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Mailbox load error: " + e.getMessage());
        }
        return entries;
    }

    public void deleteMailboxEntry(int id) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                    "DELETE FROM auction_mailbox WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        });
    }

    public int countMailbox(UUID player) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT COUNT(*) FROM auction_mailbox WHERE player_uuid = ?")) {
            ps.setString(1, player.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException ignored) {}
        return 0;
    }

    public void cleanupExpired(int days) {
        long cutoff = System.currentTimeMillis() - (long) days * 86_400_000L;
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "DELETE FROM auction_mailbox WHERE timestamp < ?")) {
            ps.setLong(1, cutoff);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private AuctionItem fromResultSet(ResultSet rs) throws SQLException {
        try {
            byte[] data = rs.getBytes("item_data");
            var item = ItemUtils.deserialize(data);
            if (item.getType() == org.bukkit.Material.AIR) return null;

            String bidderStr = rs.getString("highest_bidder");
            UUID bidder = bidderStr != null ? UUID.fromString(bidderStr) : null;

            return new AuctionItem(
                    UUID.fromString(rs.getString("uuid")),
                    UUID.fromString(rs.getString("seller_uuid")),
                    rs.getString("seller_name"),
                    item,
                    rs.getString("item_display_name"),
                    rs.getDouble("price"),
                    rs.getBoolean("is_bid"),
                    rs.getDouble("current_bid"),
                    bidder,
                    rs.getString("highest_bidder_name"),
                    rs.getLong("listed_at"),
                    rs.getLong("expires_at"));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to deserialize auction item: " + e.getMessage());
            return null;
        }
    }

    public record MailboxEntry(int id, byte[] itemData, double money, String reason) {}

    // ─── Transaction History ─────────────────────────────────────────────────

    public void recordTransaction(String buyerUuid, String buyerName,
                                   String sellerUuid, String sellerName,
                                   String itemDisplayName, double price, String type) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                    "INSERT INTO auction_transactions (buyer_uuid, buyer_name, seller_uuid, seller_name, " +
                    "item_display_name, price, type, timestamp) VALUES (?,?,?,?,?,?,?,?)")) {
                ps.setString(1, buyerUuid);
                ps.setString(2, buyerName);
                ps.setString(3, sellerUuid);
                ps.setString(4, sellerName);
                ps.setString(5, itemDisplayName);
                ps.setDouble(6, price);
                ps.setString(7, type);
                ps.setLong(8, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("AH transaction record error: " + e.getMessage());
            }
        });
    }

    /** Load transactions for a player (as buyer OR seller). Blocking — call async. */
    public List<TransactionEntry> getTransactions(UUID playerUuid, int limit) {
        List<TransactionEntry> result = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT buyer_uuid, buyer_name, seller_uuid, seller_name, item_display_name, price, type, timestamp " +
                "FROM auction_transactions WHERE buyer_uuid = ? OR seller_uuid = ? ORDER BY timestamp DESC LIMIT ?")) {
            String uuid = playerUuid.toString();
            ps.setString(1, uuid);
            ps.setString(2, uuid);
            ps.setInt(3, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new TransactionEntry(
                        rs.getString("buyer_uuid"), rs.getString("buyer_name"),
                        rs.getString("seller_uuid"), rs.getString("seller_name"),
                        rs.getString("item_display_name"), rs.getDouble("price"),
                        rs.getString("type"), rs.getLong("timestamp")));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("AH transactions load error: " + e.getMessage());
        }
        return result;
    }

    /** Load aggregate stats for a player. Blocking — call async. */
    public TransactionStats getStats(UUID playerUuid) {
        String uuid = playerUuid.toString();
        double totalSpent = 0, totalEarned = 0;
        int bought = 0, sold = 0;
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT SUM(price), COUNT(*) FROM auction_transactions WHERE buyer_uuid = ?")) {
                ps.setString(1, uuid);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) { totalSpent = rs.getDouble(1); bought = rs.getInt(2); }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT SUM(price), COUNT(*) FROM auction_transactions WHERE seller_uuid = ?")) {
                ps.setString(1, uuid);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) { totalEarned = rs.getDouble(1); sold = rs.getInt(2); }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("AH stats error: " + e.getMessage());
        }
        return new TransactionStats(totalSpent, totalEarned, bought, sold);
    }

    public record TransactionEntry(String buyerUuid, String buyerName,
                                   String sellerUuid, String sellerName,
                                   String itemDisplayName, double price,
                                   String type, long timestamp) {}

    public record TransactionStats(double totalSpent, double totalEarned, int bought, int sold) {}
}

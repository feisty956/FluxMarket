package me.fluxmarket.module.sell;

import me.fluxmarket.FluxMarket;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SellDao {

    private final FluxMarket plugin;

    public SellDao(FluxMarket plugin) {
        this.plugin = plugin;
    }

    public void insertHistory(UUID player, String material, int amount, double totalPrice) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                    "INSERT INTO sell_history (player_uuid, material, amount, total_price, timestamp) VALUES (?,?,?,?,?)")) {
                ps.setString(1, player.toString());
                ps.setString(2, material);
                ps.setInt(3, amount);
                ps.setDouble(4, totalPrice);
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("SellDao insert error: " + e.getMessage());
            }
        });
    }

    public List<SellEntry> getHistory(UUID player, int limit) {
        List<SellEntry> entries = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT material, amount, total_price, timestamp FROM sell_history WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ?")) {
            ps.setString(1, player.toString());
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                entries.add(new SellEntry(
                        rs.getString("material"),
                        rs.getInt("amount"),
                        rs.getDouble("total_price"),
                        rs.getLong("timestamp")));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("SellDao history error: " + e.getMessage());
        }
        return entries;
    }

    public record SellEntry(String material, int amount, double totalPrice, long timestamp) {}

    public List<SellTopEntry> getSellTop(int limit) {
        List<SellTopEntry> entries = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT player_name, total_earned FROM sell_top ORDER BY total_earned DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            int rank = 1;
            while (rs.next()) {
                entries.add(new SellTopEntry(rank++, rs.getString("player_name"), rs.getDouble("total_earned")));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("SellDao getSellTop error: " + e.getMessage());
        }
        return entries;
    }

    public void upsertSellTop(UUID playerUuid, String playerName, double earned) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean mysql = plugin.getDatabaseManager().isMySQL();
                String sql = mysql
                        ? "INSERT INTO sell_top (player_uuid, player_name, total_earned, updated_at) VALUES (?,?,?,?) " +
                          "ON DUPLICATE KEY UPDATE player_name=VALUES(player_name), " +
                          "total_earned=total_earned+VALUES(total_earned), updated_at=VALUES(updated_at)"
                        : "INSERT INTO sell_top (player_uuid, player_name, total_earned, updated_at) VALUES (?,?,?,?) " +
                          "ON CONFLICT(player_uuid) DO UPDATE SET player_name=excluded.player_name, " +
                          "total_earned=total_earned+excluded.total_earned, updated_at=excluded.updated_at";
                try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, playerName);
                    ps.setDouble(3, earned);
                    ps.setLong(4, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("SellDao upsertSellTop error: " + e.getMessage());
            }
        });
    }

    public record SellTopEntry(int rank, String playerName, double totalEarned) {}

    // ─── Sell Progression ────────────────────────────────────────────────────

    public java.util.Map<String, Double> loadProgress(UUID playerUuid) {
        java.util.Map<String, Double> result = new java.util.LinkedHashMap<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT category, total_earned FROM sell_progress WHERE player_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.put(rs.getString("category"), rs.getDouble("total_earned"));
        } catch (SQLException e) {
            plugin.getLogger().warning("Progress load error: " + e.getMessage());
        }
        return result;
    }

    public void upsertProgress(UUID playerUuid, String category, double earnedDelta) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean mysql = plugin.getDatabaseManager().isMySQL();
                String sql = mysql
                        ? "INSERT INTO sell_progress (player_uuid, category, total_earned) VALUES (?,?,?) " +
                          "ON DUPLICATE KEY UPDATE total_earned=total_earned+VALUES(total_earned)"
                        : "INSERT INTO sell_progress (player_uuid, category, total_earned) VALUES (?,?,?) " +
                          "ON CONFLICT(player_uuid, category) DO UPDATE SET total_earned=total_earned+excluded.total_earned";
                try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, category);
                    ps.setDouble(3, earnedDelta);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Progress upsert error: " + e.getMessage());
            }
        });
    }
}

package me.fluxmarket.module.profit;

import me.fluxmarket.database.DatabaseManager;
import org.bukkit.Material;

import java.sql.*;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProfitDao {

    private final DatabaseManager db;
    private final Logger logger;

    public ProfitDao(DatabaseManager db) {
        this.db = db;
        this.logger = Logger.getLogger("FluxMarket");
    }

    public void createTable() {
        String auto = db.isMySQL() ? "AUTO_INCREMENT" : "AUTOINCREMENT";
        try (Statement st = db.getConnection().createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS flux_profit_log (
                    id INTEGER PRIMARY KEY %s,
                    player_uuid VARCHAR(36) NOT NULL,
                    material VARCHAR(64) NOT NULL,
                    buy_price DOUBLE NOT NULL,
                    sell_price DOUBLE NOT NULL,
                    quantity INTEGER NOT NULL,
                    timestamp BIGINT NOT NULL
                )""".formatted(auto));
            try {
                st.execute("CREATE INDEX IF NOT EXISTS idx_profit_log_player ON flux_profit_log(player_uuid, timestamp)");
            } catch (SQLException ignored) {}
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create flux_profit_log table", e);
        }
    }

    /**
     * Insert a profit log entry.
     *
     * @param player    the player's UUID
     * @param material  the material sold
     * @param buyPrice  the buy price per unit (0 if unknown / items from inventory)
     * @param sellPrice the sell price per unit earned
     * @param qty       quantity sold
     * @param timestamp epoch millis
     */
    public void addEntry(UUID player, Material material, double buyPrice, double sellPrice,
                         int qty, long timestamp) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT INTO flux_profit_log (player_uuid, material, buy_price, sell_price, quantity, timestamp)"
                        + " VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, player.toString());
            ps.setString(2, material.name());
            ps.setDouble(3, buyPrice);
            ps.setDouble(4, sellPrice);
            ps.setInt(5, qty);
            ps.setLong(6, timestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to add profit entry", e);
        }
    }

    /**
     * Calculate total profit for a player since the given timestamp.
     * Profit = SUM((sell_price - buy_price) * quantity)
     */
    public double getPlayerProfit(UUID player, long since) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT SUM((sell_price - buy_price) * quantity) FROM flux_profit_log"
                        + " WHERE player_uuid = ? AND timestamp >= ?")) {
            ps.setString(1, player.toString());
            ps.setLong(2, since);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double val = rs.getDouble(1);
                    return rs.wasNull() ? 0.0 : val;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to get player profit for " + player, e);
        }
        return 0.0;
    }

    /**
     * Get top-N players by total profit since the given timestamp.
     * Returns a list of (playerUuid, totalProfit) pairs sorted descending.
     */
    public List<Map.Entry<UUID, Double>> getTopProfits(int limit, long since) {
        List<Map.Entry<UUID, Double>> result = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT player_uuid, SUM((sell_price - buy_price) * quantity) AS total_profit"
                        + " FROM flux_profit_log WHERE timestamp >= ?"
                        + " GROUP BY player_uuid ORDER BY total_profit DESC LIMIT ?")) {
            ps.setLong(1, since);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                        double profit = rs.getDouble("total_profit");
                        result.add(new AbstractMap.SimpleEntry<>(uuid, profit));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to get top profits", e);
        }
        return result;
    }
}

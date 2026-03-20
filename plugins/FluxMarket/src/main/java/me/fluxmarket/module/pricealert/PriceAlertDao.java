package me.fluxmarket.module.pricealert;

import me.fluxmarket.database.DatabaseManager;
import org.bukkit.Material;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PriceAlertDao {

    private final DatabaseManager db;
    private final Logger logger;

    public PriceAlertDao(DatabaseManager db) {
        this.db = db;
        this.logger = Logger.getLogger("FluxMarket");
    }

    public void createTable() {
        String auto = db.isMySQL() ? "AUTO_INCREMENT" : "AUTOINCREMENT";
        try (Statement st = db.getConnection().createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS flux_price_alerts (
                    id INTEGER PRIMARY KEY %s,
                    player_uuid VARCHAR(36) NOT NULL,
                    material VARCHAR(64) NOT NULL,
                    target_price DOUBLE NOT NULL,
                    created BIGINT NOT NULL
                )""".formatted(auto));
            try {
                st.execute("CREATE INDEX IF NOT EXISTS idx_price_alerts_player ON flux_price_alerts(player_uuid)");
            } catch (SQLException ignored) {}
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create flux_price_alerts table", e);
        }
    }

    public void addAlert(UUID player, Material material, double targetPrice) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT INTO flux_price_alerts (player_uuid, material, target_price, created) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, player.toString());
            ps.setString(2, material.name());
            ps.setDouble(3, targetPrice);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to add price alert", e);
        }
    }

    public void removeAlert(UUID player, Material material) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM flux_price_alerts WHERE player_uuid = ? AND material = ?")) {
            ps.setString(1, player.toString());
            ps.setString(2, material.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to remove price alert", e);
        }
    }

    public List<PriceAlert> getAlerts(UUID player) {
        List<PriceAlert> list = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT material, target_price FROM flux_price_alerts WHERE player_uuid = ?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        Material mat = Material.valueOf(rs.getString("material"));
                        list.add(new PriceAlert(player, mat, rs.getDouble("target_price")));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to get alerts for player " + player, e);
        }
        return list;
    }

    public List<PriceAlert> getAllAlerts() {
        List<PriceAlert> list = new ArrayList<>();
        try (Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT player_uuid, material, target_price FROM flux_price_alerts")) {
            while (rs.next()) {
                try {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    Material mat = Material.valueOf(rs.getString("material"));
                    list.add(new PriceAlert(playerUuid, mat, rs.getDouble("target_price")));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to load all price alerts", e);
        }
        return list;
    }
}

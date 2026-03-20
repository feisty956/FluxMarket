package me.fluxmarket.module.treasury;

import me.fluxmarket.database.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TreasuryDao {

    private final DatabaseManager db;
    private final Logger logger;

    public TreasuryDao(DatabaseManager db) {
        this.db = db;
        this.logger = Logger.getLogger("FluxMarket");
    }

    public void createTable() {
        String auto = db.isMySQL() ? "AUTO_INCREMENT" : "AUTOINCREMENT";
        String sql = "CREATE TABLE IF NOT EXISTS flux_treasury (" +
                "id INTEGER PRIMARY KEY " + auto + ", " +
                "source VARCHAR(32) NOT NULL, " +
                "amount DOUBLE NOT NULL, " +
                "timestamp BIGINT NOT NULL" +
                ")";
        try (Statement st = db.getConnection().createStatement()) {
            st.execute(sql);
            try {
                st.execute("CREATE INDEX IF NOT EXISTS idx_treasury_source ON flux_treasury(source)");
            } catch (SQLException ignored) {}
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create flux_treasury table", e);
        }
    }

    /** INSERT a new treasury entry. Meant to be called async. */
    public void addEntry(String source, double amount) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT INTO flux_treasury (source, amount, timestamp) VALUES (?,?,?)")) {
            ps.setString(1, source);
            ps.setDouble(2, amount);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "TreasuryDao addEntry error", e);
        }
    }

    /** Sum of all amounts for a given source. Blocking — call async. */
    public double getTotalBySource(String source) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT COALESCE(SUM(amount), 0) FROM flux_treasury WHERE source = ?")) {
            ps.setString(1, source);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "TreasuryDao getTotalBySource error", e);
        }
        return 0.0;
    }

    /** Grand total across all sources. Blocking — call async. */
    public double getGrandTotal() {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT COALESCE(SUM(amount), 0) FROM flux_treasury")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "TreasuryDao getGrandTotal error", e);
        }
        return 0.0;
    }

    /** Most recent N entries, newest first. Blocking — call async. */
    public List<TreasuryEntry> getRecentEntries(int limit) {
        List<TreasuryEntry> entries = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT source, amount, timestamp FROM flux_treasury ORDER BY timestamp DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                entries.add(new TreasuryEntry(
                        rs.getString("source"),
                        rs.getDouble("amount"),
                        rs.getLong("timestamp")));
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "TreasuryDao getRecentEntries error", e);
        }
        return entries;
    }

    public record TreasuryEntry(String source, double amount, long timestamp) {}
}

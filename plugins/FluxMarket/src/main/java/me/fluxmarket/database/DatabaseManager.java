package me.fluxmarket.database;

import me.fluxmarket.FluxMarket;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;

public class DatabaseManager {

    private final FluxMarket plugin;
    private Connection connection;
    private final boolean mysql;

    public DatabaseManager(FluxMarket plugin) {
        this.plugin = plugin;
        this.mysql = plugin.getConfigManager().getDbType().equalsIgnoreCase("mysql");
    }

    public boolean initialize() {
        try {
            openConnection();
            createTables();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Database init failed", e);
            return false;
        }
    }

    private void openConnection() throws SQLException, ClassNotFoundException {
        if (mysql) {
            Class.forName("com.mysql.cj.jdbc.Driver");
            var cfg = plugin.getConfigManager();
            String url = "jdbc:mysql://" + cfg.getMysqlHost() + ":" + cfg.getMysqlPort()
                    + "/" + cfg.getMysqlDatabase()
                    + "?useSSL=" + cfg.getMysqlUseSSL()
                    + "&allowPublicKeyRetrieval=" + cfg.getMysqlAllowPublicKeyRetrieval()
                    + "&autoReconnect=true";
            connection = DriverManager.getConnection(url, cfg.getMysqlUsername(), cfg.getMysqlPassword());
        } else {
            Class.forName("org.sqlite.JDBC");
            File dbFile = new File(plugin.getDataFolder(), "fluxmarket.db");
            plugin.getDataFolder().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            // Enable WAL for better concurrent read performance
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
            }
        }
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                openConnection();
            } catch (ClassNotFoundException e) {
                throw new SQLException("JDBC driver not found", e);
            }
        }
        return connection;
    }

    private void createTables() throws SQLException {
        String auto = mysql ? "AUTO_INCREMENT" : "AUTOINCREMENT";
        try (Statement st = getConnection().createStatement()) {
            // FLUX tables
            st.execute("""
                CREATE TABLE IF NOT EXISTS flux_items (
                    material VARCHAR(64) PRIMARY KEY,
                    base_price DOUBLE NOT NULL,
                    max_swing DOUBLE,
                    sensitivity DOUBLE,
                    category VARCHAR(64)
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS flux_transactions (
                    id INTEGER PRIMARY KEY %s,
                    player_uuid VARCHAR(36) NOT NULL,
                    material VARCHAR(64) NOT NULL,
                    amount INTEGER NOT NULL,
                    type VARCHAR(4) NOT NULL,
                    price DOUBLE NOT NULL,
                    timestamp BIGINT NOT NULL
                )""".formatted(auto));

            st.execute("""
                CREATE TABLE IF NOT EXISTS flux_price_history (
                    id INTEGER PRIMARY KEY %s,
                    material VARCHAR(64) NOT NULL,
                    price DOUBLE NOT NULL,
                    timestamp BIGINT NOT NULL
                )""".formatted(auto));

            // SELL tables
            st.execute("""
                CREATE TABLE IF NOT EXISTS sell_history (
                    id INTEGER PRIMARY KEY %s,
                    player_uuid VARCHAR(36) NOT NULL,
                    material VARCHAR(64) NOT NULL,
                    amount INTEGER NOT NULL,
                    total_price DOUBLE NOT NULL,
                    timestamp BIGINT NOT NULL
                )""".formatted(auto));

            // AUCTION tables
            st.execute("""
                CREATE TABLE IF NOT EXISTS auction_listings (
                    uuid VARCHAR(36) PRIMARY KEY,
                    seller_uuid VARCHAR(36) NOT NULL,
                    seller_name VARCHAR(16) NOT NULL,
                    item_data BLOB NOT NULL,
                    item_display_name VARCHAR(128),
                    price DOUBLE NOT NULL,
                    is_bid BOOLEAN NOT NULL DEFAULT 0,
                    current_bid DOUBLE NOT NULL DEFAULT 0,
                    highest_bidder VARCHAR(36),
                    highest_bidder_name VARCHAR(16),
                    listed_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS auction_mailbox (
                    id INTEGER PRIMARY KEY %s,
                    player_uuid VARCHAR(36) NOT NULL,
                    item_data BLOB,
                    money DOUBLE NOT NULL DEFAULT 0,
                    reason VARCHAR(64) NOT NULL,
                    timestamp BIGINT NOT NULL
                )""".formatted(auto));

            // ORDERS tables
            st.execute("""
                CREATE TABLE IF NOT EXISTS orders (
                    uuid VARCHAR(36) PRIMARY KEY,
                    creator_uuid VARCHAR(36) NOT NULL,
                    creator_name VARCHAR(16) NOT NULL,
                    material VARCHAR(64) NOT NULL,
                    amount_needed INTEGER NOT NULL,
                    amount_delivered INTEGER NOT NULL DEFAULT 0,
                    price_each DOUBLE NOT NULL,
                    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                    created_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL
                )""");

            // Items waiting to be claimed by the order creator
            st.execute("""
                CREATE TABLE IF NOT EXISTS order_mailbox (
                    id INTEGER PRIMARY KEY %s,
                    order_uuid VARCHAR(36) NOT NULL,
                    creator_uuid VARCHAR(36) NOT NULL,
                    item_data BLOB NOT NULL,
                    delivered_at BIGINT NOT NULL
                )""".formatted(auto));

            // Indexes for performance
            executeIgnoreError(st, "CREATE INDEX IF NOT EXISTS idx_flux_tx_material ON flux_transactions(material, timestamp)");
            executeIgnoreError(st, "CREATE INDEX IF NOT EXISTS idx_flux_history_material ON flux_price_history(material, timestamp)");
            executeIgnoreError(st, "CREATE INDEX IF NOT EXISTS idx_sell_player ON sell_history(player_uuid, timestamp)");
            executeIgnoreError(st, "CREATE INDEX IF NOT EXISTS idx_auction_expires ON auction_listings(expires_at)");
            executeIgnoreError(st, "CREATE INDEX IF NOT EXISTS idx_mailbox_player ON auction_mailbox(player_uuid)");
            executeIgnoreError(st, "CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status, expires_at)");
            executeIgnoreError(st, "CREATE INDEX IF NOT EXISTS idx_order_mailbox_creator ON order_mailbox(creator_uuid)");

            // SELLTOP — cumulative all-time earnings per player
            st.execute("""
                CREATE TABLE IF NOT EXISTS sell_top (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(16) NOT NULL,
                    total_earned DOUBLE NOT NULL DEFAULT 0,
                    updated_at BIGINT NOT NULL
                )""");

            // SELL PROGRESSION — per-player per-category earnings for tier system
            st.execute("""
                CREATE TABLE IF NOT EXISTS sell_progress (
                    player_uuid VARCHAR(36) NOT NULL,
                    category VARCHAR(32) NOT NULL,
                    total_earned DOUBLE NOT NULL DEFAULT 0,
                    PRIMARY KEY (player_uuid, category)
                )""");

            // SELL WANDS
            st.execute("""
                CREATE TABLE IF NOT EXISTS sell_wands (
                    uuid VARCHAR(36) PRIMARY KEY,
                    owner_uuid VARCHAR(36) NOT NULL,
                    owner_name VARCHAR(16) NOT NULL,
                    wand_type VARCHAR(4) NOT NULL,
                    uses_remaining INTEGER NOT NULL DEFAULT 0,
                    expires_at BIGINT NOT NULL DEFAULT 0,
                    created_at BIGINT NOT NULL
                )""");

            // AUCTION TRANSACTION HISTORY
            st.execute("""
                CREATE TABLE IF NOT EXISTS auction_transactions (
                    id INTEGER PRIMARY KEY %s,
                    buyer_uuid VARCHAR(36) NOT NULL,
                    buyer_name VARCHAR(16) NOT NULL,
                    seller_uuid VARCHAR(36) NOT NULL,
                    seller_name VARCHAR(16) NOT NULL,
                    item_display_name VARCHAR(128) NOT NULL,
                    price DOUBLE NOT NULL,
                    type VARCHAR(4) NOT NULL,
                    timestamp BIGINT NOT NULL
                )""".formatted(auto));

            executeIgnoreError(st, "CREATE INDEX IF NOT EXISTS idx_sell_top_earned ON sell_top(total_earned DESC)");
            executeIgnoreError(st, "CREATE INDEX IF NOT EXISTS idx_auction_tx_buyer ON auction_transactions(buyer_uuid, timestamp)");
            executeIgnoreError(st, "CREATE INDEX IF NOT EXISTS idx_auction_tx_seller ON auction_transactions(seller_uuid, timestamp)");
        }
    }

    private void executeIgnoreError(Statement st, String sql) {
        try {
            st.execute(sql);
        } catch (SQLException ignored) {}
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error closing database connection", e);
        }
    }

    public boolean isMySQL() { return mysql; }
}

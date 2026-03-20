package me.fluxmarket.module.playershop;

import me.fluxmarket.database.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlayerShopDao {

    private final DatabaseManager db;
    private final Logger logger;
    private final String auto;

    public PlayerShopDao(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
        this.auto = db.isMySQL() ? "AUTO_INCREMENT" : "AUTOINCREMENT";
    }

    public void createTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS player_shops (
                    id VARCHAR(36) PRIMARY KEY,
                    owner_uuid VARCHAR(36) NOT NULL,
                    owner_name VARCHAR(16) NOT NULL,
                    world_name VARCHAR(64) NOT NULL,
                    sign_x INTEGER NOT NULL,
                    sign_y INTEGER NOT NULL,
                    sign_z INTEGER NOT NULL,
                    chest_x INTEGER NOT NULL,
                    chest_y INTEGER NOT NULL,
                    chest_z INTEGER NOT NULL,
                    quantity INTEGER NOT NULL,
                    price DOUBLE NOT NULL,
                    material VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN'
                )""";
        try (Statement st = db.getConnection().createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create player_shops table", e);
        }
    }

    public void save(PlayerShop shop) {
        String sql = db.isMySQL()
                ? "INSERT INTO player_shops VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE quantity=?,price=?,material=?"
                : "INSERT OR REPLACE INTO player_shops VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, shop.getId().toString());
            ps.setString(2, shop.getOwnerUuid().toString());
            ps.setString(3, shop.getOwnerName());
            ps.setString(4, shop.getWorldName());
            ps.setInt(5, shop.getSignX());
            ps.setInt(6, shop.getSignY());
            ps.setInt(7, shop.getSignZ());
            ps.setInt(8, shop.getChestX());
            ps.setInt(9, shop.getChestY());
            ps.setInt(10, shop.getChestZ());
            ps.setInt(11, shop.getQuantity());
            ps.setDouble(12, shop.getPrice());
            ps.setString(13, shop.getMaterial());
            if (db.isMySQL()) {
                ps.setInt(14, shop.getQuantity());
                ps.setDouble(15, shop.getPrice());
                ps.setString(16, shop.getMaterial());
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to save player shop", e);
        }
    }

    public void delete(UUID shopId) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM player_shops WHERE id=?")) {
            ps.setString(1, shopId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to delete player shop", e);
        }
    }

    public List<PlayerShop> loadAll() {
        List<PlayerShop> list = new ArrayList<>();
        try (Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM player_shops")) {
            while (rs.next()) {
                list.add(fromRow(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to load player shops", e);
        }
        return list;
    }

    public List<PlayerShop> loadByOwner(UUID ownerUuid) {
        List<PlayerShop> list = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT * FROM player_shops WHERE owner_uuid=?")) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(fromRow(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to load player shops by owner", e);
        }
        return list;
    }

    private PlayerShop fromRow(ResultSet rs) throws SQLException {
        return new PlayerShop(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("owner_name"),
                rs.getString("world_name"),
                rs.getInt("sign_x"), rs.getInt("sign_y"), rs.getInt("sign_z"),
                rs.getInt("chest_x"), rs.getInt("chest_y"), rs.getInt("chest_z"),
                rs.getInt("quantity"),
                rs.getDouble("price"),
                rs.getString("material")
        );
    }
}

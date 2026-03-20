package me.fluxmarket.module.orders;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class OrdersDao {

    private final FluxMarket plugin;

    public OrdersDao(FluxMarket plugin) {
        this.plugin = plugin;
    }

    // ─── Orders ─────────────────────────────────────────────────────────────

    public void save(Order order) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                    "REPLACE INTO orders (uuid, creator_uuid, creator_name, material, amount_needed, " +
                    "amount_delivered, price_each, status, created_at, expires_at) VALUES (?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, order.getUuid().toString());
                ps.setString(2, order.getCreatorUuid().toString());
                ps.setString(3, order.getCreatorName());
                ps.setString(4, order.getMaterial().name());
                ps.setInt(5, order.getAmountNeeded());
                ps.setInt(6, order.getAmountDelivered());
                ps.setDouble(7, order.getPriceEach());
                ps.setString(8, order.getStatus().name());
                ps.setLong(9, order.getCreatedAt());
                ps.setLong(10, order.getExpiresAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "OrdersDao save error", e);
            }
        });
    }

    public List<Order> loadActive() {
        List<Order> result = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT * FROM orders WHERE status = 'ACTIVE' ORDER BY created_at DESC")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Order o = fromResultSet(rs);
                if (o != null) result.add(o);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "OrdersDao load error", e);
        }
        return result;
    }

    public List<Order> loadByPlayer(UUID player) {
        List<Order> result = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT * FROM orders WHERE creator_uuid = ? AND status = 'ACTIVE'")) {
            ps.setString(1, player.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Order o = fromResultSet(rs);
                if (o != null) result.add(o);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("OrdersDao loadByPlayer error: " + e.getMessage());
        }
        return result;
    }

    private Order fromResultSet(ResultSet rs) throws SQLException {
        try {
            return new Order(
                    UUID.fromString(rs.getString("uuid")),
                    UUID.fromString(rs.getString("creator_uuid")),
                    rs.getString("creator_name"),
                    Material.valueOf(rs.getString("material")),
                    rs.getInt("amount_needed"),
                    rs.getInt("amount_delivered"),
                    rs.getDouble("price_each"),
                    Order.Status.valueOf(rs.getString("status")),
                    rs.getLong("created_at"),
                    rs.getLong("expires_at"));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse order: " + e.getMessage());
            return null;
        }
    }

    // ─── Order Mailbox ───────────────────────────────────────────────────────

    /** Store a delivered item in the order creator's mailbox. Async-safe. */
    public void saveMailboxItem(UUID orderUuid, UUID creatorUuid, ItemStack item) {
        byte[] data = ItemUtils.serialize(item);
        if (data.length == 0) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                    "INSERT INTO order_mailbox (order_uuid, creator_uuid, item_data, delivered_at) VALUES (?,?,?,?)")) {
                ps.setString(1, orderUuid.toString());
                ps.setString(2, creatorUuid.toString());
                ps.setBytes(3, data);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Mailbox save error: " + e.getMessage());
            }
        });
    }

    /** Load all mailbox entries for a player. Blocking — call async. */
    public List<MailboxEntry> loadMailbox(UUID playerUuid) {
        List<MailboxEntry> result = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT id, order_uuid, item_data FROM order_mailbox WHERE creator_uuid = ? ORDER BY delivered_at ASC")) {
            ps.setString(1, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                UUID orderUuid = UUID.fromString(rs.getString("order_uuid"));
                ItemStack item = ItemUtils.deserialize(rs.getBytes("item_data"));
                if (item != null && !item.getType().isAir()) {
                    result.add(new MailboxEntry(id, orderUuid, item));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Mailbox load error: " + e.getMessage());
        }
        return result;
    }

    /** Delete a single mailbox entry by its row id. Call async. */
    public void removeMailboxEntry(int id) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                    "DELETE FROM order_mailbox WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Mailbox remove error: " + e.getMessage());
            }
        });
    }

    /** Count unclaimed mailbox items for a player. Blocking — call async. */
    public int countMailbox(UUID playerUuid) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT COUNT(*) FROM order_mailbox WHERE creator_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().warning("Mailbox count error: " + e.getMessage());
        }
        return 0;
    }

    /** Simple record holding one mailbox row. */
    public record MailboxEntry(int id, UUID orderUuid, ItemStack item) {}
}

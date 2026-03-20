package me.fluxmarket.module.sell.wand;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Material;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SellWandManager {

    private final FluxMarket plugin;
    private final NamespacedKey wandKey;

    public SellWandManager(FluxMarket plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "wand_uuid");
    }

    /**
     * Create a SellWand, persist to DB, return its ItemStack.
     */
    public ItemStack createWand(UUID ownerUuid, String ownerName, SellWand.WandType type,
                                int uses, long expiresAt) {
        UUID wandUuid = UUID.randomUUID();
        SellWand wand = new SellWand(wandUuid, ownerUuid, ownerName, type, uses, expiresAt);
        saveWand(wand);
        return buildWandItem(wand);
    }

    /**
     * Load a wand from DB by its UUID (synchronous — intended for quick event lookups).
     */
    public SellWand getWand(UUID wandUuid) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT * FROM sell_wands WHERE uuid = ?")) {
            ps.setString(1, wandUuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return fromResultSet(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("SellWandManager getWand error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Persist wand state asynchronously (INSERT OR REPLACE / UPSERT).
     */
    public void saveWand(SellWand wand) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean mysql = plugin.getDatabaseManager().isMySQL();
                String sql = mysql
                        ? "INSERT INTO sell_wands (uuid, owner_uuid, owner_name, wand_type, uses_remaining, expires_at, created_at) " +
                          "VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE " +
                          "uses_remaining=VALUES(uses_remaining), expires_at=VALUES(expires_at)"
                        : "INSERT OR REPLACE INTO sell_wands (uuid, owner_uuid, owner_name, wand_type, uses_remaining, expires_at, created_at) " +
                          "VALUES (?,?,?,?,?,?,?)";
                try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                    ps.setString(1, wand.getUuid().toString());
                    ps.setString(2, wand.getOwnerUuid().toString());
                    ps.setString(3, wand.getOwnerName());
                    ps.setString(4, wand.getType().name());
                    ps.setInt(5, wand.getUsesRemaining());
                    ps.setLong(6, wand.getExpiresAt());
                    ps.setLong(7, wand.getCreatedAt());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("SellWandManager save error: " + e.getMessage());
            }
        });
    }

    /**
     * Delete wand record asynchronously.
     */
    public void deleteWand(UUID wandUuid) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                    "DELETE FROM sell_wands WHERE uuid = ?")) {
                ps.setString(1, wandUuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("SellWandManager delete error: " + e.getMessage());
            }
        });
    }

    /**
     * Build the ItemStack representation of a sell wand.
     */
    public ItemStack buildWandItem(SellWand wand) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Owner: &f" + wand.getOwnerName());
        if (wand.getType() == SellWand.WandType.USE) {
            lore.add("&7Uses: &e" + wand.getUsesRemaining());
        } else {
            long remaining = wand.getExpiresAt() - System.currentTimeMillis();
            lore.add("&7Expires: &e" + (remaining > 0 ? FormatUtils.formatDuration(remaining) : "Expired"));
        }
        lore.add("");
        lore.add("&7Right-click a container to sell its contents.");

        ItemStack item = ItemUtils.named(Material.BLAZE_ROD, "&6Sell Wand",
                lore.toArray(new String[0]));

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.STRING, wand.getUuid().toString());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Read wand UUID from item PDC. Returns null if item is not a sell wand.
     */
    public UUID getWandUuidFromItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String uuidStr = meta.getPersistentDataContainer().get(wandKey, PersistentDataType.STRING);
        if (uuidStr == null) return null;
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Update lore of an existing wand ItemStack in-place to reflect current state.
     */
    public void updateWandItem(ItemStack item, SellWand wand) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(FormatUtils.comp("&7Owner: &f" + wand.getOwnerName()));
        if (wand.getType() == SellWand.WandType.USE) {
            lore.add(FormatUtils.comp("&7Uses: &e" + wand.getUsesRemaining()));
        } else {
            long remaining = wand.getExpiresAt() - System.currentTimeMillis();
            lore.add(FormatUtils.comp("&7Expires: &e" + (remaining > 0 ? FormatUtils.formatDuration(remaining) : "Expired")));
        }
        lore.add(net.kyori.adventure.text.Component.empty());
        lore.add(FormatUtils.comp("&7Right-click a container to sell its contents."));

        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private SellWand fromResultSet(ResultSet rs) throws SQLException {
        return new SellWand(
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("owner_name"),
                SellWand.WandType.valueOf(rs.getString("wand_type")),
                rs.getInt("uses_remaining"),
                rs.getLong("expires_at"),
                rs.getLong("created_at")
        );
    }

    public NamespacedKey getWandKey() { return wandKey; }
}

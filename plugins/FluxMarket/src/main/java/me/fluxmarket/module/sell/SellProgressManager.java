package me.fluxmarket.module.sell;

import me.fluxmarket.FluxMarket;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SellProgressManager {

    // In-memory cache: playerUUID -> (category -> totalEarned)
    private final Map<UUID, Map<String, Double>> cache = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> loading = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final FluxMarket plugin;

    public SellProgressManager(FluxMarket plugin) {
        this.plugin = plugin;
    }

    // Load from DB async, store in cache (call on player join or first use)
    public void loadPlayer(UUID uuid) {
        if (!loading.add(uuid)) return; // already loading
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Double> earnings = new ConcurrentHashMap<>();
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                    "SELECT category, total_earned FROM sell_progress WHERE player_uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    earnings.put(rs.getString("category"), rs.getDouble("total_earned"));
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("SellProgressManager load error: " + e.getMessage());
            }
            cache.put(uuid, earnings);
            loading.remove(uuid);
        });
    }

    // Add earnings for a category, update cache, persist async
    public void addEarnings(UUID uuid, String category, double earned) {
        // Ensure DB data is loaded before computing the new total (called from async context)
        if (!cache.containsKey(uuid) && loading.add(uuid)) {
            loadPlayerSync(uuid);
            loading.remove(uuid);
        } else if (!cache.containsKey(uuid)) {
            // Another thread is loading — wait briefly then proceed with empty cache
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
        cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .merge(category, earned, Double::sum);
        double newTotal = cache.get(uuid).get(category);
        persistAsync(uuid, category, newTotal);
    }

    /** Synchronous DB load — call from an async thread. Used by GUI and addEarnings. */
    public void loadPlayerSync(UUID uuid) {
        Map<String, Double> earnings = new ConcurrentHashMap<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT category, total_earned FROM sell_progress WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) earnings.put(rs.getString("category"), rs.getDouble("total_earned"));
        } catch (SQLException e) {
            plugin.getLogger().warning("SellProgressManager sync load: " + e.getMessage());
        }
        cache.put(uuid, earnings);
    }

    // Get current tier (1-20) for player in category
    public int getTier(UUID uuid, String category) {
        double total = getTotalEarned(uuid, category);
        int highestTier = 1;
        for (int tier = 1; tier <= 20; tier++) {
            double threshold = getThresholdForTier(tier);
            if (total >= threshold) {
                highestTier = tier;
            } else {
                break;
            }
        }
        return highestTier;
    }

    // Get multiplier for current tier (from config)
    public double getMultiplier(UUID uuid, String category) {
        return getMultiplierForTier(getTier(uuid, category));
    }

    // Get total earned for category
    public double getTotalEarned(UUID uuid, String category) {
        Map<String, Double> playerData = cache.get(uuid);
        if (playerData == null) return 0.0;
        return playerData.getOrDefault(category, 0.0);
    }

    // Get all categories with earnings for a player
    public Map<String, Double> getAllEarnings(UUID uuid) {
        Map<String, Double> playerData = cache.get(uuid);
        if (playerData == null) return Collections.emptyMap();
        return Collections.unmodifiableMap(playerData);
    }

    // Tier thresholds and multipliers from config
    // Config path: sell.progression.tiers.N.threshold, sell.progression.tiers.N.multiplier
    public double getThresholdForTier(int tier) {
        return plugin.getConfigManager().getProgressionThreshold(tier);
    }

    public double getMultiplierForTier(int tier) {
        return plugin.getConfigManager().getProgressionMultiplier(tier);
    }

    private void persistAsync(UUID uuid, String category, double totalEarned) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean mysql = plugin.getDatabaseManager().isMySQL();
                String sql = mysql
                        ? "INSERT INTO sell_progress (player_uuid, category, total_earned) VALUES (?,?,?) " +
                          "ON DUPLICATE KEY UPDATE total_earned=VALUES(total_earned)"
                        : "INSERT OR REPLACE INTO sell_progress (player_uuid, category, total_earned) VALUES (?,?,?)";
                try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, category);
                    ps.setDouble(3, totalEarned);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("SellProgressManager persist error: " + e.getMessage());
            }
        });
    }

    public void evict(UUID uuid) {
        cache.remove(uuid);
    }
}

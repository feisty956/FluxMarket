package me.fluxmarket.module.flux;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.config.ConfigManager;

import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core dynamic pricing engine.
 * Price = basePrice × (1 + maxSwing × tanh(sensitivity × netDemand))
 * netDemand = Σ(weight(tx) × sign(BUY=+1, SELL=-1) × amount) / normFactor
 * weight(tx) = e^(-λ × ageInHours)
 */
public class FluxEngine {

    private final FluxMarket plugin;
    // Cached computed prices: material → current price
    private final Map<String, Double> priceCache = new ConcurrentHashMap<>();
    // Cached demand state: material → [netDemand, normFactor] — used for batch sell simulation
    private final Map<String, double[]> demandCache = new ConcurrentHashMap<>();
    // Active event modifiers: material → multiplier (1.0 = no effect)
    private final Map<String, Double> eventModifiers = new ConcurrentHashMap<>();
    // Global sell modifier (for TAX DAY): multiplier on sell prices
    private volatile double globalSellModifier = 1.0;

    public FluxEngine(FluxMarket plugin) {
        this.plugin = plugin;
    }

    /**
     * Record a transaction. Must be called on main thread or async — thread-safe.
     * type: "BUY" or "SELL"
     */
    public void recordTransaction(String playerUuid, String material, int amount, String type, double price) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                    "INSERT INTO flux_transactions (player_uuid, material, amount, type, price, timestamp) VALUES (?,?,?,?,?,?)")) {
                ps.setString(1, playerUuid);
                ps.setString(2, material);
                ps.setInt(3, amount);
                ps.setString(4, type);
                ps.setDouble(5, price);
                ps.setLong(6, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to record transaction: " + e.getMessage());
            }
            // Invalidate cache for this material so next access recalculates
            priceCache.remove(material);
        });
    }

    /**
     * Get the current dynamic buy price for a material.
     * Uses cache — recalculated on demand or by scheduler.
     */
    public double getBuyPrice(String material, double basePrice, String playerUuid) {
        double dynamic = getDynamicPrice(material, basePrice);
        ConfigManager cfg = plugin.getConfigManager();
        double spread = cfg.getFluxDefaultSpread();
        double buyPrice = dynamic * (1.0 + spread);
        // Apply player discount if player is online
        if (playerUuid != null) {
            org.bukkit.entity.Player player = plugin.getServer().getPlayer(java.util.UUID.fromString(playerUuid));
            if (player != null) {
                double discount = cfg.getBuyDiscount(player);
                buyPrice *= (1.0 - discount);
            }
        }
        return Math.max(0.01, buyPrice);
    }

    public double getSellPrice(String material, double basePrice, String playerUuid) {
        double dynamic = getDynamicPrice(material, basePrice);
        ConfigManager cfg = plugin.getConfigManager();
        double spread = cfg.getFluxDefaultSpread();
        double sellPrice = dynamic * (1.0 - spread) * globalSellModifier;
        // Apply sell multiplier if player is online
        if (playerUuid != null) {
            org.bukkit.entity.Player player = plugin.getServer().getPlayer(java.util.UUID.fromString(playerUuid));
            if (player != null) {
                double multiplier = cfg.getSellMultiplier(player);
                sellPrice *= multiplier;
            }
        }
        // Anti-exploit: sell may never reach or exceed the player's actual buy price.
        // Include event modifier AND buy discount so the cap scales correctly in all conditions.
        double buyFloor = dynamic * (1.0 + spread) * getEventModifier(material);
        if (playerUuid != null) {
            org.bukkit.entity.Player p = plugin.getServer().getPlayer(java.util.UUID.fromString(playerUuid));
            if (p != null) buyFloor *= (1.0 - plugin.getConfigManager().getBuyDiscount(p));
        }
        return Math.max(0.01, Math.min(sellPrice, buyFloor * 0.95));
    }

    /**
     * Simulate selling `quantity` items at once, integrating the price curve.
     * Each unit sold pushes netDemand down, so later units fetch a lower price.
     * Uses up to 50 discrete steps for efficiency regardless of quantity.
     * Returns the total earned (spread + globalSellModifier + player multiplier applied).
     */
    public double simulateBatchSell(String material, double basePrice, int quantity, String playerUuid) {
        if (quantity <= 0) return 0.0;
        ConfigManager cfg = plugin.getConfigManager();
        double maxSwing = cfg.getFluxDefaultMaxSwing();
        double sensitivity = cfg.getFluxDefaultSensitivity();
        double spread = cfg.getFluxDefaultSpread();

        // Start from last known demand state; default to neutral if unknown
        double[] demand = demandCache.getOrDefault(material, new double[]{0.0, Math.max(1.0, (double) quantity)});
        double netDemand = demand[0];
        double normFactor = Math.max(demand[1], 1.0);

        // Integrate in discrete steps (max 50 for performance)
        int steps = Math.min(quantity, 50);
        double itemsPerStep = (double) quantity / steps;
        double totalEarned = 0.0;

        for (int s = 0; s < steps; s++) {
            // Each sell step pushes demand down
            normFactor  += itemsPerStep;
            netDemand   -= itemsPerStep;
            double norm  = netDemand / normFactor;
            double dyn   = basePrice * (1.0 + maxSwing * Math.tanh(sensitivity * norm));
            dyn = Math.max(basePrice * (1.0 - maxSwing), Math.min(basePrice * (1.0 + maxSwing), dyn));
            // Cap: sell per unit may never reach buy price for this step
            double buyRef   = dyn * (1.0 + spread) * getEventModifier(material);
            double maxStep  = buyRef * 0.95;
            double stepPrice = Math.min(dyn * (1.0 - spread) * globalSellModifier * getEventModifier(material), maxStep);
            totalEarned += stepPrice * itemsPerStep;
        }

        // Apply player sell multiplier, then re-apply cap on total
        if (playerUuid != null) {
            org.bukkit.entity.Player player = plugin.getServer().getPlayer(java.util.UUID.fromString(playerUuid));
            if (player != null) {
                double multiplier = cfg.getSellMultiplier(player);
                // Cap post-multiplier: effective sell/unit ≤ 95% of buy/unit
                double discount = cfg.getBuyDiscount(player);
                double buyRef = basePrice * (1.0 + spread) * getEventModifier(material) * (1.0 - discount);
                double maxTotal = buyRef * 0.95 * quantity;
                totalEarned = Math.min(totalEarned * multiplier, maxTotal);
            }
        }

        return Math.max(0.0, totalEarned);
    }

    /** Compute or fetch cached dynamic price (no spread applied). */
    public double getDynamicPrice(String material, double basePrice) {
        Double cached = priceCache.get(material);
        if (cached != null) return cached * getEventModifier(material);
        double computed = computePrice(material, basePrice);
        priceCache.put(material, computed);
        return computed * getEventModifier(material);
    }

    private double getEventModifier(String material) {
        Double mod = eventModifiers.get(material);
        return mod != null ? mod : 1.0;
    }

    /** Recalculate price from DB transactions. Blocking — call async. */
    public double computePrice(String material, double basePrice) {
        ConfigManager cfg = plugin.getConfigManager();
        int windowHours = cfg.getFluxTransactionWindow();
        double lambda = cfg.getFluxDecayLambda();
        double maxSwing = cfg.getFluxDefaultMaxSwing();
        double sensitivity = cfg.getFluxDefaultSensitivity();

        long cutoff = System.currentTimeMillis() - (long) windowHours * 3_600_000L;

        double netDemand = 0.0;
        double normFactor = 0.0;
        long now = System.currentTimeMillis();

        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT type, amount, timestamp FROM flux_transactions WHERE material = ? AND timestamp > ?")) {
            ps.setString(1, material);
            ps.setLong(2, cutoff);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String type = rs.getString("type");
                int amount = rs.getInt("amount");
                long ts = rs.getLong("timestamp");
                double ageHours = (now - ts) / 3_600_000.0;
                double weight = Math.exp(-lambda * ageHours);
                double sign = type.equals("BUY") ? 1.0 : -1.0;
                netDemand += sign * amount * weight;
                normFactor += amount * weight;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("FluxEngine DB error: " + e.getMessage());
            return basePrice;
        }

        if (normFactor < 1.0) {
            // No recent transactions — preserve existing demand state rather than zeroing out.
            // This prevents artificially low normFactor on next batch-sell simulation.
            demandCache.putIfAbsent(material, new double[]{0.0, 1.0});
            return basePrice;
        }

        demandCache.put(material, new double[]{netDemand, normFactor});
        double normalizedDemand = netDemand / normFactor;
        double price = basePrice * (1.0 + maxSwing * Math.tanh(sensitivity * normalizedDemand));
        return Math.max(basePrice * (1.0 - maxSwing), Math.min(basePrice * (1.0 + maxSwing), price));
    }

    /** Recalculate all registered materials and update cache. Called by scheduler. */
    public void recalculateAll() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // Get all materials with transactions in the window
            long cutoff = System.currentTimeMillis()
                    - (long) plugin.getConfigManager().getFluxTransactionWindow() * 3_600_000L;
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                    "SELECT DISTINCT material FROM flux_transactions WHERE timestamp > ?")) {
                ps.setLong(1, cutoff);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String material = rs.getString("material");
                    // We need the base price — look it up from shop config
                    double basePrice = getBasePrice(material);
                    if (basePrice > 0) {
                        double price = computePrice(material, basePrice);
                        priceCache.put(material, price);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("FluxEngine recalculate error: " + e.getMessage());
            }
            // Snapshot to history
            snapshotHistory();
            // Cleanup old transactions
            cleanupOldTransactions();
        });
    }

    private double getBasePrice(String material) {
        var raw = plugin.getConfigManager().getRaw();
        if (!raw.isConfigurationSection("shop.categories")) return -1;
        for (String cat : raw.getConfigurationSection("shop.categories").getKeys(false)) {
            String path = "shop.categories." + cat + ".items." + material + ".base-price";
            if (raw.contains(path)) return raw.getDouble(path);
        }
        return -1;
    }

    private void snapshotHistory() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Double> entry : priceCache.entrySet()) {
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                    "INSERT INTO flux_price_history (material, price, timestamp) VALUES (?,?,?)")) {
                ps.setString(1, entry.getKey());
                ps.setDouble(2, entry.getValue());
                ps.setLong(3, now);
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        }
    }

    private void cleanupOldTransactions() {
        int days = plugin.getConfigManager().getFluxHistoryCleanupDays();
        long cutoff = System.currentTimeMillis() - (long) days * 86_400_000L;
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "DELETE FROM flux_transactions WHERE timestamp < ?")) {
            ps.setLong(1, cutoff);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "DELETE FROM flux_price_history WHERE timestamp < ?")) {
            ps.setLong(1, cutoff);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    /** Fetch last N price snapshots for sparkline. */
    public double[] getPriceHistory(String material, int points) {
        double[] result = new double[points];
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT price FROM flux_price_history WHERE material = ? ORDER BY timestamp DESC LIMIT ?")) {
            ps.setString(1, material);
            ps.setInt(2, points);
            ResultSet rs = ps.executeQuery();
            int i = points - 1;
            while (rs.next() && i >= 0) {
                result[i--] = rs.getDouble("price");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("History fetch error: " + e.getMessage());
        }
        return result;
    }

    /** Compute percent change vs. base price. */
    public double getPercentChange(String material, double basePrice) {
        Double current = priceCache.get(material);
        if (current == null) return 0.0;
        return ((current - basePrice) / basePrice) * 100.0;
    }

    // --- Event modifiers ---

    public void applyEventModifier(String material, double modifier) {
        eventModifiers.put(material, modifier);
    }

    public void removeEventModifier(String material) {
        eventModifiers.remove(material);
    }

    public void setGlobalSellModifier(double modifier) {
        this.globalSellModifier = modifier;
    }

    public void resetGlobalSellModifier() {
        this.globalSellModifier = 1.0;
    }

    public void invalidateCache(String material) {
        priceCache.remove(material);
    }

    public void invalidateAll() {
        priceCache.clear();
    }

    public Map<String, Double> getPriceCache() {
        return java.util.Collections.unmodifiableMap(priceCache);
    }
}

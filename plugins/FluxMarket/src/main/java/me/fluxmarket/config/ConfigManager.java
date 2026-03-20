package me.fluxmarket.config;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.util.FormatUtils;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final FluxMarket plugin;
    private FileConfiguration cfg;

    public ConfigManager(FluxMarket plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.cfg = plugin.getConfig();
    }

    public boolean isModuleEnabled(String module) {
        return cfg.getBoolean("modules." + module, true);
    }

    public String getPrefix() {
        return FormatUtils.color(cfg.getString("messages.prefix", "&8[&6FluxMarket&8] &r"));
    }

    public String getMessage(String key) {
        return FormatUtils.color(cfg.getString("messages." + key, "&cMessage not found: " + key));
    }

    // Database
    public String getDbType() { return cfg.getString("database.type", "sqlite"); }
    public String getMysqlHost() { return cfg.getString("database.mysql.host", "localhost"); }
    public int getMysqlPort() { return cfg.getInt("database.mysql.port", 3306); }
    public String getMysqlDatabase() { return cfg.getString("database.mysql.database", "fluxmarket"); }
    public String getMysqlUsername() { return cfg.getString("database.mysql.username", "root"); }
    public String getMysqlPassword() { return cfg.getString("database.mysql.password", ""); }
    public boolean getMysqlUseSSL() { return cfg.getBoolean("database.mysql.use-ssl", false); }
    public boolean getMysqlAllowPublicKeyRetrieval() { return cfg.getBoolean("database.mysql.allow-public-key-retrieval", true); }

    // Flux
    public int getFluxUpdateInterval() { return cfg.getInt("flux.update-interval-minutes", 15); }
    public int getFluxTransactionWindow() { return cfg.getInt("flux.transaction-window-hours", 6); }
    public double getFluxDecayLambda() { return cfg.getDouble("flux.decay-lambda", 0.3); }
    public int getFluxHistoryCleanupDays() { return cfg.getInt("flux.history-cleanup-days", 7); }
    public double getFluxDefaultMaxSwing() { return cfg.getDouble("flux.defaults.max-swing", 0.4); }
    public double getFluxDefaultSensitivity() { return cfg.getDouble("flux.defaults.sensitivity", 0.5); }
    public double getFluxDefaultSpread() { return cfg.getDouble("flux.defaults.spread", 0.1); }
    public boolean isFluxEventsEnabled() { return cfg.getBoolean("flux.events.enabled", true); }
    public int getFluxEventCheckInterval() { return cfg.getInt("flux.events.check-interval-minutes", 60); }

    // Shop
    public FileConfiguration getShopSection() { return cfg; }
    /**
     * When true (default), buy and sell prices are both derived from the same
     * dynamic base price via the flux engine (spread applied to both sides).
     * When false, per-item buy-price / sell-price fields act as fully independent
     * static prices — useful for items where buy ≠ sell even before flux adjustments.
     */
    public boolean isShopLinkPrices() { return cfg.getBoolean("shop.link-prices", true); }

    // Sell
    public boolean isSellGuiEnabled() { return cfg.getBoolean("sell.gui-enabled", true); }
    public boolean isSellWorthLore() { return cfg.getBoolean("sell.worth-lore", true); }
    public boolean isSellActionbar() { return cfg.getBoolean("sell.actionbar-feedback", true); }
    public String getSellSound() { return cfg.getString("sell.sound", "ENTITY_EXPERIENCE_ORB_PICKUP"); }
    public boolean isSellShulkerSupport() { return cfg.getBoolean("sell.shulker-support", true); }
    public boolean isSellAnyItem() { return cfg.getBoolean("sell.allow-any-item", false); }
    public double getSellDefaultPrice() { return cfg.getDouble("sell.default-price", 1.0); }
    public int getSellHistorySize() { return cfg.getInt("sell.history-size", 20); }
    public int getSellTopSize() { return cfg.getInt("sell.selltop-size", 10); }
    public boolean isSellWandsEnabled() { return cfg.getBoolean("sell.wands.enabled", true); }
    public int getSellWandDefaultUses() { return cfg.getInt("sell.wands.default-uses", 5000); }
    public int getSellWandDefaultDurationDays() { return cfg.getInt("sell.wands.default-duration-days", 3); }
    public boolean isSellProgressionEnabled() { return cfg.getBoolean("sell.progression.enabled", true); }
    public double getProgressionThreshold(int tier) {
        return cfg.getDouble("sell.progression.tiers." + tier + ".threshold", tier * 1000.0);
    }
    public double getProgressionMultiplier(int tier) {
        double configured = cfg.getDouble("sell.progression.tiers." + tier + ".multiplier", 1.0);
        double cap = cfg.getDouble("sell.progression.max-multiplier", 1.25);
        if (configured > cap) {
            plugin.getLogger().warning("[Config] sell.progression.tiers." + tier
                    + ".multiplier (" + configured + ") exceeds max-multiplier (" + cap
                    + ") — capping. Adjust max-multiplier if intended.");
        }
        return Math.max(1.0, Math.min(configured, cap));
    }

    // Auction
    public int getAhMaxListings() { return cfg.getInt("auction.max-listings", 5); }
    public int getAhDefaultDurationHours() { return cfg.getInt("auction.default-duration-hours", 48); }
    public double getAhMinPrice() { return cfg.getDouble("auction.min-price", 1.0); }
    public double getAhMaxPrice() { return cfg.getDouble("auction.max-price", 1_000_000.0); }
    public double getAhListingTax() { return cfg.getDouble("auction.listing-tax-percent", 5.0) / 100.0; }
    public double getAhSaleTax() { return cfg.getDouble("auction.sale-tax-percent", 2.0) / 100.0; }
    public int getAhAntiSnipeSeconds() { return cfg.getInt("auction.anti-snipe-seconds", 30); }
    public int getAhExpiredCleanupDays() { return cfg.getInt("auction.expired-cleanup-days", 7); }
    public boolean isAhShulkerPreview() { return cfg.getBoolean("auction.shulker-preview", true); }
    public int getAhHistorySize() { return cfg.getInt("auction.history-size", 50); }
    public boolean isAhEmergencyDisable() { return cfg.getBoolean("auction.emergency-disable", false); }
    public int getBulkDiscountMinQty() { return cfg.getInt("auction.bulk-discount.min-qty", 64); }
    public double getBulkDiscountPercent() { return cfg.getDouble("auction.bulk-discount.percent", 10.0); }

    // Orders
    public int getOrdersMaxPerPlayer() { return cfg.getInt("orders.max-orders", 3); }
    public int getOrdersDefaultExpiryHours() { return cfg.getInt("orders.default-expiry-hours", 72); }
    public double getOrdersMinPrice() { return cfg.getDouble("orders.min-price", 1.0); }
    public boolean isOrdersPartialDelivery() { return cfg.getBoolean("orders.partial-delivery", true); }
    public boolean isOrdersShulkerDelivery() { return cfg.getBoolean("orders.shulker-delivery", true); }

    // Webhook
    public String getWebhookUrl() { return cfg.getString("webhook.url", ""); }
    public double getWebhookMinSaleAmount() { return cfg.getDouble("webhook.min-sale-amount", 10000.0); }
    public boolean isWebhookFluxEvents() { return cfg.getBoolean("webhook.notify-flux-events", true); }
    public boolean isWebhookSellTop() { return cfg.getBoolean("webhook.notify-selltop-changes", false); }

    // Multipliers & Discounts
    public double getSellMultiplier(org.bukkit.entity.Player player) {
        if (!cfg.isConfigurationSection("multipliers")) return 1.0;
        double best = 1.0;
        for (String key : cfg.getConfigurationSection("multipliers").getKeys(false)) {
            String perm = cfg.getString("multipliers." + key + ".permission", "");
            if (player.hasPermission(perm)) {
                double mult = cfg.getDouble("multipliers." + key + ".sell-multiplier", 1.0);
                if (mult > best) best = mult;
            }
        }
        return best;
    }

    public double getBuyDiscount(org.bukkit.entity.Player player) {
        if (!cfg.isConfigurationSection("discounts")) return 0.0;
        double best = 0.0;
        for (String key : cfg.getConfigurationSection("discounts").getKeys(false)) {
            String perm = cfg.getString("discounts." + key + ".permission", "");
            if (player.hasPermission(perm)) {
                double disc = cfg.getDouble("discounts." + key + ".buy-discount", 0.0);
                if (disc > best) best = disc;
            }
        }
        return best;
    }

    // Player Shops
    public int getPlayerShopMaxPerPlayer() { return cfg.getInt("player-shops.max-per-player", 5); }

    public FileConfiguration getRaw() { return cfg; }
}

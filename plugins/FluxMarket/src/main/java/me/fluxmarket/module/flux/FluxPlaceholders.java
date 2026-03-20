package me.fluxmarket.module.flux;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.fluxmarket.FluxMarket;
import me.fluxmarket.util.FormatUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI expansion.
 * Placeholders:
 *   %flux_price_MATERIAL%    → current dynamic price
 *   %flux_sell_MATERIAL%     → current sell price
 *   %flux_buy_MATERIAL%      → current buy price
 *   %flux_trend_MATERIAL%    → ▲ / ▼ / ●
 *   %flux_change_MATERIAL%   → +12.3% / -5.1%
 *   %flux_suggested_MATERIAL% → formatted buy price suggestion
 */
public class FluxPlaceholders extends PlaceholderExpansion {

    private final FluxMarket plugin;
    private final FluxEngine engine;

    public FluxPlaceholders(FluxMarket plugin, FluxEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    @Override public @NotNull String getIdentifier() { return "flux"; }
    @Override public @NotNull String getAuthor() { return "FluxMarket"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        // identifier looks like "price_DIAMOND" or "trend_IRON_INGOT"
        int sep = identifier.indexOf('_');
        if (sep == -1) return null;
        String type = identifier.substring(0, sep).toLowerCase();
        String material = identifier.substring(sep + 1).toUpperCase();

        double basePrice = getBasePrice(material);
        if (basePrice < 0) return "N/A";

        return switch (type) {
            case "price" -> FormatUtils.formatMoney(engine.getDynamicPrice(material, basePrice));
            case "sell"  -> FormatUtils.formatMoney(engine.getSellPrice(material, basePrice,
                    player != null ? player.getUniqueId().toString() : null));
            case "buy"   -> FormatUtils.formatMoney(engine.getBuyPrice(material, basePrice,
                    player != null ? player.getUniqueId().toString() : null));
            case "trend" -> {
                double pct = engine.getPercentChange(material, basePrice);
                yield FormatUtils.color(FormatUtils.trendIndicator(pct));
            }
            case "change" -> {
                double pct = engine.getPercentChange(material, basePrice);
                yield FormatUtils.formatPercent(pct);
            }
            case "suggested" -> FormatUtils.formatMoney(engine.getBuyPrice(material, basePrice, null));
            default -> null;
        };
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
}

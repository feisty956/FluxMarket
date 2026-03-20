package me.fluxmarket.util;

import org.bukkit.ChatColor;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class FormatUtils {

    private static final DecimalFormat MONEY_FMT;
    private static final char[] SPARK = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};

    static {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
        sym.setGroupingSeparator(',');
        sym.setDecimalSeparator('.');
        MONEY_FMT = new DecimalFormat("#,##0.00", sym);
    }

    private FormatUtils() {}

    /** Translate &-color codes. */
    public static String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Deserialize a legacy &-formatted string into an Adventure Component
     * with italic explicitly disabled (prevents Minecraft's default item-name italic).
     */
    public static net.kyori.adventure.text.Component comp(String text) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(text == null ? "" : text)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    /** Format a monetary value: 1234.5 → "1.234,50" */
    public static String formatMoney(double amount) {
        return MONEY_FMT.format(amount);
    }

    /** Compact number: 1500 → "1,5k", 2000000 → "2M" */
    public static String compact(double value) {
        if (value >= 1_000_000) return String.format("%.1fM", value / 1_000_000);
        if (value >= 1_000)    return String.format("%.1fk", value / 1_000);
        return String.format("%.0f", value);
    }

    /**
     * Build an 8-char Unicode sparkline for a price history array.
     * Values outside [min, max] are clamped.
     */
    public static String sparkline(double[] values, double min, double max) {
        if (values == null || values.length == 0) return "--------";
        StringBuilder sb = new StringBuilder();
        double range = max - min;
        for (double v : values) {
            int idx = range <= 0 ? 4 : (int) Math.min(7, Math.max(0, ((v - min) / range) * 7));
            sb.append(SPARK[idx]);
        }
        return sb.toString();
    }

    /** ▲ green / ▼ red / ● yellow based on percent change */
    public static String trendIndicator(double percentChange) {
        if (percentChange > 1.0)  return "&a▲";
        if (percentChange < -1.0) return "&c▼";
        return "&e●";
    }

    /** Format percent change: +12.3% or -5.1% */
    public static String formatPercent(double change) {
        String sign = change >= 0 ? "+" : "";
        return String.format("%s%.1f%%", sign, change);
    }

    /** Format milliseconds to "2h 30m" or "45s" */
    public static String formatDuration(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m " + (seconds % 60) + "s";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h " + (minutes % 60) + "m";
        long days = hours / 24;
        return days + "d " + (hours % 24) + "h";
    }

    /** Format a material name for display: DIAMOND_SWORD → "Diamond Sword" */
    public static String formatMaterialName(String material) {
        String[] words = material.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}

package me.fluxmarket.module.shop;

import me.fluxmarket.FluxMarket;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShopRegistry {

    private final FluxMarket plugin;
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();

    public ShopRegistry(FluxMarket plugin) {
        this.plugin = plugin;
    }

    public void load() {
        categories.clear();
        ConfigurationSection cats = plugin.getConfigManager().getRaw()
                .getConfigurationSection("shop.categories");
        if (cats == null) {
            plugin.getLogger().warning("No shop categories configured!");
            return;
        }
        for (String key : cats.getKeys(false)) {
            ConfigurationSection cat = cats.getConfigurationSection(key);
            if (cat == null) continue;

            String name = cat.getString("name", key);
            Material icon = parseMaterial(cat.getString("icon", "CHEST"), Material.CHEST);
            List<ShopItem> items = new ArrayList<>();

            ConfigurationSection itemsSection = cat.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String mat : itemsSection.getKeys(false)) {
                    ConfigurationSection iCfg = itemsSection.getConfigurationSection(mat);
                    if (iCfg == null) continue;
                    double basePrice       = iCfg.getDouble("base-price",  1.0);
                    boolean buy            = iCfg.getBoolean("buy",         true);
                    boolean sell           = iCfg.getBoolean("sell",        true);
                    double buyOverride     = iCfg.getDouble("buy-price",   -1.0);
                    double sellOverride    = iCfg.getDouble("sell-price",  -1.0);
                    items.add(new ShopItem(mat, basePrice, buy, sell, buyOverride, sellOverride));
                }
            }
            categories.put(key, new ShopCategory(key, name, icon, items));
        }
        plugin.getLogger().info("ShopRegistry: loaded " + categories.size() + " categories.");
    }

    public Map<String, ShopCategory> getCategories() {
        return Collections.unmodifiableMap(categories);
    }

    public ShopItem findItem(String material) {
        for (ShopCategory cat : categories.values()) {
            for (ShopItem item : cat.items()) {
                if (item.material().equalsIgnoreCase(material)) return item;
            }
        }
        return null;
    }

    private Material parseMaterial(String name, Material fallback) {
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return fallback; }
    }
}

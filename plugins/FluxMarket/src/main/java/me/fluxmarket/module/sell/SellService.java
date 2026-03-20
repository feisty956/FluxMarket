package me.fluxmarket.module.sell;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.module.flux.FluxEngine;
import me.fluxmarket.module.shop.ShopItem;
import me.fluxmarket.module.shop.ShopRegistry;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SellService {

    private final FluxMarket plugin;
    private final SellDao dao;

    public SellService(FluxMarket plugin, SellDao dao) {
        this.plugin = plugin;
        this.dao = dao;
    }

    public SellResult sell(Player player, List<ItemStack> items) {
        ShopRegistry registry = plugin.getShopModule() != null ? plugin.getShopModule().getRegistry() : null;
        FluxEngine engine = plugin.getFluxModule() != null ? plugin.getFluxModule().getEngine() : null;
        if (registry == null) return new SellResult(0, Map.of(), List.of());

        Map<String, Integer> soldAmounts = new LinkedHashMap<>();
        Map<String, Double> soldPrices = new LinkedHashMap<>();

        double total = 0.0;
        for (ItemStack item : items) {
            total += processItem(player, item, registry, engine, soldAmounts, soldPrices);
        }

        if (total <= 0) return new SellResult(0, Map.of(), List.of());

        plugin.getEconomyProvider().deposit(player, total);

        double finalTotalForRecord = total;
        for (Map.Entry<String, Integer> entry : soldAmounts.entrySet()) {
            double itemEarned = soldPrices.getOrDefault(entry.getKey(), 0.0);
            dao.insertHistory(player.getUniqueId(), entry.getKey(), entry.getValue(), itemEarned);

            String cat = getCategoryForMaterial(entry.getKey());
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                dao.upsertProgress(player.getUniqueId(), cat, itemEarned);
                if (plugin.getSellProgressManager() != null) {
                    plugin.getSellProgressManager().addEarnings(player.getUniqueId(), cat, itemEarned);
                }
            });
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                dao.upsertSellTop(player.getUniqueId(), player.getName(), finalTotalForRecord));

        List<String> summary = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : soldAmounts.entrySet()) {
            summary.add("&7" + entry.getValue() + "x " + FormatUtils.formatMaterialName(entry.getKey())
                    + " &8- &a+" + FormatUtils.formatMoney(soldPrices.getOrDefault(entry.getKey(), 0.0)));
        }

        if (plugin.getConfigManager().isSellActionbar()) {
            String msg = FormatUtils.color("&aSold for &f" + FormatUtils.formatMoney(total));
            player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().deserialize(msg));
        }
        try {
            player.playSound(player.getLocation(),
                    Sound.valueOf(plugin.getConfigManager().getSellSound()), 1f, 1.2f);
        } catch (IllegalArgumentException ignored) {}

        return new SellResult(total, soldAmounts, summary);
    }

    private double processItem(Player player, ItemStack item, ShopRegistry registry, FluxEngine engine,
                               Map<String, Integer> soldAmounts, Map<String, Double> soldPrices) {
        if (item == null || item.getType().isAir()) return 0.0;

        if (ItemUtils.isShulkerBox(item.getType()) && plugin.getConfigManager().isSellShulkerSupport()) {
            return processShulker(player, item, registry, engine, soldAmounts, soldPrices);
        }

        String material = item.getType().name();
        ShopItem shopItem = registry.findItem(material);

        // If not in shop, check if selling any item is allowed
        if (shopItem == null || !shopItem.sellEnabled()) {
            if (!plugin.getConfigManager().isSellAnyItem()) return 0.0;
            double defaultPrice = plugin.getConfigManager().getSellDefaultPrice();
            if (defaultPrice <= 0) return 0.0;
            int amount = item.getAmount();
            String category = getCategoryForMaterial(material);
            double progressMult = (plugin.getConfigManager().isSellProgressionEnabled()
                    && plugin.getSellProgressManager() != null)
                    ? plugin.getSellProgressManager().getMultiplier(player.getUniqueId(), category)
                    : 1.0;
            double rankMult = plugin.getConfigManager().getSellMultiplier(player);
            double earned = defaultPrice * amount * rankMult * progressMult;
            soldAmounts.merge(material, amount, Integer::sum);
            soldPrices.merge(material, earned, Double::sum);
            item.setAmount(0);
            if (engine != null) engine.recordTransaction(player.getUniqueId().toString(), material, amount, "SELL", defaultPrice);
            return earned;
        }

        double base = shopItem.basePrice();
        int amount = item.getAmount();
        String uuid = player.getUniqueId().toString();
        String category = getCategoryForMaterial(material);
        double progressMult = (plugin.getConfigManager().isSellProgressionEnabled()
                && plugin.getSellProgressManager() != null)
                ? plugin.getSellProgressManager().getMultiplier(player.getUniqueId(), category)
                : 1.0;
        double rankMult = plugin.getConfigManager().getSellMultiplier(player);

        double earned;
        if (shopItem.hasSellOverride()) {
            earned = shopItem.sellPriceOverride() * amount * rankMult * progressMult;
        } else if (engine != null) {
            earned = engine.simulateBatchSell(material, base, amount, uuid) * rankMult * progressMult;
        } else {
            earned = base * amount * rankMult * progressMult;
        }
        earned = capSellAgainstBuy(material, base, amount, uuid, earned, engine, shopItem);

        double avgPrice = amount > 0 ? earned / amount : base;
        soldAmounts.merge(material, amount, Integer::sum);
        soldPrices.merge(material, earned, Double::sum);

        item.setAmount(0);

        if (engine != null) {
            engine.recordTransaction(uuid, material, amount, "SELL", avgPrice);
        }
        return earned;
    }

    private double processShulker(Player player, ItemStack shulkerItem, ShopRegistry registry, FluxEngine engine,
                                  Map<String, Integer> soldAmounts, Map<String, Double> soldPrices) {
        if (!(shulkerItem.getItemMeta() instanceof BlockStateMeta blockStateMeta)) return 0.0;
        if (!(blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox)) return 0.0;

        ItemStack[] contents = shulkerBox.getInventory().getContents();
        double total = 0.0;
        boolean changed = false;

        for (int i = 0; i < contents.length; i++) {
            ItemStack inner = contents[i];
            if (inner == null || inner.getType().isAir()) continue;

            double earned = processItem(player, inner, registry, engine, soldAmounts, soldPrices);
            if (earned > 0.0) {
                total += earned;
                changed = true;
            }
            contents[i] = (inner.getAmount() <= 0 || inner.getType() == Material.AIR) ? null : inner;
        }

        if (changed) {
            shulkerBox.getInventory().setContents(contents);
            blockStateMeta.setBlockState(shulkerBox);
            shulkerItem.setItemMeta(blockStateMeta);
        }

        return total;
    }

    public SellResult sellAll(Player player) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && !is.getType().isAir()) items.add(is);
        }
        return sell(player, items);
    }

    public SellResult sellAllOf(Player player, String material) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && is.getType().name().equalsIgnoreCase(material)) items.add(is);
        }
        return sell(player, items);
    }

    public String getCategoryForMaterial(String material) {
        if (plugin.getShopModule() == null) return "misc";
        for (var entry : plugin.getShopModule().getRegistry().getCategories().entrySet()) {
            for (var item : entry.getValue().items()) {
                if (item.material().equalsIgnoreCase(material)) return entry.getKey();
            }
        }
        return "misc";
    }

    private double capSellAgainstBuy(String material, double basePrice, int amount, String playerUuid,
                                     double earned, FluxEngine engine, ShopItem shopItem) {
        if (amount <= 0) return 0.0;

        double buyUnit = -1.0;
        if (shopItem.hasBuyOverride()) {
            buyUnit = shopItem.buyPriceOverride();
        } else if (engine != null) {
            buyUnit = engine.getBuyPrice(material, basePrice, playerUuid);
        }

        if (buyUnit <= 0.0) return earned;
        return Math.min(earned, buyUnit * amount);
    }

    public record SellResult(double totalEarned, Map<String, Integer> soldAmounts, List<String> summary) {
        public boolean isEmpty() { return totalEarned <= 0; }
    }
}

package me.fluxmarket.module.shop.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.module.flux.FluxEngine;
import me.fluxmarket.module.shop.ShopItem;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ShopConfirmGui implements FluxGui {

    private static final int[] AMOUNT_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] AMOUNTS = {1, 8, 16, 32, 64, 128, 256};
    private static final int SLOT_BACK = 22;
    private static final int SLOT_CANCEL = 28;
    private static final int SLOT_CONFIRM = 34;

    private final FluxMarket plugin;
    private final Player player;
    private final ShopItem shopItem;
    private final boolean buying; // true = buy, false = sell
    private final Inventory inventory;
    private int selectedAmount = 1;

    public ShopConfirmGui(FluxMarket plugin, Player player, ShopItem shopItem, boolean buying) {
        this.plugin = plugin;
        this.player = player;
        this.shopItem = shopItem;
        this.buying = buying;
        inventory = Bukkit.createInventory(null, 45,
                FormatUtils.comp("&8» " + (buying ? "&aBuy" : "&cSell") + " &8— " +
                        FormatUtils.formatMaterialName(shopItem.material())));
        populate();
    }

    private void populate() {
        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 45; i++) inventory.setItem(i, filler);

        FluxEngine engine = plugin.getFluxModule() != null ? plugin.getFluxModule().getEngine() : null;
        String uuid = player.getUniqueId().toString();
        double base = shopItem.basePrice();

        // Amount buttons
        for (int i = 0; i < AMOUNTS.length; i++) {
            int amt = AMOUNTS[i];
            double total = resolveTotal(engine, uuid, base, amt);
            boolean selected = amt == selectedAmount;
            Material mat = selected ? Material.LIME_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE;
            inventory.setItem(AMOUNT_SLOTS[i], ItemUtils.named(mat,
                    (selected ? "&a" : "&7") + "× " + amt,
                    "&7Total: &f$" + FormatUtils.formatMoney(total)));
        }

        // Item display
        try {
            Material itemMat = Material.valueOf(shopItem.material());
            double total = resolveTotal(engine, uuid, base, selectedAmount);
            double unitPrice = selectedAmount > 0 ? total / selectedAmount : base;
            List<String> lore = new ArrayList<>();
            lore.add("&7Amount: &f" + selectedAmount);
            lore.add("&7Avg. Price: &f$" + FormatUtils.formatMoney(unitPrice));
            lore.add("&7Total: " + (buying ? "&a" : "&c") + "$" + FormatUtils.formatMoney(total));
            inventory.setItem(SLOT_BACK, ItemUtils.named(itemMat,
                    "&f" + FormatUtils.formatMaterialName(shopItem.material()),
                    lore.toArray(new String[0])));
        } catch (IllegalArgumentException ignored) {}

        double confirmTotal = resolveTotal(engine, uuid, base, selectedAmount);
        inventory.setItem(SLOT_CANCEL, ItemUtils.named(Material.RED_STAINED_GLASS_PANE, "&cCancel"));
        String action = buying ? "&aBuy for &f" : "&cSell for &f";
        inventory.setItem(SLOT_CONFIRM, ItemUtils.named(Material.LIME_STAINED_GLASS_PANE,
                action + "$" + FormatUtils.formatMoney(confirmTotal)));
    }

    /**
     * Resolve the total cost/earnings for a given amount, respecting per-item
     * price overrides and the buy/sell direction.
     * For selling, uses simulateBatchSell so bulk dumps are fairly priced.
     */
    private double resolveTotal(FluxEngine engine, String uuid, double base, int amount) {
        if (buying) {
            double unitPrice = shopItem.hasBuyOverride() ? shopItem.buyPriceOverride()
                             : engine != null ? engine.getBuyPrice(shopItem.material(), base, uuid) : base;
            return unitPrice * amount;
        } else {
            if (shopItem.hasSellOverride()) return shopItem.sellPriceOverride() * amount;
            if (engine != null) return engine.simulateBatchSell(shopItem.material(), base, amount, uuid);
            return base * amount * plugin.getConfigManager().getSellMultiplier(player);
        }
    }

    public void open() {
        player.openInventory(inventory);
        GuiListener.open(player.getUniqueId(), this);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        int slot = event.getSlot();

        // Amount buttons
        for (int i = 0; i < AMOUNT_SLOTS.length; i++) {
            if (slot == AMOUNT_SLOTS[i]) {
                selectedAmount = AMOUNTS[i];
                populate();
                return;
            }
        }

        if (slot == SLOT_BACK || slot == SLOT_CANCEL) {
            // Go back to items list
            player.closeInventory();
            return;
        }

        if (slot == SLOT_CONFIRM) {
            executeTransaction();
        }
    }

    private void executeTransaction() {
        FluxEngine engine = plugin.getFluxModule() != null ? plugin.getFluxModule().getEngine() : null;
        String uuid = player.getUniqueId().toString();
        double base = shopItem.basePrice();
        double total = resolveTotal(engine, uuid, base, selectedAmount);
        double unitPrice = selectedAmount > 0 ? total / selectedAmount : base;
        String prefix = plugin.getConfigManager().getPrefix();

        if (buying) {
            // Check balance
            if (!plugin.getEconomyProvider().has(player, total)) {
                player.sendMessage(FormatUtils.color(prefix + "&cNot enough money! Required: &f" + FormatUtils.formatMoney(total)));
                player.closeInventory();
                return;
            }
            // Check inventory space
            Material mat = Material.valueOf(shopItem.material());
            if (player.getInventory().firstEmpty() == -1) {
                player.sendMessage(FormatUtils.color(prefix + "&cYour inventory is full!"));
                player.closeInventory();
                return;
            }
            plugin.getEconomyProvider().withdraw(player, total);
            ItemStack item = new ItemStack(mat, selectedAmount);
            player.getInventory().addItem(item);
            player.sendMessage(FormatUtils.color(prefix + "&aBought: &f" + selectedAmount + "x "
                    + FormatUtils.formatMaterialName(shopItem.material())
                    + " &7for &a" + FormatUtils.formatMoney(total)));
            if (engine != null) engine.recordTransaction(uuid, shopItem.material(), selectedAmount, "BUY", unitPrice);
        } else {
            // Check if player has the items
            Material mat = Material.valueOf(shopItem.material());
            int count = 0;
            for (ItemStack is : player.getInventory().getContents()) {
                if (is != null && is.getType() == mat) count += is.getAmount();
            }
            if (count < selectedAmount) {
                player.sendMessage(FormatUtils.color(prefix + "&cYou only have &f" + count + "x &c"
                        + FormatUtils.formatMaterialName(shopItem.material()) + " &cin your inventory."));
                player.closeInventory();
                return;
            }
            // Remove items
            int toRemove = selectedAmount;
            for (ItemStack is : player.getInventory().getContents()) {
                if (is == null || is.getType() != mat) continue;
                if (is.getAmount() <= toRemove) {
                    toRemove -= is.getAmount();
                    is.setAmount(0);
                } else {
                    is.setAmount(is.getAmount() - toRemove);
                    toRemove = 0;
                }
                if (toRemove == 0) break;
            }
            plugin.getEconomyProvider().deposit(player, total);
            player.sendMessage(FormatUtils.color(prefix + "&aSold: &f" + selectedAmount + "x "
                    + FormatUtils.formatMaterialName(shopItem.material())
                    + " &7for &a" + FormatUtils.formatMoney(total)));
            if (engine != null) engine.recordTransaction(uuid, shopItem.material(), selectedAmount, "SELL", unitPrice);
        }

        try {
            player.playSound(player.getLocation(), Sound.valueOf(plugin.getConfigManager().getSellSound()), 1f, 1f);
        } catch (IllegalArgumentException ignored) {}
        player.closeInventory();
    }
}

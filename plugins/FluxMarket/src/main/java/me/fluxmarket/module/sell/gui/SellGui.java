package me.fluxmarket.module.sell.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.module.sell.SellService;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Drag-and-drop sell GUI.
 * Player drops items in, closes inventory, sold automatically.
 */
public class SellGui implements FluxGui {

    // Slots 0–44 are the sell area, row 5 is UI
    private static final int ROWS = 6;
    private static final int SELL_SLOTS = 45;
    private static final int SLOT_INFO = 45;
    private static final int SLOT_SELL_ALL = 49;
    private static final int SLOT_CLOSE = 53;

    private final FluxMarket plugin;
    private final Player player;
    private final SellService service;
    private final Inventory inventory;

    public SellGui(FluxMarket plugin, Player player, SellService service) {
        this.plugin = plugin;
        this.player = player;
        this.service = service;
        inventory = Bukkit.createInventory(null, ROWS * 9,
                FormatUtils.comp("&8» &6Sell"));
        buildUI();
    }

    private void buildUI() {
        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = SELL_SLOTS; i < ROWS * 9; i++) inventory.setItem(i, filler);
        inventory.setItem(SLOT_INFO, ItemUtils.named(Material.BOOK, "&6Sell Items",
                "&7Place items in the top slots.",
                "&7Everything will be sold on close."));
        inventory.setItem(SLOT_SELL_ALL, ItemUtils.named(Material.HOPPER, "&aSell All Now",
                "&7Sells everything sellable",
                "&7from your inventory."));
        inventory.setItem(SLOT_CLOSE, ItemUtils.named(Material.BARRIER, "&cClose"));
    }

    public void open() {
        player.openInventory(inventory);
        GuiListener.open(player.getUniqueId(), this);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == inventory) {
            int slot = event.getSlot();
            if (slot < SELL_SLOTS) {
                // Allow placing items into sell area
                return;
            }
            event.setCancelled(true);
            if (slot == SLOT_SELL_ALL) {
                // Sell everything from player inventory + anything already in the sell GUI
                returnItemsToPlayer();
                service.sellAll(player);
            } else if (slot == SLOT_CLOSE) {
                player.closeInventory();
            }
        }
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        // Allow drag into sell area slots only
        for (int slot : event.getRawSlots()) {
            if (slot >= SELL_SLOTS && slot < ROWS * 9) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        // Collect items from sell area and sell them
        List<ItemStack> toSell = new ArrayList<>();
        for (int i = 0; i < SELL_SLOTS; i++) {
            ItemStack is = inventory.getItem(i);
            if (is != null && !is.getType().isAir()) {
                toSell.add(is.clone());
                inventory.setItem(i, null);
            }
        }
        if (toSell.isEmpty()) return;

        SellService.SellResult result = service.sell(player, toSell);
        if (!result.isEmpty()) {
            String prefix = plugin.getConfigManager().getPrefix();
            player.sendMessage(FormatUtils.color(prefix + "&aSold &f" + result.soldAmounts().values().stream()
                    .mapToInt(Integer::intValue).sum() + " &aitems for &f"
                    + FormatUtils.formatMoney(result.totalEarned()) + "&a."));
        } else {
            // Return unsellable items to player
            returnItemsToPlayer(toSell);
        }
    }

    private void returnItemsToPlayer() {
        for (int i = 0; i < SELL_SLOTS; i++) {
            ItemStack is = inventory.getItem(i);
            if (is != null && !is.getType().isAir()) {
                player.getInventory().addItem(is.clone());
                inventory.setItem(i, null);
            }
        }
    }

    private void returnItemsToPlayer(List<ItemStack> items) {
        for (ItemStack is : items) {
            if (is != null && !is.getType().isAir()) player.getInventory().addItem(is);
        }
    }
}

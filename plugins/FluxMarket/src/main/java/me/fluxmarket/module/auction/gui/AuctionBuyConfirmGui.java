package me.fluxmarket.module.auction.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.module.auction.AuctionItem;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class AuctionBuyConfirmGui implements FluxGui {

    // Item center-row: cancel left (col 2), item center (col 5), confirm right (col 8)
    private static final int SLOT_CANCEL  = 20;
    private static final int SLOT_ITEM    = 22;
    private static final int SLOT_CONFIRM = 24;

    private final FluxMarket plugin;
    private final Player player;
    private final AuctionItem auction;
    private final AuctionMainGui parent;
    private final Inventory inventory;

    public AuctionBuyConfirmGui(FluxMarket plugin, Player player, AuctionItem auction, AuctionMainGui parent) {
        this.plugin = plugin;
        this.player = player;
        this.auction = auction;
        this.parent = parent;
        this.inventory = Bukkit.createInventory(null, 45,
                FormatUtils.comp("&8» &6Confirm Purchase"));
        populate();
    }

    private void populate() {
        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);

        // Cancel — left of item, red barrel
        inventory.setItem(SLOT_CANCEL, ItemUtils.named(Material.RED_STAINED_GLASS_PANE,
                "&cCancel", "&7Return to the auction browser."));

        // Item display — center
        ItemStack display = auction.getItem().clone();
        var meta = display.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(FormatUtils.comp("&7Seller: &f" + auction.getSellerName()));
        lore.add(FormatUtils.comp("&7Price: &a$" + FormatUtils.formatMoney(auction.getPrice())));
        lore.add(FormatUtils.comp("&7Expires in: &f" + FormatUtils.formatDuration(auction.getRemainingMillis())));
        lore.add(FormatUtils.comp(""));
        lore.add(FormatUtils.comp("&eLeft &7— Buy  &c| &7Right — Cancel"));
        meta.lore(lore);
        display.setItemMeta(meta);
        inventory.setItem(SLOT_ITEM, display);

        // Confirm — right of item, barrel (kleine Truhe)
        inventory.setItem(SLOT_CONFIRM, ItemUtils.named(Material.BARREL,
                "&aBuy Now",
                "&7Cost: &f$" + FormatUtils.formatMoney(auction.getPrice()),
                "&7This will complete immediately."));
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
        if (slot == SLOT_CANCEL) {
            parent.open();
            return;
        }
        if (slot != SLOT_CONFIRM) return;

        String prefix = plugin.getConfigManager().getPrefix();
        if (auction.isExpired()) {
            player.sendMessage(FormatUtils.color(prefix + "&cThis listing has already expired."));
            parent.refresh();
            parent.open();
            return;
        }
        if (plugin.getAuctionModule().getManager().getById(auction.getUuid()) == null) {
            player.sendMessage(FormatUtils.color(prefix + "&cThis listing is no longer available."));
            parent.refresh();
            parent.open();
            return;
        }
        if (!plugin.getEconomyProvider().has(player, auction.getPrice())) {
            player.sendMessage(FormatUtils.color(prefix + "&cNot enough money! Required: &f$" + FormatUtils.formatMoney(auction.getPrice())));
            return;
        }
        if (plugin.getAuctionModule().getManager().buyNow(auction, player.getUniqueId(), player.getName())) {
            player.getInventory().addItem(auction.getItem().clone());
            player.sendMessage(FormatUtils.color(prefix + "&aBought: &f" + auction.getItemDisplayName()
                    + " &afor &f$" + FormatUtils.formatMoney(auction.getPrice())));
            parent.refresh();
            parent.open();
            return;
        }

        player.sendMessage(FormatUtils.color(prefix + "&cThis listing could not be purchased."));
        parent.refresh();
        parent.open();
    }
}

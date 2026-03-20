package me.fluxmarket.module.auction.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.module.auction.AuctionItem;
import me.fluxmarket.module.auction.AuctionManager;
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

public class AuctionBidGui implements FluxGui {

    private static final int[] BID_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final double[] BID_INCREMENTS = {1, 5, 10, 25, 50, 100, 500};
    private static final int SLOT_ITEM   = 22;
    private static final int SLOT_CANCEL = 20;
    private static final int SLOT_BID    = 24;

    private final FluxMarket plugin;
    private final Player player;
    private final AuctionItem auction;
    private final AuctionMainGui parent;
    private final Inventory inventory;
    private double selectedIncrement = 1;

    public AuctionBidGui(FluxMarket plugin, Player player, AuctionItem auction, AuctionMainGui parent) {
        this.plugin = plugin;
        this.player = player;
        this.auction = auction;
        this.parent = parent;
        inventory = Bukkit.createInventory(null, 45,
                FormatUtils.comp("&8» &6Bid — " + auction.getItemDisplayName()));
        populate();
    }

    private void populate() {
        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 45; i++) inventory.setItem(i, filler);

        double minBid = Math.max(auction.getPrice(), auction.getCurrentBid());

        // Bid increment buttons
        for (int i = 0; i < BID_SLOTS.length; i++) {
            double inc = BID_INCREMENTS[i];
            boolean sel = inc == selectedIncrement;
            double totalBid = minBid + inc;
            inventory.setItem(BID_SLOTS[i], ItemUtils.named(
                    sel ? Material.LIME_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE,
                    (sel ? "&a" : "&7") + "+" + FormatUtils.formatMoney(inc),
                    "&7Total Bid: &f" + FormatUtils.formatMoney(totalBid)));
        }

        // Item display
        ItemStack display = auction.getItem();
        var meta = display.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(FormatUtils.comp("&7Current Bid: &f" + FormatUtils.formatMoney(auction.getCurrentBid())));
        lore.add(FormatUtils.comp("&7Minimum Bid: &f" + FormatUtils.formatMoney(minBid + selectedIncrement)));
        lore.add(FormatUtils.comp("&7Expires: &f" + FormatUtils.formatDuration(auction.getRemainingMillis())));
        meta.lore(lore);
        display.setItemMeta(meta);
        inventory.setItem(SLOT_ITEM, display);

        inventory.setItem(SLOT_CANCEL, ItemUtils.named(Material.RED_STAINED_GLASS_PANE, "&cCancel"));
        double myBid = minBid + selectedIncrement;
        inventory.setItem(SLOT_BID, ItemUtils.named(Material.BARREL,
                "&aBid: &f$" + FormatUtils.formatMoney(myBid),
                "&7You are bidding &f$" + FormatUtils.formatMoney(myBid)));
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

        for (int i = 0; i < BID_SLOTS.length; i++) {
            if (slot == BID_SLOTS[i]) {
                selectedIncrement = BID_INCREMENTS[i];
                populate();
                return;
            }
        }

        if (slot == SLOT_CANCEL) { parent.open(); return; }
        if (slot == SLOT_BID) {
            double minBid = Math.max(auction.getPrice(), auction.getCurrentBid());
            double myBid = minBid + selectedIncrement;
            AuctionManager.BidResult result = plugin.getAuctionModule().getManager()
                    .placeBid(auction, player.getUniqueId(), player.getName(), myBid);
            String prefix = plugin.getConfigManager().getPrefix();
            switch (result) {
                case SUCCESS ->  player.sendMessage(FormatUtils.color(prefix + "&aBid of &f" + FormatUtils.formatMoney(myBid) + " &aplaced!"));
                case TOO_LOW -> player.sendMessage(FormatUtils.color(prefix + "&cYour bid is too low."));
                case NO_MONEY -> player.sendMessage(FormatUtils.color(prefix + "&cNot enough money."));
                case OWN_LISTING -> player.sendMessage(FormatUtils.color(prefix + "&cYou cannot bid on your own listing."));
                default -> player.sendMessage(FormatUtils.color(prefix + "&cAn unknown error occurred."));
            }
            player.closeInventory();
        }
    }
}

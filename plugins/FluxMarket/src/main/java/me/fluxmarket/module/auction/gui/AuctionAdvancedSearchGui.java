package me.fluxmarket.module.auction.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.AnvilInputGui;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class AuctionAdvancedSearchGui implements FluxGui {

    private static final int SLOT_TITLE     = 4;
    private static final int SLOT_NAME      = 10;
    private static final int SLOT_MIN_PRICE = 12;
    private static final int SLOT_MAX_PRICE = 14;
    private static final int SLOT_SELLER    = 16;
    private static final int SLOT_SEARCH    = 31;
    private static final int SLOT_CLOSE     = 49;

    private final FluxMarket plugin;
    private final Player player;
    private final AuctionMainGui parent;
    private final Inventory inventory;

    // Current filter state
    private String nameFilter   = null;
    private Double minPrice     = null;
    private Double maxPrice     = null;
    private String sellerFilter = null;

    public AuctionAdvancedSearchGui(FluxMarket plugin, Player player, AuctionMainGui parent) {
        this(plugin, player, parent, null, null, null, null);
    }

    public AuctionAdvancedSearchGui(FluxMarket plugin, Player player, AuctionMainGui parent,
                                    String nameFilter, Double minPrice, Double maxPrice, String sellerFilter) {
        this.plugin       = plugin;
        this.player       = player;
        this.parent       = parent;
        this.nameFilter   = nameFilter;
        this.minPrice     = minPrice;
        this.maxPrice     = maxPrice;
        this.sellerFilter = sellerFilter;

        inventory = Bukkit.createInventory(null, 54, FormatUtils.comp("&8» &6Advanced AH Search"));
        populate();
    }

    private void populate() {
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);

        // Title icon
        inventory.setItem(SLOT_TITLE, ItemUtils.named(Material.SPYGLASS, "&6Advanced AH Search"));

        // Item Name filter
        String nameVal = nameFilter != null ? nameFilter : "any";
        inventory.setItem(SLOT_NAME, ItemUtils.named(Material.NAME_TAG,
                "&eItem Name",
                "&7Current: &f" + nameVal,
                "&eClick to set"));

        // Min Price filter
        String minVal = minPrice != null ? "$" + FormatUtils.formatMoney(minPrice) : "none";
        inventory.setItem(SLOT_MIN_PRICE, ItemUtils.named(Material.GOLD_INGOT,
                "&eMin Price",
                "&7Current: &f" + minVal,
                "&eClick to set"));

        // Max Price filter
        String maxVal = maxPrice != null ? "$" + FormatUtils.formatMoney(maxPrice) : "none";
        inventory.setItem(SLOT_MAX_PRICE, ItemUtils.named(Material.EMERALD,
                "&eMax Price",
                "&7Current: &f" + maxVal,
                "&eClick to set"));

        // Seller Name filter
        String sellerVal = sellerFilter != null ? sellerFilter : "any";
        inventory.setItem(SLOT_SELLER, ItemUtils.named(Material.PLAYER_HEAD,
                "&eSeller Name",
                "&7Current: &f" + sellerVal,
                "&eClick to set"));

        // Search button
        inventory.setItem(SLOT_SEARCH, ItemUtils.named(Material.LIME_STAINED_GLASS_PANE,
                "&aSearch",
                "&7Apply filters and browse results"));

        // Close button
        inventory.setItem(SLOT_CLOSE, ItemUtils.named(Material.BARRIER, "&cClose"));
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

        switch (slot) {
            case SLOT_NAME -> AnvilInputGui.open(plugin, player,
                    "&eItem Name Filter", nameFilter != null ? nameFilter : "",
                    value -> {
                        nameFilter = (value == null || value.isBlank()) ? null : value;
                        new AuctionAdvancedSearchGui(plugin, player, parent,
                                nameFilter, minPrice, maxPrice, sellerFilter).open();
                    });

            case SLOT_MIN_PRICE -> AnvilInputGui.open(plugin, player,
                    "&eMin Price Filter", minPrice != null ? String.valueOf(minPrice) : "",
                    value -> {
                        if (value == null || value.isBlank()) {
                            minPrice = null;
                        } else {
                            try {
                                minPrice = Double.parseDouble(value.replace(",", "."));
                            } catch (NumberFormatException ignored) {
                                player.sendMessage(FormatUtils.color(
                                        plugin.getConfigManager().getPrefix() + "&cInvalid number."));
                                minPrice = null;
                            }
                        }
                        new AuctionAdvancedSearchGui(plugin, player, parent,
                                nameFilter, minPrice, maxPrice, sellerFilter).open();
                    });

            case SLOT_MAX_PRICE -> AnvilInputGui.open(plugin, player,
                    "&eMax Price Filter", maxPrice != null ? String.valueOf(maxPrice) : "",
                    value -> {
                        if (value == null || value.isBlank()) {
                            maxPrice = null;
                        } else {
                            try {
                                maxPrice = Double.parseDouble(value.replace(",", "."));
                            } catch (NumberFormatException ignored) {
                                player.sendMessage(FormatUtils.color(
                                        plugin.getConfigManager().getPrefix() + "&cInvalid number."));
                                maxPrice = null;
                            }
                        }
                        new AuctionAdvancedSearchGui(plugin, player, parent,
                                nameFilter, minPrice, maxPrice, sellerFilter).open();
                    });

            case SLOT_SELLER -> AnvilInputGui.open(plugin, player,
                    "&eSeller Name Filter", sellerFilter != null ? sellerFilter : "",
                    value -> {
                        sellerFilter = (value == null || value.isBlank()) ? null : value;
                        new AuctionAdvancedSearchGui(plugin, player, parent,
                                nameFilter, minPrice, maxPrice, sellerFilter).open();
                    });

            case SLOT_SEARCH -> new AuctionFilteredResultsGui(plugin, player,
                    nameFilter, minPrice, maxPrice, sellerFilter, parent).open();

            case SLOT_CLOSE -> parent.open();
        }
    }
}

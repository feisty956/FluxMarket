package me.fluxmarket.module.shop.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.module.shop.ShopCategory;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ShopCategoryGui implements FluxGui {

    private final FluxMarket plugin;
    private final Player player;
    private final Inventory inventory;
    private final List<ShopCategory> categories;

    public ShopCategoryGui(FluxMarket plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        Collection<ShopCategory> cats = plugin.getShopModule().getRegistry().getCategories().values();
        this.categories = new ArrayList<>(cats);

        int rows = Math.max(1, (int) Math.ceil(categories.size() / 9.0));
        rows = Math.min(rows, 6);
        inventory = Bukkit.createInventory(null, rows * 9,
                FormatUtils.comp("&8» &6Shop &8— Categories"));
        populate();
    }

    private void populate() {
        // Fill all with filler
        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);

        // Center categories in row slots
        for (int i = 0; i < categories.size() && i < inventory.getSize(); i++) {
            ShopCategory cat = categories.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("&7" + cat.items().size() + " items");
            lore.add("");
            lore.add("&eClick to open");
            inventory.setItem(i, ItemUtils.named(cat.icon(),
                    cat.displayName(), lore.toArray(new String[0])));
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
        if (slot < 0 || slot >= categories.size()) return;
        ShopCategory cat = categories.get(slot);
        // Open item list for this category
        new ShopItemsGui(plugin, player, cat).open();
    }
}

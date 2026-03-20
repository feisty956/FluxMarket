package me.fluxmarket.module.sell.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.module.sell.SellDao;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class SellHistoryGui implements FluxGui {

    private final FluxMarket plugin;
    private final Player player;
    private final Inventory inventory;

    public SellHistoryGui(FluxMarket plugin, Player player, List<SellDao.SellEntry> entries) {
        this.plugin = plugin;
        this.player = player;
        inventory = Bukkit.createInventory(null, 54,
                FormatUtils.comp("&8» &6Sell History"));
        populate(entries);
    }

    private void populate(List<SellDao.SellEntry> entries) {
        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) inventory.setItem(i, filler);
        inventory.setItem(49, ItemUtils.named(Material.BARRIER, "&cClose"));

        SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        int slot = 0;
        for (SellDao.SellEntry entry : entries) {
            if (slot >= 45) break;
            Material mat;
            try { mat = Material.valueOf(entry.material()); }
            catch (IllegalArgumentException e) { mat = Material.PAPER; }

            String date = fmt.format(new Date(entry.timestamp()));
            inventory.setItem(slot, ItemUtils.named(mat,
                    "&f" + FormatUtils.formatMaterialName(entry.material()),
                    "&7Amount: &f" + entry.amount(),
                    "&7Earned: &a" + FormatUtils.formatMoney(entry.totalPrice()),
                    "&8" + date));
            slot++;
        }
    }

    public void open() {
        player.openInventory(inventory);
        GuiListener.open(player.getUniqueId(), this);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == inventory && event.getSlot() == 49)
            player.closeInventory();
    }
}

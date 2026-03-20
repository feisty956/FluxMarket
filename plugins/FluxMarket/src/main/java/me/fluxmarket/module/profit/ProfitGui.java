package me.fluxmarket.module.profit;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class ProfitGui implements FluxGui {

    private final FluxMarket plugin;
    private final Player player;
    private final Inventory inv;

    private ProfitGui(FluxMarket plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inv = Bukkit.createInventory(null, 27, FormatUtils.comp("&6Profit Tracker"));
        buildSkeleton();
    }

    public static void open(FluxMarket plugin, Player player) {
        ProfitGui gui = new ProfitGui(plugin, player);
        player.openInventory(gui.inv);
        GuiListener.open(player.getUniqueId(), gui);

        // Load data async then populate on main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            ProfitDao dao = plugin.getProfitDao();
            if (dao == null) return;
            long now = System.currentTimeMillis();
            long day = now - TimeUnit.DAYS.toMillis(1);
            long week = now - TimeUnit.DAYS.toMillis(7);

            double todayProfit = dao.getPlayerProfit(player.getUniqueId(), day);
            double weekProfit = dao.getPlayerProfit(player.getUniqueId(), week);
            double allTimeProfit = dao.getPlayerProfit(player.getUniqueId(), 0L);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Check the player still has this GUI open
                if (!(GuiListener.get(player.getUniqueId()) instanceof ProfitGui)) return;
                gui.populate(todayProfit, weekProfit, allTimeProfit);
            });
        });
    }

    private void buildSkeleton() {
        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        // Fill border-ish slots that aren't used by content
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Header item — slot 4
        inv.setItem(4, ItemUtils.named(Material.GOLD_INGOT, "&6Your Profit Tracker",
                "&7Loading..."));

        // Period slots — placeholders while loading
        inv.setItem(11, ItemUtils.named(Material.PAPER, "&eToday", "&7Loading..."));
        inv.setItem(13, ItemUtils.named(Material.BOOK, "&eThis Week", "&7Loading..."));
        inv.setItem(15, ItemUtils.named(Material.CHEST, "&eAll Time", "&7Loading..."));

        // Close button
        inv.setItem(22, ItemUtils.named(Material.BARRIER, "&cClose"));
    }

    private void populate(double today, double week, double allTime) {
        inv.setItem(4, ItemUtils.named(Material.GOLD_INGOT, "&6Your Profit Tracker",
                "&7Track your buy→sell margin"));

        inv.setItem(11, buildPeriodItem(Material.PAPER, "&eToday",
                today, "&7Last 24 hours"));
        inv.setItem(13, buildPeriodItem(Material.BOOK, "&eThis Week",
                week, "&7Last 7 days"));
        inv.setItem(15, buildPeriodItem(Material.CHEST, "&eAll Time",
                allTime, "&7All recorded profit"));
    }

    private ItemStack buildPeriodItem(Material material, String name, double profit, String subtitle) {
        String profitStr = (profit >= 0 ? "&a+" : "&c") + "$" + FormatUtils.formatMoney(profit);
        return ItemUtils.named(material, name, subtitle, profitStr);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getRawSlot() == 22) {
            player.closeInventory();
        }
    }
}

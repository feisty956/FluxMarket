package me.fluxmarket.module.orders.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.module.orders.Order;
import me.fluxmarket.module.orders.OrdersDao.MailboxEntry;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class OrdersMyGui implements FluxGui {

    private static final int SLOT_BACK = 45;
    private static final int SLOT_MAILBOX = 53;

    private final FluxMarket plugin;
    private final Player player;
    private final List<Order> myOrders; // mutable copy — remove() used on cancel
    private final Inventory inventory;

    public OrdersMyGui(FluxMarket plugin, Player player, List<Order> myOrders) {
        this.plugin = plugin;
        this.player = player;
        this.myOrders = new java.util.ArrayList<>(myOrders);
        inventory = org.bukkit.Bukkit.createInventory(null, 54, FormatUtils.comp("&8[&6Orders&8] &7My Orders"));
        populate(0);
    }

    private void populate(int mailboxCount) {
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);
        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) inventory.setItem(i, filler);

        inventory.setItem(SLOT_BACK, ItemUtils.named(Material.ARROW, "&7Back"));

        String mbName = mailboxCount > 0
                ? "&6Mailbox &c(" + mailboxCount + " item" + (mailboxCount == 1 ? "" : "s") + ")"
                : "&7Mailbox &8(empty)";
        inventory.setItem(SLOT_MAILBOX, ItemUtils.named(Material.CHEST, mbName,
                mailboxCount > 0 ? "&eClick &7to claim your delivered items." : "&7No items to claim."));

        for (int i = 0; i < myOrders.size() && i < 45; i++) {
            inventory.setItem(i, buildOrderItem(myOrders.get(i)));
        }
    }

    private ItemStack buildOrderItem(Order order) {
        int progress = order.getAmountNeeded() > 0
                ? (int) ((double) order.getAmountDelivered() / order.getAmountNeeded() * 10) : 0;
        String bar = "&a" + "|".repeat(progress) + "&8" + "|".repeat(10 - progress);
        boolean done = order.isComplete();

        List<String> lore = new java.util.ArrayList<>();
        lore.add("&7Progress: &f" + order.getAmountDelivered() + " &8/ &f" + order.getAmountNeeded());
        lore.add(bar + " &7" + (int) ((double) order.getAmountDelivered() / order.getAmountNeeded() * 100) + "%");
        lore.add("&7Price/item: &a$" + FormatUtils.formatMoney(order.getPriceEach()));
        lore.add("&7Total paid: &a$" + FormatUtils.formatMoney(order.getTotalValue()));
        if (!done) {
            lore.add("&7Remaining: &e$" + FormatUtils.formatMoney(order.getRemainingValue()));
            lore.add("&7Expires: &f" + FormatUtils.formatDuration(order.getRemainingMillis()));
            lore.add("");
            lore.add("&cRight-click &7to cancel and refund");
        } else {
            lore.add("");
            lore.add("&aLeft-click &7to claim this order's items");
        }

        return ItemUtils.named(order.getMaterial(),
                (done ? "&a" : "&f") + FormatUtils.formatMaterialName(order.getMaterial().name()),
                lore.toArray(new String[0]));
    }

    public void open() {
        player.openInventory(inventory);
        GuiListener.open(player.getUniqueId(), this);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            int count = plugin.getOrdersModule().getDao().countMailbox(player.getUniqueId());
            plugin.getServer().getScheduler().runTask(plugin, () -> populate(count));
        });
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        int slot = event.getSlot();

        if (slot == SLOT_BACK) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                var active = plugin.getOrdersModule().getManager().getActiveOrders();
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> new OrdersMainGui(plugin, player, active).open());
            });
            return;
        }

        if (slot == SLOT_MAILBOX) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                List<MailboxEntry> entries = plugin.getOrdersModule().getDao().loadMailbox(player.getUniqueId());
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> new OrderClaimGui(plugin, player, entries).open());
            });
            return;
        }

        if (slot >= 45 || slot >= myOrders.size()) return;
        Order order = myOrders.get(slot);

        if (event.isLeftClick() && order.isComplete()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                List<MailboxEntry> entries = plugin.getOrdersModule().getDao().loadMailbox(player.getUniqueId())
                        .stream()
                        .filter(entry -> entry.orderUuid().equals(order.getUuid()))
                        .toList();
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> new OrderClaimGui(plugin, player, entries).open());
            });
            return;
        }

        if (event.isRightClick()) {
            if (order.isComplete()) {
                player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                        + "&cCompleted orders can be claimed with left-click."));
                return;
            }
            order.setStatus(Order.Status.CANCELLED);
            plugin.getOrdersModule().getDao().save(order);
            plugin.getOrdersModule().getManager().removeOrder(order.getUuid());
            double refund = order.getRemainingValue();
            plugin.getEconomyProvider().deposit(player, refund);
            player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&aOrder cancelled. Refund: &f+" + FormatUtils.formatMoney(refund)));
            myOrders.remove(slot);
            populate(0);
        }
    }
}

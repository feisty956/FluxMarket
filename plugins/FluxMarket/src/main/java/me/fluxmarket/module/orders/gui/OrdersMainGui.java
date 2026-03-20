package me.fluxmarket.module.orders.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.module.orders.Order;
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
import java.util.concurrent.CopyOnWriteArrayList;

public class OrdersMainGui implements FluxGui {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_MAILBOX = 47;
    private static final int SLOT_CREATE = 49;
    private static final int SLOT_MY = 51;
    private static final int SLOT_NEXT = 53;

    private final FluxMarket plugin;
    private final Player player;
    private final Inventory inventory;
    private int page = 0;
    private List<Order> orders = new ArrayList<>();

    public OrdersMainGui(FluxMarket plugin, Player player, List<Order> orders) {
        this.plugin = plugin;
        this.player = player;
        this.orders = new CopyOnWriteArrayList<>(orders);
        inventory = Bukkit.createInventory(null, 54,
                FormatUtils.comp("&8» &6Orders"));
        populate();
    }

    private void populate() {
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);
        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) inventory.setItem(i, filler);

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && (start + i) < orders.size(); i++) {
            Order order = orders.get(start + i);
            inventory.setItem(i, buildOrderItem(order));
        }

        if (page > 0) inventory.setItem(SLOT_PREV, ItemUtils.named(Material.ARROW, "&7Previous Page"));
        if ((page + 1) * PAGE_SIZE < orders.size())
            inventory.setItem(SLOT_NEXT, ItemUtils.named(Material.ARROW, "&7Next Page"));

        inventory.setItem(SLOT_CREATE, ItemUtils.named(Material.WRITABLE_BOOK, "&aCreate New Order",
                "&7Click to place a new",
                "&7buy order"));
        int mailboxCount = plugin.getOrdersModule().getDao().countMailbox(player.getUniqueId());
        inventory.setItem(SLOT_MAILBOX, ItemUtils.named(
                mailboxCount > 0 ? Material.CHEST_MINECART : Material.MINECART,
                mailboxCount > 0 ? "&6Order Mailbox &c(" + mailboxCount + ")" : "&7Order Mailbox",
                mailboxCount > 0 ? "&eClick &7to claim delivered items." : "&7No delivered items waiting."));
        inventory.setItem(SLOT_MY, ItemUtils.named(Material.CHEST, "&eMy Orders",
                "&7Manage your own orders"));
    }

    private ItemStack buildOrderItem(Order order) {
        Material mat = order.getMaterial();
        List<String> lore = new ArrayList<>();
        lore.add("&7Buyer: &f" + order.getCreatorName());
        lore.add("&7Amount: &f" + order.getAmountDelivered() + "&8/" + order.getAmountNeeded());
        lore.add("&7Price/item: &a" + FormatUtils.formatMoney(order.getPriceEach()));
        lore.add("&7Remaining value: &a" + FormatUtils.formatMoney(order.getRemainingValue()));
        lore.add("&7Expires: &f" + FormatUtils.formatDuration(order.getRemainingMillis()));
        // Progress bar
        int progress = (int) ((double) order.getAmountDelivered() / order.getAmountNeeded() * 10);
        String bar = "&a" + "█".repeat(progress) + "&8" + "░".repeat(10 - progress);
        lore.add(bar + " &7" + (int)((double)order.getAmountDelivered()/order.getAmountNeeded()*100) + "%");
        lore.add("");
        if (!order.getCreatorUuid().equals(player.getUniqueId()))
            lore.add("&eClick &7— Deliver items");
        else
            lore.add("&cRight-click &7— Cancel order");

        return ItemUtils.named(mat, "&f" + FormatUtils.formatMaterialName(mat.name()),
                lore.toArray(new String[0]));
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

        if (slot == SLOT_PREV && page > 0) { page--; populate(); return; }
        if (slot == SLOT_NEXT) { page++; populate(); return; }
        if (slot == SLOT_CREATE) {
            OrderCreateGui gui = new OrderCreateGui(plugin, player);
            gui.open();
            return;
        }
        if (slot == SLOT_MAILBOX) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                var mailbox = plugin.getOrdersModule().getDao().loadMailbox(player.getUniqueId());
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> new OrderClaimGui(plugin, player, mailbox).open());
            });
            return;
        }
        if (slot == SLOT_MY) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                var myOrders = plugin.getOrdersModule().getDao().loadByPlayer(player.getUniqueId());
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> new OrdersMyGui(plugin, player, myOrders).open());
            });
            return;
        }

        int idx = page * PAGE_SIZE + slot;
        if (slot >= PAGE_SIZE || idx >= orders.size()) return;
        Order order = orders.get(idx);

        if (order.getCreatorUuid().equals(player.getUniqueId())) {
            if (event.isRightClick()) {
                // Cancel order
                order.setStatus(Order.Status.CANCELLED);
                plugin.getOrdersModule().getDao().save(order);
                plugin.getOrdersModule().getManager().removeOrder(order.getUuid());
                // Refund unspent money
                double refund = order.getRemainingValue();
                if (refund > 0) {
                    plugin.getEconomyProvider().deposit(player, refund);
                    player.sendMessage(plugin.getConfigManager().getPrefix()
                            + "&aOrder cancelled. Refund: &f" + FormatUtils.formatMoney(refund));
                }
                orders.remove(idx);
                populate();
            }
        } else {
            // Deliver items
            new OrderDeliverGui(plugin, player, order, this).open();
        }
    }
}

package me.fluxmarket.module.orders.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.module.orders.Order;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AdminOrdersGui implements FluxGui {

    public enum SortMode { NEWEST, OLDEST, HIGHEST_VALUE, MOST_DELIVERED }

    private static final int PAGE_SIZE  = 45;
    private static final int SLOT_PREV  = 45;
    private static final int SLOT_SORT  = 46;
    private static final int SLOT_INFO  = 49;
    private static final int SLOT_NEXT  = 53;

    private final FluxMarket plugin;
    private final Player player;
    private final Inventory inventory;
    private List<Order> orders;
    private SortMode sort = SortMode.NEWEST;
    private int page = 0;

    public AdminOrdersGui(FluxMarket plugin, Player player, List<Order> orders) {
        this.plugin = plugin;
        this.player = player;
        this.orders = new CopyOnWriteArrayList<>(orders);
        inventory = Bukkit.createInventory(null, 54,
                FormatUtils.comp("&8» &cAdmin Orders"));
        applySort();
        populate();
    }

    private void applySort() {
        orders.sort(switch (sort) {
            case NEWEST         -> Comparator.comparingLong(Order::getCreatedAt).reversed();
            case OLDEST         -> Comparator.comparingLong(Order::getCreatedAt);
            case HIGHEST_VALUE  -> Comparator.comparingDouble(Order::getTotalValue).reversed();
            case MOST_DELIVERED -> Comparator.comparingInt(Order::getAmountDelivered).reversed();
        });
    }

    private void populate() {
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);
        ItemStack filler = ItemUtils.filler(Material.RED_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) inventory.setItem(i, filler);

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && (start + i) < orders.size(); i++) {
            inventory.setItem(i, buildOrderItem(orders.get(start + i)));
        }

        if (page > 0) {
            inventory.setItem(SLOT_PREV, ItemUtils.named(Material.ARROW, "&7Previous Page"));
        }
        if ((page + 1) * PAGE_SIZE < orders.size()) {
            inventory.setItem(SLOT_NEXT, ItemUtils.named(Material.ARROW, "&7Next Page"));
        }

        inventory.setItem(SLOT_SORT, ItemUtils.named(Material.HOPPER,
                "&eSort: &f" + sortName(), "&7Click to cycle"));

        inventory.setItem(SLOT_INFO, ItemUtils.named(Material.COMMAND_BLOCK,
                "&cAdmin Orders &8\u2014 All Active",
                "&7Total orders: &f" + orders.size(),
                "&7Right-click any order to cancel it",
                "&7with a full refund to creator"));
    }

    private ItemStack buildOrderItem(Order order) {
        Material mat = order.getMaterial();
        List<String> lore = new ArrayList<>();
        lore.add("&7Creator: &f" + order.getCreatorName());
        lore.add("&7Material: &f" + FormatUtils.formatMaterialName(mat.name()));
        lore.add("&7Amount: &f" + order.getAmountDelivered() + "&8/" + order.getAmountNeeded());
        lore.add("&7Price/item: &a$" + FormatUtils.formatMoney(order.getPriceEach()));
        lore.add("&7Total value: &a$" + FormatUtils.formatMoney(order.getTotalValue()));
        lore.add("&7Remaining: &a$" + FormatUtils.formatMoney(order.getRemainingValue()));
        lore.add("&7Expires in: &f" + FormatUtils.formatDuration(order.getRemainingMillis()));

        int progress = order.getAmountNeeded() > 0
                ? (int) ((double) order.getAmountDelivered() / order.getAmountNeeded() * 10)
                : 0;
        String bar = "&a" + "\u2588".repeat(progress) + "&8" + "\u2591".repeat(10 - progress);
        lore.add(bar + " &7" + (int) ((double) order.getAmountDelivered() / order.getAmountNeeded() * 100) + "%");
        lore.add("");
        lore.add("&cRight-click &7\u2014 Cancel & refund creator");

        return ItemUtils.named(mat,
                "&f" + FormatUtils.formatMaterialName(mat.name()),
                lore.toArray(new String[0]));
    }

    private String sortName() {
        return switch (sort) {
            case NEWEST         -> "Newest";
            case OLDEST         -> "Oldest";
            case HIGHEST_VALUE  -> "Highest Value";
            case MOST_DELIVERED -> "Most Delivered";
        };
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
        if (slot == SLOT_NEXT && (page + 1) * PAGE_SIZE < orders.size()) { page++; populate(); return; }
        if (slot == SLOT_SORT) {
            sort = SortMode.values()[(sort.ordinal() + 1) % SortMode.values().length];
            applySort();
            populate();
            return;
        }

        if (slot >= PAGE_SIZE) return;
        int idx = page * PAGE_SIZE + slot;
        if (idx >= orders.size()) return;

        if (event.getClick() == ClickType.RIGHT) {
            Order order = orders.get(idx);
            cancelOrderWithRefund(order, idx);
        }
    }

    private void cancelOrderWithRefund(Order order, int listIndex) {
        order.setStatus(Order.Status.CANCELLED);
        plugin.getOrdersModule().getDao().save(order);
        plugin.getOrdersModule().getManager().removeOrder(order.getUuid());

        double refund = order.getRemainingValue();
        if (refund > 0) {
            // Refund to the order creator
            org.bukkit.OfflinePlayer creator = Bukkit.getOfflinePlayer(order.getCreatorUuid());
            plugin.getEconomyProvider().deposit(creator, refund);

            // Notify creator if online
            org.bukkit.entity.Player online = Bukkit.getPlayer(order.getCreatorUuid());
            if (online != null) {
                online.sendMessage(plugin.getConfigManager().getPrefix()
                        + FormatUtils.color("&cAn admin cancelled your order for &f"
                        + FormatUtils.formatMaterialName(order.getMaterial().name())
                        + "&c. Refund: &f$" + FormatUtils.formatMoney(refund)));
            }
        }

        orders.remove(listIndex);
        if (page > 0 && page * PAGE_SIZE >= orders.size()) page--;
        populate();

        player.sendMessage(plugin.getConfigManager().getPrefix()
                + FormatUtils.color("&aOrder cancelled. Refunded &f$"
                + FormatUtils.formatMoney(refund) + " &ato &f" + order.getCreatorName()));
    }
}

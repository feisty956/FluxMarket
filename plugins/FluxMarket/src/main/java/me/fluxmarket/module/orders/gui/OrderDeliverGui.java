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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class OrderDeliverGui implements FluxGui {

    private static final int DELIVER_SLOTS = 45;
    private static final int SLOT_INFO = 49;

    private final FluxMarket plugin;
    private final Player player;
    private final Order order;
    private final OrdersMainGui parent;
    private final Inventory inventory;

    public OrderDeliverGui(FluxMarket plugin, Player player, Order order, OrdersMainGui parent) {
        this.plugin = plugin;
        this.player = player;
        this.order = order;
        this.parent = parent;
        inventory = Bukkit.createInventory(null, 54,
                FormatUtils.comp("&8> &6Deliver: " + FormatUtils.formatMaterialName(order.getMaterial().name())));
        buildUI();
    }

    private void buildUI() {
        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = DELIVER_SLOTS; i < 54; i++) inventory.setItem(i, filler);
        inventory.setItem(SLOT_INFO, ItemUtils.named(order.getMaterial(),
                "&f" + FormatUtils.formatMaterialName(order.getMaterial().name()),
                "&7Still needed: &e" + order.getRemainingAmount(),
                "&7Price/item: &a$" + FormatUtils.formatMoney(order.getPriceEach()),
                "&7Max payout: &a$" + FormatUtils.formatMoney(order.getRemainingValue()),
                "",
                "&7Place &f" + FormatUtils.formatMaterialName(order.getMaterial().name())
                        + " &7(or shulkers) in the slots.",
                "&eClose &7to confirm delivery."));
    }

    public void open() {
        player.openInventory(inventory);
        GuiListener.open(player.getUniqueId(), this);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == inventory && event.getSlot() >= DELIVER_SLOTS) {
            event.setCancelled(true);
        }
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        for (int slot : event.getRawSlots()) {
            if (slot >= DELIVER_SLOTS && slot < 54) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        List<ItemStack> placed = new ArrayList<>();
        for (int i = 0; i < DELIVER_SLOTS; i++) {
            ItemStack is = inventory.getItem(i);
            if (is != null && !is.getType().isAir()) {
                placed.add(is.clone());
                inventory.setItem(i, null);
            }
        }
        if (placed.isEmpty()) return;

        List<ItemStack> matching = new ArrayList<>();
        List<ItemStack> wrong = new ArrayList<>();

        for (ItemStack is : placed) {
            if (ItemUtils.isShulkerBox(is.getType())) {
                for (ItemStack inner : ItemUtils.getShulkerContents(is)) {
                    if (inner.getType() == order.getMaterial()) {
                        matching.add(inner.clone());
                    }
                }
            } else if (is.getType() == order.getMaterial()) {
                matching.add(is.clone());
            } else {
                wrong.add(is.clone());
            }
        }

        for (ItemStack w : wrong) player.getInventory().addItem(w);

        if (matching.isEmpty()) {
            player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cNo matching items found - nothing delivered."));
            return;
        }

        int totalMatching = matching.stream().mapToInt(ItemStack::getAmount).sum();
        int accepted = Math.min(totalMatching, order.getRemainingAmount());
        int excess = totalMatching - accepted;

        if (accepted <= 0) {
            for (ItemStack is : matching) player.getInventory().addItem(is);
            player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cThis order is already fully fulfilled."));
            return;
        }

        double earned = accepted * order.getPriceEach();
        order.addDelivery(accepted);
        plugin.getEconomyProvider().deposit(player, earned);
        plugin.getOrdersModule().getDao().save(order);

        int toStore = accepted;
        for (ItemStack is : matching) {
            if (toStore <= 0) break;
            int take = Math.min(is.getAmount(), toStore);
            ItemStack stored = is.clone();
            stored.setAmount(take);
            plugin.getOrdersModule().getDao().saveMailboxItem(
                    order.getUuid(), order.getCreatorUuid(), stored);
            toStore -= take;
        }

        if (excess > 0) {
            int toReturn = excess;
            for (ItemStack is : matching) {
                if (toReturn <= 0) break;
                int ret = Math.min(is.getAmount(), toReturn);
                if (ret > 0) {
                    ItemStack returned = is.clone();
                    returned.setAmount(ret);
                    player.getInventory().addItem(returned);
                    toReturn -= ret;
                }
            }
            if (toReturn > 0) {
                player.getInventory().addItem(new ItemStack(order.getMaterial(), toReturn));
            }
        }

        String prefix = plugin.getConfigManager().getPrefix();
        player.sendMessage(FormatUtils.color(prefix + "&aDelivered &f" + accepted + "x "
                + FormatUtils.formatMaterialName(order.getMaterial().name())
                + " &afor &f+" + FormatUtils.formatMoney(earned)));

        var creator = Bukkit.getPlayer(order.getCreatorUuid());
        if (creator != null) {
            if (order.isComplete()) {
                creator.sendMessage(FormatUtils.color(prefix
                        + "&aYour order for &f" + order.getAmountNeeded() + "x "
                        + FormatUtils.formatMaterialName(order.getMaterial().name())
                        + " &ahas been fully fulfilled! Check &e/orders &ato claim your items."));
            } else {
                creator.sendMessage(FormatUtils.color(prefix
                        + "&f" + accepted + "x "
                        + FormatUtils.formatMaterialName(order.getMaterial().name())
                        + " &adelivered to your order. &e/orders &ato claim."));
            }
        }

        if (order.isComplete()) {
            plugin.getOrdersModule().getManager().removeOrder(order.getUuid());
        }
    }
}

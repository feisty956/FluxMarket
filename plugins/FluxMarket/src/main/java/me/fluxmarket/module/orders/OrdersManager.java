package me.fluxmarket.module.orders;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.util.FormatUtils;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OrdersManager {

    private final FluxMarket plugin;
    private final OrdersDao dao;
    private final Map<UUID, Order> orders = new ConcurrentHashMap<>();
    private BukkitTask expiryTask;

    public OrdersManager(FluxMarket plugin, OrdersDao dao) {
        this.plugin = plugin;
        this.dao = dao;
    }

    public void load() {
        orders.clear();
        List<Order> loaded = dao.loadActive();
        for (Order o : loaded) orders.put(o.getUuid(), o);
        plugin.getLogger().info("OrdersManager: loaded " + orders.size() + " active orders.");

        // Check expiry every minute
        expiryTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkExpiry, 1200L, 1200L);
    }

    public void stop() {
        if (expiryTask != null) expiryTask.cancel();
    }

    private void checkExpiry() {
        List<Order> expired = orders.values().stream().filter(Order::isExpired).toList();
        for (Order o : expired) {
            o.setStatus(Order.Status.EXPIRED);
            orders.remove(o.getUuid());
            dao.save(o);
            // Refund remaining balance
            double refund = o.getRemainingValue();
            if (refund > 0) {
                plugin.getEconomyProvider().deposit(Bukkit.getOfflinePlayer(o.getCreatorUuid()), refund);
                var creator = Bukkit.getPlayer(o.getCreatorUuid());
                if (creator != null) {
                    creator.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                            + "&eYour order for &f" + FormatUtils.formatMaterialName(o.getMaterial().name())
                            + " &ehas expired. Refund: &a+" + FormatUtils.formatMoney(refund)));
                }
            }
        }
    }

    public void addOrder(Order order) { orders.put(order.getUuid(), order); }
    public void removeOrder(UUID uuid) { orders.remove(uuid); }
    public Collection<Order> getAll() { return Collections.unmodifiableCollection(orders.values()); }
    public Order getById(UUID uuid) { return orders.get(uuid); }

    public int countByPlayer(UUID uuid) {
        return (int) orders.values().stream().filter(o -> o.getCreatorUuid().equals(uuid)).count();
    }

    public List<Order> getActiveOrders() {
        return orders.values().stream()
                .filter(o -> o.getStatus() == Order.Status.ACTIVE && !o.isExpired())
                .sorted(Comparator.comparingLong(Order::getCreatedAt).reversed())
                .toList();
    }
}

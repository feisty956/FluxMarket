package me.fluxmarket.module.orders;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.module.orders.gui.AdminOrdersGui;
import me.fluxmarket.module.orders.gui.OrderCreateGui;
import me.fluxmarket.module.orders.gui.OrderSignInput;
import me.fluxmarket.module.orders.gui.OrdersMainGui;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class OrdersModule {

    private final FluxMarket plugin;
    private final OrdersDao dao;
    private final OrdersManager manager;

    public OrdersModule(FluxMarket plugin) {
        this.plugin = plugin;
        this.dao = new OrdersDao(plugin);
        this.manager = new OrdersManager(plugin, dao);
    }

    public void enable() {
        manager.load();
        plugin.getServer().getPluginManager().registerEvents(new OrderSignInput(), plugin);
        registerCommand();
        plugin.getLogger().info("ORDERS module enabled.");
    }

    public void disable() {
        manager.stop();
    }

    private void registerCommand() {
        var cmd = plugin.getCommand("orders");
        if (cmd == null) return;
        CommandExecutor exec = (sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("player-only"));
                return true;
            }
            if (!player.hasPermission("fluxmarket.orders.use")) {
                player.sendMessage(plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("no-permission"));
                return true;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("create")) {
                new OrderCreateGui(plugin, player).open();
            } else if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
                if (!player.hasPermission("fluxmarket.orders.admin")) {
                    player.sendMessage(plugin.getConfigManager().getPrefix()
                            + plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                var orders = manager.getActiveOrders();
                new AdminOrdersGui(plugin, player, orders).open();
            } else {
                // Load orders async, open GUI sync
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    var orders = manager.getActiveOrders();
                    plugin.getServer().getScheduler().runTask(plugin,
                            () -> new OrdersMainGui(plugin, player, orders).open());
                });
            }
            return true;
        };
        cmd.setExecutor(exec);
        cmd.setTabCompleter((TabCompleter) (sender, command, alias, args) ->
                args.length == 1 ? List.of("create", "admin") : List.of());
    }

    public OrdersDao getDao() { return dao; }
    public OrdersManager getManager() { return manager; }
}

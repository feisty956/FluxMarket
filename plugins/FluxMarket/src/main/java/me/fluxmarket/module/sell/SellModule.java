package me.fluxmarket.module.sell;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.module.sell.gui.SellGui;
import me.fluxmarket.module.sell.gui.SellHistoryGui;
import me.fluxmarket.module.sell.gui.SellProgressGui;
import me.fluxmarket.module.sell.gui.SellTopGui;
import me.fluxmarket.module.sell.gui.WorthGui;
import me.fluxmarket.module.sell.wand.SellWand;
import me.fluxmarket.module.sell.wand.SellWandListener;
import me.fluxmarket.module.sell.wand.SellWandManager;
import me.fluxmarket.module.shop.ShopItem;
import me.fluxmarket.module.shop.ShopRegistry;
import me.fluxmarket.util.FormatUtils;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SellModule {

    private final FluxMarket plugin;
    private final SellDao dao;
    private final SellService service;
    private final SellWandManager wandManager;

    public SellModule(FluxMarket plugin) {
        this.plugin = plugin;
        this.dao = new SellDao(plugin);
        this.service = new SellService(plugin, dao);
        this.wandManager = new SellWandManager(plugin);
    }

    public void enable() {
        registerSellCommand();
        registerWorthCommand();
        registerHistoryCommand();
        registerSellTopCommand();
        registerSellProgressCommand();
        registerSellWandCommand();
        if (plugin.getConfigManager().isSellWandsEnabled()) {
            plugin.getServer().getPluginManager().registerEvents(
                    new SellWandListener(plugin, wandManager, service), plugin);
        }
        // Pre-load sell progression cache when player joins
        plugin.getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                plugin.getSellProgressManager().loadPlayer(e.getPlayer().getUniqueId());
            }
            @org.bukkit.event.EventHandler
            public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
                plugin.getSellProgressManager().evict(e.getPlayer().getUniqueId());
            }
        }, plugin);
        plugin.getLogger().info("SELL module enabled.");
    }

    public void disable() {}

    private void registerSellCommand() {
        var cmd = plugin.getCommand("sell");
        if (cmd == null) return;
        CommandExecutor exec = (sender, command, label, args) -> {
            if (!(sender instanceof org.bukkit.entity.Player player)) {
                sender.sendMessage(plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("player-only"));
                return true;
            }
            if (!player.hasPermission("fluxmarket.sell.use")) {
                player.sendMessage(plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("no-permission"));
                return true;
            }
            String prefix = plugin.getConfigManager().getPrefix();

            if (args.length == 0) {
                if (plugin.getConfigManager().isSellGuiEnabled()) {
                    new SellGui(plugin, player, service).open();
                } else {
                    player.sendMessage(FormatUtils.color(prefix + "&cUsage: /sell hand | /sell all [item]"));
                }
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "hand" -> {
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (hand.getType().isAir()) {
                        player.sendMessage(FormatUtils.color(prefix + "&cHold an item in your hand."));
                        return true;
                    }
                    List<ItemStack> single = new ArrayList<>();
                    single.add(hand);
                    SellService.SellResult result = service.sell(player, single);
                    if (result.isEmpty()) {
                        player.sendMessage(FormatUtils.color(prefix + "&cThis item cannot be sold."));
                    }
                }
                case "all" -> {
                    if (args.length >= 2) {
                        SellService.SellResult result = service.sellAllOf(player, args[1].toUpperCase());
                        if (result.isEmpty()) {
                            player.sendMessage(FormatUtils.color(prefix + "&cThis item cannot be sold."));
                        }
                    } else {
                        SellService.SellResult result = service.sellAll(player);
                        if (result.isEmpty()) {
                            player.sendMessage(FormatUtils.color(prefix + "&cNo sellable items in your inventory."));
                        }
                    }
                }
                default -> player.sendMessage(FormatUtils.color(prefix + "&cUsage: /sell | /sell hand | /sell all [item]"));
            }
            return true;
        };
        cmd.setExecutor(exec);
        cmd.setTabCompleter((TabCompleter) (sender, command, alias, args) -> {
            if (args.length == 1) return List.of("hand", "all");
            if (args.length == 2 && args[0].equalsIgnoreCase("all")) return getMaterials();
            return List.of();
        });
    }

    private void registerWorthCommand() {
        var cmd = plugin.getCommand("worth");
        if (cmd == null) return;
        cmd.setExecutor((CommandExecutor) (sender, command, label, args) -> {
            if (!(sender instanceof org.bukkit.entity.Player player)) return true;
            if (!player.hasPermission("fluxmarket.sell.worth")) {
                player.sendMessage(plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("no-permission"));
                return true;
            }
            if (args.length == 0) {
                new WorthGui(plugin, player).open();
                return true;
            }
            ShopRegistry registry = plugin.getShopModule() != null ? plugin.getShopModule().getRegistry() : null;
            if (registry == null) return true;
            String prefix = plugin.getConfigManager().getPrefix();
            String material = args[0].toUpperCase();
            ShopItem shopItem = registry.findItem(material);
            if (shopItem == null || (!shopItem.sellEnabled() && !shopItem.buyEnabled())) {
                player.sendMessage(FormatUtils.color(prefix + "&cThis item is not tradeable."));
                return true;
            }
            double base = shopItem.basePrice();
            double sell = plugin.getFluxModule() != null
                    ? plugin.getFluxModule().getEngine().getSellPrice(material, base, player.getUniqueId().toString())
                    : base;
            double buy = plugin.getFluxModule() != null
                    ? plugin.getFluxModule().getEngine().getBuyPrice(material, base, player.getUniqueId().toString())
                    : base;
            player.sendMessage(FormatUtils.color(prefix + "&f" + FormatUtils.formatMaterialName(material)
                    + " &8- &7Sell: &c$" + FormatUtils.formatMoney(sell)
                    + " &8| &7Buy: &a$" + FormatUtils.formatMoney(buy)));
            return true;
        });
        cmd.setTabCompleter((TabCompleter) (sender, command, alias, args) ->
                args.length == 1 ? getMaterials() : List.of());
    }

    private void registerSellTopCommand() {
        var cmd = plugin.getCommand("selltop");
        if (cmd == null) return;
        cmd.setExecutor((CommandExecutor) (sender, command, label, args) -> {
            if (!(sender instanceof org.bukkit.entity.Player player)) return true;
            if (!player.hasPermission("fluxmarket.sell.top")) {
                player.sendMessage(plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("no-permission"));
                return true;
            }
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                var entries = dao.getSellTop(plugin.getConfigManager().getSellTopSize());
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> new SellTopGui(plugin, player, entries).open());
            });
            return true;
        });
    }

    private void registerSellProgressCommand() {
        var cmd = plugin.getCommand("sellprogress");
        if (cmd == null) return;
        cmd.setExecutor((CommandExecutor) (sender, command, label, args) -> {
            if (!(sender instanceof org.bukkit.entity.Player player)) return true;
            if (!player.hasPermission("fluxmarket.sell.progress")) {
                player.sendMessage(plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("no-permission"));
                return true;
            }
            plugin.getSellProgressManager().loadPlayer(player.getUniqueId());
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> new SellProgressGui(plugin, player).open(), 2L);
            return true;
        });
    }

    private void registerSellWandCommand() {
        var cmd = plugin.getCommand("sellwand");
        if (cmd == null) return;
        cmd.setExecutor((CommandExecutor) (sender, command, label, args) -> {
            if (!sender.hasPermission("fluxmarket.sell.wand")) {
                sender.sendMessage(plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("no-permission"));
                return true;
            }
            String prefix = plugin.getConfigManager().getPrefix();
            if (args.length < 1 || !args[0].equalsIgnoreCase("give") || args.length < 2) {
                sender.sendMessage(FormatUtils.color(prefix + "&cUsage: /sellwand give <player> [use|time] [amount]"));
                return true;
            }
            org.bukkit.entity.Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(FormatUtils.color(prefix + "&cPlayer not found."));
                return true;
            }
            SellWand.WandType type = (args.length >= 3 && args[2].equalsIgnoreCase("time"))
                    ? SellWand.WandType.TIME : SellWand.WandType.USE;
            int amount = plugin.getConfigManager().getSellWandDefaultUses();
            if (args.length >= 4) {
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException ignored) {}
            }
            long expiresAt = type == SellWand.WandType.TIME
                    ? System.currentTimeMillis() + (long) plugin.getConfigManager().getSellWandDefaultDurationDays() * 86_400_000L
                    : 0L;
            ItemStack wand = wandManager.createWand(target.getUniqueId(), target.getName(), type, amount, expiresAt);
            target.getInventory().addItem(wand);
            sender.sendMessage(FormatUtils.color(prefix + "&aGave &f" + target.getName() + " &aa sell wand (&f"
                    + (type == SellWand.WandType.USE ? amount + " uses" : "time-based") + "&a)."));
            return true;
        });
        cmd.setTabCompleter((TabCompleter) (sender, command, alias, args) -> {
            if (args.length == 1) return List.of("give");
            if (args.length == 3) return List.of("use", "time");
            return List.of();
        });
    }

    private void registerHistoryCommand() {
        var cmd = plugin.getCommand("sellhistory");
        if (cmd == null) return;
        cmd.setExecutor((CommandExecutor) (sender, command, label, args) -> {
            if (!(sender instanceof org.bukkit.entity.Player player)) return true;
            if (!player.hasPermission("fluxmarket.sell.history")) {
                player.sendMessage(plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("no-permission"));
                return true;
            }
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                var entries = dao.getHistory(player.getUniqueId(), plugin.getConfigManager().getSellHistorySize());
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        new SellHistoryGui(plugin, player, entries).open());
            });
            return true;
        });
    }

    private List<String> getMaterials() {
        if (plugin.getShopModule() == null) return List.of();
        List<String> result = new ArrayList<>();
        for (var cat : plugin.getShopModule().getRegistry().getCategories().values()) {
            for (var item : cat.items()) result.add(item.material());
        }
        return result;
    }

    public SellService getService() { return service; }
    public SellDao getDao() { return dao; }
    public SellWandManager getWandManager() { return wandManager; }
}

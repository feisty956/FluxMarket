package me.fluxmarket.module.auction;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.module.auction.gui.AuctionHistoryGui;
import me.fluxmarket.module.auction.gui.AuctionMainGui;
import me.fluxmarket.util.FormatUtils;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class AuctionModule {

    private final FluxMarket plugin;
    private final AuctionDao dao;
    private final AuctionManager manager;

    public AuctionModule(FluxMarket plugin) {
        this.plugin = plugin;
        this.dao = new AuctionDao(plugin);
        this.manager = new AuctionManager(plugin, dao);
    }

    public void enable() {
        manager.load();
        registerCommand();
        plugin.getLogger().info("AUCTION module enabled.");
    }

    public void disable() {
        manager.stop();
    }

    private void registerCommand() {
        var cmd = plugin.getCommand("ah");
        if (cmd == null) return;
        CommandExecutor exec = (sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("player-only"));
                return true;
            }
            if (!player.hasPermission("fluxmarket.auction.use")) {
                player.sendMessage(plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("no-permission"));
                return true;
            }
            String prefix = plugin.getConfigManager().getPrefix();

            if (args.length == 0) {
                new AuctionMainGui(plugin, player).open();
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "history" -> {
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        var raw = dao.getTransactions(player.getUniqueId(), plugin.getConfigManager().getAhHistorySize());
                        var rawStats = dao.getStats(player.getUniqueId());
                        var stats = java.util.Map.of(
                                "total_spent", rawStats.totalSpent(),
                                "total_earned", rawStats.totalEarned(),
                                "total_bought", (double) rawStats.bought(),
                                "total_sold", (double) rawStats.sold());
                        String pUuid = player.getUniqueId().toString();
                        var entries = raw.stream().map(t -> {
                            boolean bought = t.buyerUuid().equals(pUuid);
                            String other = bought ? t.sellerName() : t.buyerName();
                            String type = bought ? "BOUGHT" : "SOLD";
                            return new AuctionHistoryGui.AuctionHistoryEntry(
                                    0, t.itemDisplayName(), t.price(), other, type, t.timestamp());
                        }).toList();
                        plugin.getServer().getScheduler().runTask(plugin,
                                () -> new AuctionHistoryGui(plugin, player, entries, stats).open());
                    });
                }
                case "admin" -> {
                    if (!player.hasPermission("fluxmarket.auction.admin")) {
                        player.sendMessage(prefix + plugin.getConfigManager().getMessage("no-permission"));
                        return true;
                    }
                    if (args.length < 2) {
                        player.sendMessage(FormatUtils.color(prefix + "&cUsage: /ah admin [enable|disable]"));
                        return true;
                    }
                    boolean disable = args[1].equalsIgnoreCase("disable");
                    manager.setEmergencyDisabled(disable);
                    player.sendMessage(FormatUtils.color(prefix + (disable
                            ? "&cAuction House has been DISABLED."
                            : "&aAuction House has been ENABLED.")));
                }
                case "sell" -> {
                    if (args.length < 2) {
                        player.sendMessage(FormatUtils.color(prefix + "&cUsage: /ah sell <price>"));
                        return true;
                    }
                    double price = parsePrice(args[1]);
                    if (price < plugin.getConfigManager().getAhMinPrice()) {
                        player.sendMessage(FormatUtils.color(prefix + "&cMinimum price: &f"
                                + FormatUtils.formatMoney(plugin.getConfigManager().getAhMinPrice())));
                        return true;
                    }
                    if (price > plugin.getConfigManager().getAhMaxPrice()) {
                        player.sendMessage(FormatUtils.color(prefix + "&cMaximum price: &f"
                                + FormatUtils.formatMoney(plugin.getConfigManager().getAhMaxPrice())));
                        return true;
                    }
                    listItem(player, price, false);
                }
                case "bid" -> {
                    if (args.length < 2) {
                        player.sendMessage(FormatUtils.color(prefix + "&cUsage: /ah bid <start price>"));
                        return true;
                    }
                    double price = parsePrice(args[1]);
                    if (price < plugin.getConfigManager().getAhMinPrice()) {
                        player.sendMessage(FormatUtils.color(prefix + "&cMinimum price: &f"
                                + FormatUtils.formatMoney(plugin.getConfigManager().getAhMinPrice())));
                        return true;
                    }
                    listItem(player, price, true);
                }
                default -> new AuctionMainGui(plugin, player).open();
            }
            return true;
        };
        cmd.setExecutor(exec);
        cmd.setTabCompleter((TabCompleter) (sender, command, alias, args) -> {
            if (args.length == 1) return List.of("sell", "bid", "history", "admin");
            if (args.length == 2 && args[0].equalsIgnoreCase("admin")) return List.of("enable", "disable");
            return List.of();
        });
    }

    private void listItem(Player player, double price, boolean bid) {
        String prefix = plugin.getConfigManager().getPrefix();
        if (manager.isEmergencyDisabled()) {
            player.sendMessage(FormatUtils.color(prefix + "&cThe Auction House is currently disabled by an admin."));
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(FormatUtils.color(prefix + "&cHold an item in your hand."));
            return;
        }

        int current = manager.countByPlayer(player.getUniqueId());
        int max = plugin.getConfigManager().getAhMaxListings();
        if (current >= max && !player.hasPermission("fluxmarket.auction.bypass-limit")) {
            player.sendMessage(FormatUtils.color(prefix + "&cYou already have &f" + current + "/" + max + " &cactive listings."));
            return;
        }

        double tax = price * plugin.getConfigManager().getAhListingTax();
        int qty = hand.getAmount();
        int bulkMinQty = plugin.getConfigManager().getBulkDiscountMinQty();
        double bulkPercent = plugin.getConfigManager().getBulkDiscountPercent();
        boolean bulkApplied = qty >= bulkMinQty;
        if (bulkApplied) {
            tax = tax * (1.0 - bulkPercent / 100.0);
        }
        if (!plugin.getEconomyProvider().has(player, tax)) {
            player.sendMessage(FormatUtils.color(prefix + "&cListing fee: &f" + FormatUtils.formatMoney(tax)
                    + " &c- not enough money."));
            return;
        }
        plugin.getEconomyProvider().withdraw(player, tax);
        if (bulkApplied) {
            player.sendMessage(FormatUtils.color(prefix + "&aBulk discount applied: &f"
                    + bulkPercent + "% &aoff listing fee!"));
        }
        if (tax > 0 && plugin.getTreasuryDao() != null) {
            double listingTax = tax;
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                    () -> plugin.getTreasuryDao().addEntry("auction_tax", listingTax));
        }

        ItemStack toList = hand.clone();
        hand.setAmount(0);

        long duration = plugin.getConfigManager().getAhDefaultDurationHours() * 3_600_000L;
        AuctionItem item = new AuctionItem(UUID.randomUUID(), player.getUniqueId(), player.getName(),
                toList, price, bid, duration);

        if (manager.listItem(item)) {
            player.sendMessage(FormatUtils.color(prefix + "&a" + (bid ? "Auction" : "Buy Now") + " created: &f"
                    + item.getItemDisplayName() + " &afor &f" + FormatUtils.formatMoney(price)
                    + " &8(fee: &c-" + FormatUtils.formatMoney(tax) + "&8)"));
        } else {
            plugin.getEconomyProvider().deposit(player, tax);
            player.getInventory().addItem(toList);
            player.sendMessage(FormatUtils.color(prefix + "&cFailed to create listing."));
        }
    }

    private double parsePrice(String s) {
        try {
            s = s.toLowerCase().replace(",", ".");
            if (s.endsWith("k")) return Double.parseDouble(s.replace("k", "")) * 1_000;
            if (s.endsWith("m")) return Double.parseDouble(s.replace("m", "")) * 1_000_000;
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public AuctionDao getDao() { return dao; }
    public AuctionManager getManager() { return manager; }
}

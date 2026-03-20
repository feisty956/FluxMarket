package me.fluxmarket;

import me.fluxmarket.config.ConfigManager;
import me.fluxmarket.database.DatabaseManager;
import me.fluxmarket.economy.EconomyProvider;
import me.fluxmarket.economy.VaultProvider;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.module.auction.AuctionModule;
import me.fluxmarket.module.flux.FluxModule;
import me.fluxmarket.module.orders.OrdersModule;
import me.fluxmarket.module.pricealert.PriceAlertCommand;
import me.fluxmarket.module.pricealert.PriceAlertDao;
import me.fluxmarket.module.pricealert.PriceAlertManager;
import me.fluxmarket.module.profit.ProfitCommand;
import me.fluxmarket.module.profit.ProfitDao;
import me.fluxmarket.module.sell.SellModule;
import me.fluxmarket.module.sell.SellProgressManager;
import me.fluxmarket.module.shop.ShopModule;
import me.fluxmarket.api.FluxMarketApi;
import me.fluxmarket.api.FluxMarketApiImpl;
import me.fluxmarket.module.playershop.PlayerShopModule;
import me.fluxmarket.module.treasury.TreasuryDao;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.WebhookManager;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class FluxMarket extends JavaPlugin {

    private static FluxMarket instance;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private EconomyProvider economyProvider;
    private WebhookManager webhookManager;
    private SellProgressManager sellProgressManager;
    private TreasuryDao treasuryDao;
    private PriceAlertDao priceAlertDao;
    private PriceAlertManager priceAlertManager;
    private ProfitDao profitDao;

    private FluxModule fluxModule;
    private ShopModule shopModule;
    private SellModule sellModule;
    private AuctionModule auctionModule;
    private OrdersModule ordersModule;
    private PlayerShopModule playerShopModule;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        webhookManager = new WebhookManager(this);

        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("Database initialization failed! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        treasuryDao = new TreasuryDao(databaseManager);
        treasuryDao.createTable();

        // Price Alerts
        priceAlertDao = new PriceAlertDao(databaseManager);
        priceAlertDao.createTable();
        priceAlertManager = new PriceAlertManager(this, priceAlertDao);

        // Profit Tracker
        profitDao = new ProfitDao(databaseManager);
        profitDao.createTable();

        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        registerAdminCommand();
        registerNewCommands();

        // Delay economy + module init until all plugins are loaded (Vault needs an economy provider)
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onServerLoad(ServerLoadEvent event) {
                economyProvider = new VaultProvider(FluxMarket.this);
                if (!economyProvider.isAvailable()) {
                    getLogger().severe("No economy plugin registered with Vault! Install EssentialsX or similar. Disabling plugin.");
                    getServer().getPluginManager().disablePlugin(FluxMarket.this);
                    return;
                }
                loadModules();
                getLogger().info("FluxMarket v" + getDescription().getVersion() + " enabled.");
            }
        }, this);
    }

    private void loadModules() {
        sellProgressManager = new SellProgressManager(this);
        if (configManager.isModuleEnabled("flux")) {
            fluxModule = new FluxModule(this);
            fluxModule.enable();
        }
        if (configManager.isModuleEnabled("shop")) {
            shopModule = new ShopModule(this);
            shopModule.enable();
        }
        if (configManager.isModuleEnabled("sell")) {
            sellModule = new SellModule(this);
            sellModule.enable();
        }
        if (configManager.isModuleEnabled("auction")) {
            auctionModule = new AuctionModule(this);
            auctionModule.enable();
        }
        if (configManager.isModuleEnabled("orders")) {
            ordersModule = new OrdersModule(this);
            ordersModule.enable();
        }
        if (configManager.isModuleEnabled("player-shops")) {
            playerShopModule = new PlayerShopModule(this);
            playerShopModule.enable();
        }
        // Register public API
        getServer().getServicesManager().register(
                FluxMarketApi.class, new FluxMarketApiImpl(this), this, ServicePriority.Normal);
    }

    private void registerAdminCommand() {
        var cmd = getCommand("fluxmarket");
        if (cmd == null) return;
        CommandExecutor executor = (sender, command, label, args) -> {
            if (!sender.hasPermission("fluxmarket.admin")) {
                sender.sendMessage(configManager.getPrefix() + configManager.getMessage("no-permission"));
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                configManager.reload();
                if (shopModule != null) shopModule.reload();
                if (fluxModule != null) fluxModule.reload();
                sender.sendMessage(configManager.getPrefix() + configManager.getMessage("reload-success"));
            } else {
                sender.sendMessage(FormatUtils.color(configManager.getPrefix() + "&eUsage: /fluxmarket reload"));
            }
            return true;
        };
        cmd.setExecutor(executor);
        cmd.setTabCompleter((TabCompleter) (sender, command, alias, args) ->
                args.length == 1 ? java.util.List.of("reload") : java.util.List.of());
    }

    private void registerNewCommands() {
        var paCmd = getCommand("pricealert");
        if (paCmd != null) {
            PriceAlertCommand pac = new PriceAlertCommand(this);
            paCmd.setExecutor(pac);
            paCmd.setTabCompleter(pac);
        }

        var profitCmd = getCommand("profit");
        if (profitCmd != null) {
            ProfitCommand pc = new ProfitCommand(this);
            profitCmd.setExecutor(pc);
            profitCmd.setTabCompleter(pc);
        }
    }

    @Override
    public void onDisable() {
        if (fluxModule != null) fluxModule.disable();
        if (shopModule != null) shopModule.disable();
        if (sellModule != null) sellModule.disable();
        if (auctionModule != null) auctionModule.disable();
        if (ordersModule != null) ordersModule.disable();
        if (playerShopModule != null) playerShopModule.disable();
        if (databaseManager != null) databaseManager.close();
        getLogger().info("FluxMarket disabled.");
    }

    public static FluxMarket getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public EconomyProvider getEconomyProvider() { return economyProvider; }
    public WebhookManager getWebhookManager() { return webhookManager; }
    public SellProgressManager getSellProgressManager() { return sellProgressManager; }
    public TreasuryDao getTreasuryDao() { return treasuryDao; }
    public PriceAlertDao getPriceAlertDao() { return priceAlertDao; }
    public PriceAlertManager getPriceAlertManager() { return priceAlertManager; }
    public ProfitDao getProfitDao() { return profitDao; }
    public FluxModule getFluxModule() { return fluxModule; }
    public ShopModule getShopModule() { return shopModule; }
    public SellModule getSellModule() { return sellModule; }
    public AuctionModule getAuctionModule() { return auctionModule; }
    public OrdersModule getOrdersModule() { return ordersModule; }
    public PlayerShopModule getPlayerShopModule() { return playerShopModule; }
}

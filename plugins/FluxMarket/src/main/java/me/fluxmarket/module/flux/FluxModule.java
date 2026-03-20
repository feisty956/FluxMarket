package me.fluxmarket.module.flux;

import me.fluxmarket.FluxMarket;
import org.bukkit.scheduler.BukkitTask;

public class FluxModule {

    private final FluxMarket plugin;
    private FluxEngine engine;
    private MarketEventManager eventManager;
    private BukkitTask recalcTask;

    public FluxModule(FluxMarket plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        engine = new FluxEngine(plugin);

        // Schedule periodic recalculation + history snapshots
        int intervalMinutes = plugin.getConfigManager().getFluxUpdateInterval();
        long ticks = intervalMinutes * 60L * 20L;
        recalcTask = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, engine::recalculateAll, ticks, ticks);

        // Market events
        if (plugin.getConfigManager().isFluxEventsEnabled()) {
            eventManager = new MarketEventManager(plugin, engine);
            eventManager.start();
        }

        // PlaceholderAPI
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new FluxPlaceholders(plugin, engine).register();
            plugin.getLogger().info("FluxMarket: PlaceholderAPI expansion registered.");
        }

        // Register /market command
        var cmd = plugin.getCommand("market");
        if (cmd != null) {
            var fluxCmd = new FluxCommand(plugin, engine);
            cmd.setExecutor(fluxCmd);
            cmd.setTabCompleter(fluxCmd);
        }

        plugin.getLogger().info("FLUX module enabled.");
    }

    public void disable() {
        if (recalcTask != null) recalcTask.cancel();
        if (eventManager != null) eventManager.stop();
    }

    public void reload() {
        if (engine != null) engine.invalidateAll();
    }

    public FluxEngine getEngine() { return engine; }
    public MarketEventManager getEventManager() { return eventManager; }
}

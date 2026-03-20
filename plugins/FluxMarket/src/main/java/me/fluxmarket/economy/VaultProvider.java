package me.fluxmarket.economy;

import me.fluxmarket.FluxMarket;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultProvider implements EconomyProvider {

    private Economy economy;

    public VaultProvider(FluxMarket plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> rsp = plugin.getServer()
                .getServicesManager().getRegistration(Economy.class);
        if (rsp != null) economy = rsp.getProvider();
    }

    @Override public boolean isAvailable() { return economy != null; }
    @Override public boolean has(OfflinePlayer p, double amount) { return economy.has(p, amount); }
    @Override public boolean withdraw(OfflinePlayer p, double amount) { return economy.withdrawPlayer(p, amount).transactionSuccess(); }
    @Override public boolean deposit(OfflinePlayer p, double amount) { return economy.depositPlayer(p, amount).transactionSuccess(); }
    @Override public double getBalance(OfflinePlayer p) { return economy.getBalance(p); }
    @Override public String format(double amount) { return economy.format(amount); }
    @Override public String getCurrencyName() { return economy.currencyNamePlural(); }
}

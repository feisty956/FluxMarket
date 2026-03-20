package me.fluxmarket.economy;

import org.bukkit.OfflinePlayer;

public interface EconomyProvider {
    boolean isAvailable();
    boolean has(OfflinePlayer player, double amount);
    boolean withdraw(OfflinePlayer player, double amount);
    boolean deposit(OfflinePlayer player, double amount);
    double getBalance(OfflinePlayer player);
    String format(double amount);
    String getCurrencyName();
}

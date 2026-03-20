package me.fluxmarket.module.pricealert;

import org.bukkit.Material;

import java.util.UUID;

public record PriceAlert(UUID playerUuid, Material material, double targetPrice) {}

package me.fluxmarket.module.orders;

import org.bukkit.Material;

import java.util.UUID;

public class Order {

    public enum Status { ACTIVE, COMPLETED, CANCELLED, EXPIRED }

    private final UUID uuid;
    private final UUID creatorUuid;
    private final String creatorName;
    private final Material material;
    private final int amountNeeded;
    private int amountDelivered;
    private final double priceEach;
    private Status status;
    private final long createdAt;
    private final long expiresAt;

    public Order(UUID uuid, UUID creatorUuid, String creatorName, Material material,
                 int amountNeeded, double priceEach, long durationMillis) {
        this.uuid = uuid;
        this.creatorUuid = creatorUuid;
        this.creatorName = creatorName;
        this.material = material;
        this.amountNeeded = amountNeeded;
        this.amountDelivered = 0;
        this.priceEach = priceEach;
        this.status = Status.ACTIVE;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = createdAt + durationMillis;
    }

    // DB reconstruction
    public Order(UUID uuid, UUID creatorUuid, String creatorName, Material material,
                 int amountNeeded, int amountDelivered, double priceEach,
                 Status status, long createdAt, long expiresAt) {
        this.uuid = uuid;
        this.creatorUuid = creatorUuid;
        this.creatorName = creatorName;
        this.material = material;
        this.amountNeeded = amountNeeded;
        this.amountDelivered = amountDelivered;
        this.priceEach = priceEach;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public int getRemainingAmount() { return Math.max(0, amountNeeded - amountDelivered); }
    public boolean isComplete() { return amountDelivered >= amountNeeded; }
    public boolean isExpired() { return System.currentTimeMillis() > expiresAt && status == Status.ACTIVE; }
    public long getRemainingMillis() { return Math.max(0, expiresAt - System.currentTimeMillis()); }
    public double getTotalValue() { return priceEach * amountNeeded; }
    public double getRemainingValue() { return priceEach * getRemainingAmount(); }

    public void addDelivery(int amount) {
        amountDelivered = Math.min(amountNeeded, amountDelivered + amount);
        if (isComplete()) status = Status.COMPLETED;
    }

    public void setStatus(Status status) { this.status = status; }

    // Getters
    public UUID getUuid() { return uuid; }
    public UUID getCreatorUuid() { return creatorUuid; }
    public String getCreatorName() { return creatorName; }
    public Material getMaterial() { return material; }
    public int getAmountNeeded() { return amountNeeded; }
    public int getAmountDelivered() { return amountDelivered; }
    public double getPriceEach() { return priceEach; }
    public Status getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
}

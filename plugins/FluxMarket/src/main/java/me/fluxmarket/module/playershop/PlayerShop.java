package me.fluxmarket.module.playershop;

import java.util.UUID;

/**
 * Represents a player-owned chest shop.
 * The sign is placed on the face of a chest. Line format:
 *   Line 0: [Shop]
 *   Line 1: <quantity per transaction>
 *   Line 2: $<price>
 *   Line 3: <auto: item name>
 */
public class PlayerShop {

    private final UUID id;
    private final UUID ownerUuid;
    private final String ownerName;
    private final String worldName;
    private final int signX, signY, signZ;
    private final int chestX, chestY, chestZ;
    private int quantity;
    private double price;
    private String material; // material name, may be UNKNOWN before first stock

    public PlayerShop(UUID id, UUID ownerUuid, String ownerName,
                      String worldName,
                      int signX, int signY, int signZ,
                      int chestX, int chestY, int chestZ,
                      int quantity, double price, String material) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.worldName = worldName;
        this.signX = signX; this.signY = signY; this.signZ = signZ;
        this.chestX = chestX; this.chestY = chestY; this.chestZ = chestZ;
        this.quantity = quantity;
        this.price = price;
        this.material = material;
    }

    /** Unique string key based on sign location. */
    public String signKey() {
        return worldName + ":" + signX + ":" + signY + ":" + signZ;
    }

    public UUID getId() { return id; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public String getOwnerName() { return ownerName; }
    public String getWorldName() { return worldName; }
    public int getSignX() { return signX; }
    public int getSignY() { return signY; }
    public int getSignZ() { return signZ; }
    public int getChestX() { return chestX; }
    public int getChestY() { return chestY; }
    public int getChestZ() { return chestZ; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public String getMaterial() { return material; }

    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setPrice(double price) { this.price = price; }
    public void setMaterial(String material) { this.material = material; }
}

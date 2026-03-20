package me.fluxmarket.module.shop;

/**
 * @param buyPriceOverride  Static buy price (>0 = use instead of flux engine, -1 = use engine)
 * @param sellPriceOverride Static sell price (>0 = use instead of flux engine, -1 = use engine)
 */
public record ShopItem(
        String material,
        double basePrice,
        boolean buyEnabled,
        boolean sellEnabled,
        double buyPriceOverride,
        double sellPriceOverride
) {
    public boolean hasBuyOverride()  { return buyPriceOverride  > 0; }
    public boolean hasSellOverride() { return sellPriceOverride > 0; }
}

package me.fluxmarket.module.orders.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.AnvilInputGui;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.module.orders.Order;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrderCreateGui implements FluxGui {

    // ── Main overview (27 slots) ──────────────────────────────────────────
    private static final int SLOT_ITEM    = 11;
    private static final int SLOT_AMOUNT  = 13;
    private static final int SLOT_PRICE   = 15;
    private static final int SLOT_CANCEL  = 21;
    private static final int SLOT_CONFIRM = 23;

    // ── Item picker (54 slots) ─────────────────────────────────────────────
    private static final int PICKER_ITEMS  = 45;   // slots 0-44
    private static final int PICKER_PREV   = 45;
    private static final int PICKER_SEARCH = 49;
    private static final int PICKER_NEXT   = 53;
    private static final int PICKER_BACK   = 47;

    private enum Step { MAIN, PICKER }

    private final FluxMarket plugin;
    private final Player player;
    private Inventory inventory;
    private Step step = Step.MAIN;

    // Order data
    private Material selectedMaterial;
    private Integer amount;
    private Double price;

    // Picker state
    private int pickerPage = 0;
    private String pickerFilter = null;
    private List<Material> pickerItems;

    public OrderCreateGui(FluxMarket plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    // ── Open ──────────────────────────────────────────────────────────────

    public void open() {
        if (step == Step.PICKER) {
            openPicker();
        } else {
            openMain();
        }
    }

    // ── Main screen ───────────────────────────────────────────────────────

    private void openMain() {
        step = Step.MAIN;
        inventory = Bukkit.createInventory(null, 27, FormatUtils.comp("&8[&6Orders&8] &7Create"));
        populateMain();
        player.openInventory(inventory);
        GuiListener.open(player.getUniqueId(), this);
    }

    private void populateMain() {
        for (int i = 0; i < 27; i++) inventory.setItem(i, ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE));

        inventory.setItem(SLOT_ITEM, selectedMaterial == null
                ? ItemUtils.named(Material.CHEST, "&6Item",
                    "&eClick &7to browse available items")
                : ItemUtils.named(selectedMaterial,
                    "&6Item: &f" + FormatUtils.formatMaterialName(selectedMaterial.name()),
                    "&eClick &7to change"));

        inventory.setItem(SLOT_AMOUNT, ItemUtils.named(Material.PAPER,
                "&6Amount: &f" + (amount == null ? "&8-" : amount),
                "&eClick &7to enter via sign"));

        inventory.setItem(SLOT_PRICE, ItemUtils.named(Material.EMERALD,
                "&6Price Each: &f" + (price == null ? "&8-" : "$" + FormatUtils.formatMoney(price)),
                "&eClick &7to enter via sign"));

        inventory.setItem(SLOT_CANCEL, ItemUtils.named(Material.BARRIER, "&cCancel"));

        double total = price != null && amount != null ? price * amount : 0.0;
        inventory.setItem(SLOT_CONFIRM, ItemUtils.named(Material.LIME_STAINED_GLASS_PANE, "&aCreate Order",
                "&7Item:   &f" + (selectedMaterial == null ? "&8-" : FormatUtils.formatMaterialName(selectedMaterial.name())),
                "&7Amount: &f" + (amount == null ? "&8-" : amount),
                "&7Price:  &f" + (price == null ? "&8-" : "$" + FormatUtils.formatMoney(price)),
                "&7Total:  &a$" + FormatUtils.formatMoney(total)));
    }

    // ── Item picker ───────────────────────────────────────────────────────

    private void openPicker() {
        step = Step.PICKER;
        buildPickerList();
        String title = pickerFilter != null
                ? "&8[&6Orders&8] &7Search: &f" + pickerFilter
                : "&8[&6Orders&8] &7Select Item";
        inventory = Bukkit.createInventory(null, 54, FormatUtils.comp(title));
        populatePicker();
        player.openInventory(inventory);
        GuiListener.open(player.getUniqueId(), this);
    }

    private void buildPickerList() {
        pickerItems = new ArrayList<>();
        for (Material mat : Material.values()) {
            if (mat.isAir() || mat.isLegacy() || !mat.isItem()) continue;
            if (pickerFilter != null) {
                String name = FormatUtils.formatMaterialName(mat.name()).toLowerCase();
                if (!name.contains(pickerFilter.toLowerCase())) continue;
            }
            pickerItems.add(mat);
        }
    }

    private void populatePicker() {
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);
        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = PICKER_ITEMS; i < 54; i++) inventory.setItem(i, filler);

        int start = pickerPage * PICKER_ITEMS;
        for (int i = 0; i < PICKER_ITEMS && (start + i) < pickerItems.size(); i++) {
            Material mat = pickerItems.get(start + i);
            inventory.setItem(i, ItemUtils.named(mat,
                    "&f" + FormatUtils.formatMaterialName(mat.name()),
                    "&eClick &7to select"));
        }

        if (pickerPage > 0)
            inventory.setItem(PICKER_PREV, ItemUtils.named(Material.ARROW, "&7Previous Page"));
        if ((pickerPage + 1) * PICKER_ITEMS < pickerItems.size())
            inventory.setItem(PICKER_NEXT, ItemUtils.named(Material.ARROW, "&7Next Page"));

        String searchLabel = pickerFilter != null
                ? "&eSearch: &f\"" + pickerFilter + "\" &7(click to change)"
                : "&eSearch &7— click to filter";
        inventory.setItem(PICKER_SEARCH, ItemUtils.named(Material.NAME_TAG, searchLabel,
                "&7Type a name to filter items"));
        inventory.setItem(PICKER_BACK, ItemUtils.named(Material.BARRIER, "&cBack"));
    }

    // ── Click handler ─────────────────────────────────────────────────────

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        int slot = event.getSlot();

        if (step == Step.PICKER) {
            handlePickerClick(slot);
        } else {
            handleMainClick(slot);
        }
    }

    private void handleMainClick(int slot) {
        if (slot == SLOT_CANCEL) { player.closeInventory(); return; }
        if (slot == SLOT_ITEM)   { pickerPage = 0; pickerFilter = null; openPicker(); return; }
        if (slot == SLOT_AMOUNT) { openAmountInput(); return; }
        if (slot == SLOT_PRICE)  { openPriceInput(); return; }
        if (slot == SLOT_CONFIRM) { placeOrder(); }
    }

    private void handlePickerClick(int slot) {
        if (slot == PICKER_BACK) { openMain(); return; }

        if (slot == PICKER_PREV && pickerPage > 0) {
            pickerPage--;
            buildPickerList();
            populatePicker();
            return;
        }
        if (slot == PICKER_NEXT && (pickerPage + 1) * PICKER_ITEMS < pickerItems.size()) {
            pickerPage++;
            buildPickerList();
            populatePicker();
            return;
        }
        if (slot == PICKER_SEARCH) {
            AnvilInputGui.open(plugin, player, "&8Search Items",
                    pickerFilter != null ? pickerFilter : "",
                    value -> {
                        pickerFilter = (value == null || value.isBlank()) ? null : value.trim();
                        pickerPage = 0;
                        openPicker();
                    });
            return;
        }

        if (slot >= PICKER_ITEMS) return;
        int idx = pickerPage * PICKER_ITEMS + slot;
        if (idx >= pickerItems.size()) return;

        selectedMaterial = pickerItems.get(idx);
        openMain();
    }

    // ── Anvil inputs for amount / price ──────────────────────────────────

    private void openAmountInput() {
        AnvilInputGui.open(plugin, player, "&8Order — Set Amount",
                amount == null ? "64" : String.valueOf(amount),
                value -> {
                    if (value != null) {
                        try {
                            int parsed = Integer.parseInt(value.replace(",", "").trim());
                            if (parsed < 1) throw new NumberFormatException();
                            amount = parsed;
                        } catch (NumberFormatException ex) {
                            player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                                    + "&cAmount must be a whole number above 0."));
                        }
                    }
                    openMain();
                });
    }

    private void openPriceInput() {
        AnvilInputGui.open(plugin, player, "&8Order — Set Price/Item",
                price == null ? "1.0" : String.valueOf(price),
                value -> {
                    if (value != null) {
                        try {
                            double parsed = Double.parseDouble(value.replace(",", ".").trim());
                            if (parsed < plugin.getConfigManager().getOrdersMinPrice()) throw new NumberFormatException();
                            price = Math.round(parsed * 100.0) / 100.0;
                        } catch (NumberFormatException ex) {
                            player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                                    + "&cMin price: $" + FormatUtils.formatMoney(plugin.getConfigManager().getOrdersMinPrice())));
                        }
                    }
                    openMain();
                });
    }

    // ── Order placement ───────────────────────────────────────────────────

    private void placeOrder() {
        String prefix = plugin.getConfigManager().getPrefix();
        if (selectedMaterial == null) {
            player.sendMessage(FormatUtils.color(prefix + "&cSelect an item first."));
            return;
        }
        if (amount == null || amount < 1) {
            player.sendMessage(FormatUtils.color(prefix + "&cSet a valid amount first."));
            return;
        }
        if (price == null || price < plugin.getConfigManager().getOrdersMinPrice()) {
            player.sendMessage(FormatUtils.color(prefix + "&cSet a valid price first."));
            return;
        }

        double total = price * amount;
        int existing = plugin.getOrdersModule().getManager().countByPlayer(player.getUniqueId());
        if (existing >= plugin.getConfigManager().getOrdersMaxPerPlayer()) {
            player.sendMessage(FormatUtils.color(prefix + "&cMax orders reached ("
                    + plugin.getConfigManager().getOrdersMaxPerPlayer() + ")."));
            return;
        }
        if (!plugin.getEconomyProvider().has(player, total)) {
            player.sendMessage(FormatUtils.color(prefix + "&cNot enough money! Need: &f$" + FormatUtils.formatMoney(total)));
            return;
        }

        plugin.getEconomyProvider().withdraw(player, total);
        long duration = plugin.getConfigManager().getOrdersDefaultExpiryHours() * 3_600_000L;
        Order order = new Order(UUID.randomUUID(), player.getUniqueId(), player.getName(),
                selectedMaterial, amount, price, duration);
        plugin.getOrdersModule().getManager().addOrder(order);
        plugin.getOrdersModule().getDao().save(order);

        player.sendMessage(FormatUtils.color(prefix + "&aOrder created: &f" + amount + "x "
                + FormatUtils.formatMaterialName(selectedMaterial.name())
                + " &aat &f$" + FormatUtils.formatMoney(price) + " &aeach."));
        player.closeInventory();
    }
}

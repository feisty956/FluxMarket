package me.fluxmarket.util;

import me.fluxmarket.FluxMarket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Sends Discord webhook notifications for notable market events.
 * All operations are async and silently swallow errors — webhooks are non-critical.
 */
public class WebhookManager {

    private final FluxMarket plugin;
    private final HttpClient http;

    public WebhookManager(FluxMarket plugin) {
        this.plugin = plugin;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void sendSale(String itemName, double price, String buyer, String seller) {
        send(String.format(
                "💰 **%s** sold for **$%s** | Buyer: `%s` | Seller: `%s`",
                itemName, FormatUtils.formatMoney(price), buyer, seller));
    }

    public void sendFluxEvent(String eventType, String description) {
        String icon = eventType.equalsIgnoreCase("BOOM") ? "📈" : "📉";
        send(String.format("%s **MARKET EVENT** | **%s** — %s", icon, eventType, description));
    }

    public void sendSellTopUpdate(String playerName, int rank, double totalEarned) {
        send(String.format(
                "🏆 **%s** is now rank **#%d** on SellTop with **$%s** earned!",
                playerName, rank, FormatUtils.formatMoney(totalEarned)));
    }

    private void send(String message) {
        String url = plugin.getConfigManager().getWebhookUrl();
        if (url == null || url.isEmpty()) return;

        String json = "{\"content\": " + jsonString(message) + "}";

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                http.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
                // Webhooks must never crash the server
            }
        });
    }

    /** Minimal JSON string escaping. */
    private String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}

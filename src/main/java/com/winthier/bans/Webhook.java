package com.winthier.bans;

import com.winthier.bans.sql.IPBanTable;
import com.winthier.bans.sql.SQLWebhook;
import com.winthier.bans.util.Json;
import com.winthier.bans.util.Timespan;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

final class Webhook {
    private Webhook() { }

    protected static void send(final BansPlugin plugin, Ban ban) {
        if (ban.getType() == BanType.NOTE) return;
        String message;
        if (!ban.getType().isLifted() && ban.getExpiry() != 0L) {
            message = ""
                + "`" + ban.getId() + "`"
                + " " + bold(sanitize(ban.getPlayer().getName())) + " was " + ban.getType().getPassive() + " by " + ban.getAdminName()
                + " for " + Timespan.difference(ban.getTime(), ban.getExpiry()).toNiceString()
                + ": " + (ban.getReason() != null ? sanitize(ban.getReason()) : "N/A");
        } else {
            message = ""
                + "`" + ban.getId() + "`"
                + " " + bold(sanitize(ban.getPlayer().getName())) + " was " + ban.getType().getPassive() + " by " + ban.getAdminName()
                + (ban.getReason() != null ? ": " + sanitize(ban.getReason()) : "");
        }
        send(plugin, message);
    }

    protected static void send(final BansPlugin plugin, IPBanTable ban) {
        String message = ""
            + "IP `" + ban.getIp() + "` banned by " + ban.getAdminName()
            + ": " + (ban.getReason() != null ? ban.getReason() : "N/A");
        send(plugin, message);
    }

    protected static void send(final BansPlugin plugin, final String body) {
        plugin.database.getDb().scheduleAsyncTask(() -> {
                for (SQLWebhook row : plugin.database.getDb().find(SQLWebhook.class).findList()) {
                    sendRequest(plugin, row.getUrl(), body);
                }
            });
    }

    protected static void sendRequest(final BansPlugin plugin, final String url, final String message) {
        Map<String, Object> webhookObject = new LinkedHashMap<>();
        webhookObject.put("content", message);
        final String body = Json.serialize(webhookObject);
        HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        HttpRequest request = HttpRequest.newBuilder()
            .POST(BodyPublishers.ofString(body))
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .build();
        try {
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Webhook url=" + url + " body=" + body, e);
            e.printStackTrace();
        }
    }

    private static String bold(String in) {
        return "**" + in + "**";
    }

    private static String sanitize(String in) {
        return in
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("*", "\\*")
            .replace("_", "\\_")
            .replace("/", "\\/");
    }
}

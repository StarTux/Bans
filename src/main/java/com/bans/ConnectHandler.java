package com.winthier.bans;

import com.google.gson.Gson;
import com.winthier.bans.sql.IPBanTable;
import com.winthier.connect.Connect;
import com.winthier.connect.event.ConnectMessageEvent;
import java.util.Map;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class ConnectHandler implements Listener {
    public final BansPlugin plugin;
    public static final Gson GSON = new Gson();

    public ConnectHandler(final BansPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onConnectMessage(ConnectMessageEvent event) {
        switch (event.getMessage().getChannel()) {
        case "Bans": {
            Object o = event.getMessage().getPayload();
            if (!(o instanceof Map)) return;
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) o;
            Ban ban = Ban.deserialize(map);
            plugin.playerListener.onRemoteBan(ban);
            break;
        }
        case "Bans.IPBan": {
            IPBanTable ipban = GSON.fromJson(event.getMessage().getPayload().toString(), IPBanTable.class);
            if (ipban != null) {
                plugin.playerListener.onIPBan(ipban);
            }
            break;
        }
        default: break;
        }
    }

    public void broadcast(Ban ban) {
        Connect.getInstance().broadcast("Bans", ban.serialize());
    }

    public void broadcast(IPBanTable ipban) {
        Connect.getInstance().broadcast("Bans.IPBan", GSON.toJson(ipban));
    }
}

package com.winthier.bans;

import com.winthier.bans.sql.IPBanTable;
import com.winthier.bans.util.Json;
import com.winthier.connect.Connect;
import com.winthier.connect.event.ConnectMessageEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class ConnectHandler implements Listener {
    public final BansPlugin plugin;

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
            Ban ban = Json.deserialize(event.getMessage().getPayload(), Ban.class);
            plugin.playerListener.onRemoteBan(ban);
            break;
        }
        case "Bans.IPBan": {
            IPBanTable ipban = Json.deserialize(event.getMessage().getPayload(), IPBanTable.class);
            if (ipban != null) {
                plugin.playerListener.onIPBan(ipban);
            }
            break;
        }
        default: break;
        }
    }

    public void broadcast(Ban ban) {
        Connect.getInstance().broadcast("Bans", Json.serialize(ban));
    }

    public void broadcast(IPBanTable ipban) {
        Connect.getInstance().broadcast("Bans.IPBan", Json.serialize(ipban));
    }
}

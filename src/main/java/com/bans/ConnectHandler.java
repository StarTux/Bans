package com.winthier.bans;

import com.winthier.connect.Connect;
import com.winthier.connect.bukkit.event.ConnectMessageEvent;
import java.util.Map;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ConnectHandler implements Listener {
    public final BansPlugin plugin;

    public ConnectHandler(BansPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onConnectMessage(ConnectMessageEvent event) {
        if (!"Bans".equals(event.getMessage().getChannel())) return;
        Object o = event.getMessage().getPayload();
        if (!(o instanceof Map)) return;
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>)o;
        Ban ban = Ban.deserialize(map);
        plugin.playerListener.onRemoteBan(ban);
    }

    public void broadcast(Ban ban) {
        Connect.getInstance().broadcast("Bans", ban.serialize());
    }
}

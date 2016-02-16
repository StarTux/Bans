package com.winthier.bans;

import com.winthier.connect.Connect;
import com.winthier.connect.bukkit.event.ConnectMessageEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
        try {
            byte[] data = ((String)event.getMessage().getPayload()).getBytes();
            ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data));
            Object obj = in.readObject();
            plugin.playerListener.onRemoteBan((Ban)obj);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void broadcast(Ban ban) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(baos);
            out.writeObject(ban);
            byte[] data = baos.toByteArray();
            Connect.getInstance().broadcast("Bans", new String(data));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}

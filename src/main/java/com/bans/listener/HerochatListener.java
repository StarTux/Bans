package com.winthier.bans.listener;

import com.dthielke.herochat.ChannelChatEvent;
import com.dthielke.herochat.Chatter;
import com.winthier.bans.Ban;
import com.winthier.bans.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class HerochatListener implements Listener {
    public final PlayerListener playerListener;

    public HerochatListener(PlayerListener playerListener) {
        this.playerListener = playerListener;
    }

    public void init() {
        playerListener.plugin.getServer().getPluginManager().registerEvents(this, playerListener.plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerChat(ChannelChatEvent event) {
        final Player player = event.getSender().getPlayer();
        if (player == null) return;
        BanInfo info = playerListener.getBanInfo(player);
        if (info == null) return; // Should never happen
        Ban mute = info.getMute();
        Ban jail = info.getJail();
        if (mute != null) {
            if (mute.hasExpired()) {
                playerListener.updatePlayerBans(player);
            } else {
                event.setResult(Chatter.Result.MUTED);
                player.sendMessage(Msg.getBanMessage(playerListener.plugin, mute));
            }
        }
        if (jail != null) {
            if (jail.hasExpired()) {
                playerListener.updatePlayerBans(player);
            } else {
                event.setResult(Chatter.Result.MUTED);
                player.sendMessage(Msg.getBanMessage(playerListener.plugin, jail));
            }
        }
    }
}

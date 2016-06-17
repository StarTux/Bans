package com.winthier.bans.listener;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import com.winthier.chat.event.ChatPlayerTalkEvent;
import org.bukkit.entity.Player;
import lombok.RequiredArgsConstructor;
import com.winthier.bans.Ban;
import com.winthier.bans.util.Msg;

@RequiredArgsConstructor
public class ChatListener implements Listener {
    final PlayerListener playerListener;
    
    @EventHandler
    public void onChatPlayerTalk(ChatPlayerTalkEvent event) {
        final Player player = event.getPlayer();
        BanInfo info = playerListener.getBanInfo(player);
        if (info == null) return; // Should never happen
        Ban mute = info.getMute();
        Ban jail = info.getJail();
        if (mute != null) {
            if (mute.hasExpired()) {
                playerListener.updatePlayerBans(player);
            } else {
                event.setCancelled(true);
                player.sendMessage(Msg.getBanMessage(playerListener.plugin, mute));
                return;
            }
        }
        if (jail != null) {
            if (jail.hasExpired()) {
                playerListener.updatePlayerBans(player);
            }
        } else {
            event.setCancelled(true);
            player.sendMessage(Msg.getBanMessage(playerListener.plugin, jail));
            return;
        }
    }
}

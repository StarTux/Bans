package com.winthier.bans.listener;

import com.winthier.bans.Ban;
import com.winthier.bans.BanType;
import com.winthier.bans.BansPlugin;
import com.winthier.bans.sql.BanTable;
import com.winthier.bans.sql.PlayerTable;
import com.winthier.bans.util.Msg;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerListener implements Listener {
    public final BansPlugin plugin;
    private final Map<Player, BanInfo> players = Collections.<Player, BanInfo>synchronizedMap(new WeakHashMap<Player, BanInfo>());
    private ChatListener chatListener = null;
    public PlayerListener(BansPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        players.clear();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            updateBanInfo(player);
        }
        if (plugin.getServer().getPluginManager().getPlugin("Chat") != null) {
            chatListener = new ChatListener(this);
            plugin.getServer().getPluginManager().registerEvents(chatListener, plugin);
            plugin.getLogger().info("Chat plugin found!");
        }
    }

    public void onRemoteBan(Ban ban) {
        onBan(ban);
    }

    public void onLocalBan(Ban ban) {
        onBan(ban);
    }

    /**
     * What to do when a ban happens anywhere. Called by
     * onRemoteBan() and onLocalBan().
     */
    private void onBan(Ban ban) {
        Msg.announce(plugin, ban);
        Player player = plugin.getServer().getPlayer(ban.getPlayer().getUuid());
        if (player != null) {
            updatePlayerBans(player);
            // Issue immediate actions
            switch (ban.getType()) {
            case BAN: case KICK:
                player.kickPlayer(Msg.getBanMessage(plugin, ban));
                break;
            case WARNING:
                // Update the ban type since the warning has been delivered.
                BanTable table = plugin.database.getBan(ban.getId());
                if (table != null && table.getType() == BanType.WARNING) {
                    table.lift();
                    plugin.database.storeBan(table);
                }
                player.sendMessage(Msg.getBanMessage(plugin, ban));
                break;
            case JAIL:
                // Notify player, send to jail.
                player.teleport(plugin.getJailLocation());
                player.sendMessage(Msg.getBanMessage(plugin, ban));
                break;
            case MUTE:
                // Notify player
                player.sendMessage(Msg.getBanMessage(plugin, ban));
                break;
            case UNJAIL: case UNMUTE:
                Msg.send(player, "&cYou have been %s by &o%s&c.", ban.getType().getPassive(), ban.getAdminName());
                break;
            }
        }
    }

    public BanInfo getBanInfo(Player player) {
        return players.get(player);
    }

    /**
     * Update the BanInfo cache of a player. Call this when a
     * player joins or their ban record may have changed.
     */
    public void updateBanInfo(Player player, List<BanTable> bans) {
        BanInfo banInfo = new BanInfo();
        players.put(player, banInfo);
        for (BanTable ban : bans) {
            switch (ban.getType()) {
            case MUTE:
                banInfo.setMute(new Ban(ban));
                break;
            case JAIL:
                banInfo.setJail(new Ban(ban));
                break;
            }
        }
    }

    /**
     * Update the BanInfo cache of a player. Call this when a
     * player joins or their ban record may have changed.
     */
    public void updateBanInfo(Player player) {
        updateBanInfo(player, plugin.database.getPlayer(player).getBans());
    }

    /**
     * Update a player's ban info as well as their BanInfo
     * cache. Call this whenever a chached ban may have changed.
     */
    public void updatePlayerBans(Player player) {
        PlayerTable table = plugin.database.getPlayer(player);
        if (table == null || table.getBans() == null) return;
        plugin.database.updateBans(table.getBans());
        updateBanInfo(player, table.getBans());
    }

    public void updatePlayerBansLater(UUID uuid) {
        final Player player = plugin.getServer().getPlayer(uuid);
        if (player == null) return;
        new BukkitRunnable() {
            @Override public void run() {
                updatePlayerBans(player);
            }
        }.runTask(plugin);
    }

    public void liftBanLater(final BanTable ban) {
        new BukkitRunnable() {
            @Override public void run() {
                ban.lift();
                plugin.database.storeBan(ban);
            }
        }.runTask(plugin);
    }

    /**
     * When a player tried to login, check if they have an active
     * ban or warning. Update player bans if you encounter an
     * expired ban.
     */
    @EventHandler
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        PlayerTable player = plugin.database.getPlayerNoCreate(event.getUniqueId());
        if (player == null) return;
        boolean update = false;
        for (BanTable ban : player.getBans()) {
            switch (ban.getType()) {
            case BAN:
                if (ban.hasExpired()) {
                    updatePlayerBansLater(player.getUuid());
                } else {
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Msg.getBanMessage(plugin, ban));
                }
                break;
            case WARNING:
                if (ban.hasExpired()) {
                    updatePlayerBansLater(player.getUuid());
                } else {
                    liftBanLater(ban);
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Msg.getBanMessage(plugin, ban));
                }
                break;
            }
        }
    }

    /**
     * When a player joins, we refresh their BanInfo entry. No
     * need to update bans here as it has been done in the login
     * event.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        PlayerTable player = plugin.database.getPlayer(event.getPlayer());
        if (player == null || player.getBans() == null) return;
        plugin.database.updateBans(player.getBans());
        updateBanInfo(event.getPlayer(), player.getBans());
    }

    /**
     * When someone tries to chat while muted or jailed, deny them
     * that. If either expires, update bans.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        BanInfo info = getBanInfo(player);
        if (info == null) return; // Should never happen
        Ban mute = info.getMute();
        Ban jail = info.getJail();
        boolean scheduled = false;
        if (mute != null) {
            if (mute.hasExpired()) {
                if (!scheduled) {
                    new BukkitRunnable() {
                        @Override public void run() {
                            updatePlayerBans(player);
                        }
                    }.runTask(plugin);
                    scheduled = true;
                }
            } else {
                event.setCancelled(true);
                player.sendMessage(Msg.getBanMessage(plugin, mute));
                return;
            }
        }
        if (jail != null) {
            if (jail.hasExpired()) {
                if (!scheduled) {
                    new BukkitRunnable() {
                        @Override public void run() {
                            updatePlayerBans(player);
                        }
                    }.runTask(plugin);
                    scheduled = true;
                }
            } else {
                event.setCancelled(true);
                player.sendMessage(Msg.getBanMessage(plugin, jail));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        final Player player = event.getPlayer();
        if (player == null) return;
        BanInfo info = getBanInfo(player);
        if (info == null) return; // Should never happen
        Ban jail = info.getJail();
        if (jail != null) {
            if (jail.hasExpired()) {
                updatePlayerBans(player);
            } else {
                event.setCancelled(true);
                player.sendMessage(Msg.getBanMessage(plugin, jail));
            }
        }
    }
}

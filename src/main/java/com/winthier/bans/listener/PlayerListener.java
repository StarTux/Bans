package com.winthier.bans.listener;

import com.winthier.bans.Ban;
import com.winthier.bans.BanType;
import com.winthier.bans.BansPlugin;
import com.winthier.bans.sql.BanTable;
import com.winthier.bans.sql.IPBanTable;
import com.winthier.bans.sql.MetaTable;
import com.winthier.bans.sql.SQLSilentBan;
import com.winthier.bans.util.Msg;
import com.winthier.chat.event.ChatPlayerTalkEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.Semaphore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class PlayerListener implements Listener {
    public final BansPlugin plugin;
    private final Map<UUID, BanInfo> players = Collections.<UUID, BanInfo>synchronizedMap(new WeakHashMap<UUID, BanInfo>());

    public PlayerListener(final BansPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        players.clear();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            updateBanInfo(player);
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
                player.kick(Msg.getBanComponent(ban, List.of()));
                break;
            case WARNING:
                // Update the ban type since the warning has been delivered.
                BanTable table = plugin.database.getBan(ban.getId());
                if (table != null && table.getType() == BanType.WARNING) {
                    table.lift();
                    plugin.database.storeBan(table);
                }
                player.sendMessage(Msg.getBanComponent(ban, List.of()));
                break;
            case JAIL:
                // Notify player, send to jail.
                player.teleport(plugin.getJailLocation());
                player.sendMessage(Msg.getBanComponent(ban, List.of()));
                break;
            case MUTE:
                // Notify player
                player.sendMessage(Msg.getBanComponent(ban, List.of()));
                break;
            case UNJAIL: case UNMUTE:
                player.sendMessage(text("You have been " + ban.getType().getPassive() + " by " + ban.getAdminName(), RED));
                break;
            default:
                break;
            }
        }
    }

    public BanInfo getBanInfo(Player player) {
        return players.get(player.getUniqueId());
    }

    /**
     * Update the BanInfo cache of a player. Call this when a
     * player joins or their ban record may have changed.
     */
    public void updateBanInfo(Player player, List<BanTable> bans) {
        BanInfo banInfo = new BanInfo();
        players.put(player.getUniqueId(), banInfo);
        for (BanTable ban : bans) {
            switch (ban.getType()) {
            case MUTE:
                banInfo.setMute(new Ban(ban));
                break;
            case JAIL:
                banInfo.setJail(new Ban(ban));
                break;
            default:
                break;
            }
        }
    }

    /**
     * Update the BanInfo cache of a player. Call this when a
     * player joins or their ban record may have changed.
     */
    public void updateBanInfo(Player player) {
        updateBanInfo(player, plugin.database.getPlayerBans(player.getUniqueId()));
    }

    /**
     * Update a player's ban info as well as their BanInfo
     * cache. Call this whenever a chached ban may have changed.
     */
    public void updatePlayerBans(Player player) {
        List<BanTable> bans = plugin.database.getPlayerBans(player.getUniqueId());
        plugin.database.updateBans(bans);
        updateBanInfo(player, bans);
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
        Semaphore sem = new Semaphore(1);
        try {
            sem.acquire();
        } catch (InterruptedException ie) {
            ie.printStackTrace();
            return;
        }
        final List<BanTable> bans = new ArrayList<>();
        final List<IPBanTable> ipbans = new ArrayList<>();
        final List<SQLSilentBan> silentBans = new ArrayList<>();
        final String ip = event.getAddress().getHostAddress();
        final UUID uuid = event.getUniqueId();
        plugin.database.getDb().scheduleAsyncTask(() -> {
                List<BanTable> list = plugin.database.getDb()
                    .find(BanTable.class)
                    .eq("player", uuid)
                    .findList();
                bans.addAll(list);
                ipbans.addAll(plugin.database.getDb().find(IPBanTable.class).eq("ip", ip).findList());
                silentBans.addAll(plugin.database.getDb().find(SQLSilentBan.class).eq("uuid", uuid).findList());
                sem.release();
            });
        try {
            sem.acquire();
        } catch (InterruptedException ie) {
            ie.printStackTrace();
            return;
        }
        boolean update = false;
        for (BanTable ban: bans) {
            switch (ban.getType()) {
            case BAN:
                if (ban.hasExpired()) {
                    updatePlayerBansLater(event.getUniqueId());
                } else {
                    List<MetaTable> comments = new ArrayList<>();
                    plugin.database.getDb().scheduleAsyncTask(() -> {
                            comments.addAll(plugin.database.findComments(ban));
                            sem.release();
                        });
                    try {
                        sem.acquire();
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                        return;
                    }
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Msg.getBanComponent(ban, comments));
                    return;
                }
                break;
            case WARNING:
                if (ban.hasExpired()) {
                    updatePlayerBansLater(event.getUniqueId());
                } else {
                    liftBanLater(ban);
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Msg.getBanComponent(ban, List.of()));
                    return;
                }
                break;
            default:
                break;
            }
        }
        if (!ipbans.isEmpty()) {
            IPBanTable ipban = ipbans.get(0);
            Component message = Component.text("You have been banned by " + ipban.getAdminName()).color(RED)
                .append(Component.text("\nReason: " + ipban.getReason()).color(RED));
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, message);
        }
        if (!silentBans.isEmpty()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, text("Access Denied :)", RED));
        }
    }

    /**
     * When a player joins, we refresh their BanInfo entry. No
     * need to update bans here as it has been done in the login
     * event.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        updatePlayerBans(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        players.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChatPlayerTalk(ChatPlayerTalkEvent event) {
        final Player player = event.getPlayer();
        BanInfo info = getBanInfo(player);
        if (info == null) return; // Should never happen
        Ban mute = info.getMute();
        Ban jail = info.getJail();
        if (mute != null) {
            if (mute.hasExpired()) {
                updatePlayerBans(player);
            } else {
                event.setCancelled(true);
                player.sendMessage(Msg.getBanComponent(mute, List.of()));
                return;
            }
        }
        if (jail != null) {
            if (jail.hasExpired()) {
                updatePlayerBans(player);
            } else {
                event.setCancelled(true);
                player.sendMessage(Msg.getBanComponent(jail, List.of()));
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
                player.sendMessage(Msg.getBanComponent(jail, List.of()));
            }
        }
    }

    public void onIPBan(IPBanTable ipban) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String ip = player.getAddress().getAddress().getHostAddress();
            if (ip == null) continue;
            if (ip.equals(ipban.getIp())) {
                Component message = Component.text("You have been banned by " + ipban.getAdminName()).color(RED)
                    .append(Component.text("\nReason: " + ipban.getReason()).color(RED));
                player.kick(message);
            }
        }
    }
}

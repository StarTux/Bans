package com.winthier.bans.sql;

import com.avaje.ebean.EbeanServer;
import com.winthier.bans.Ban;
import com.winthier.bans.BanType;
import com.winthier.bans.BansPlugin;
import com.winthier.bans.util.Msg;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.persistence.PersistenceException;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

//import net.minecraft.server.v1_7_R3.MinecraftServer;// TODO removeme!

public class Database {
    public final BansPlugin plugin;

    public Database(BansPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        try {
            for (Class<?> clazz : getDatabaseClasses()) {
                getDatabase().find(clazz).findRowCount();
            }
        } catch (PersistenceException ex) {
            return false;
        }
        return true;
    }

    public List<Class<?>> getDatabaseClasses() {
        List<Class<?>> result = new ArrayList<Class<?>>();
        result.add(PlayerTable.class);
        result.add(BanTable.class);
        result.add(LegacyTable.class);
        return result;
    }

    private EbeanServer getDatabase() {
        return plugin.getDatabase();
    }

    /**
     * Use for async events.
     */
    public PlayerTable getPlayerNoCreate(UUID uuid) {
        PlayerTable result = getDatabase().find(PlayerTable.class).where().eq("uuid", uuid).findUnique();
        return result;
    }

    public PlayerTable getPlayer(UUID uuid) {
        PlayerTable result = getDatabase().find(PlayerTable.class).where().eq("uuid", uuid).findUnique();
        if (result == null) {
            result = new PlayerTable(uuid, new Date());
            getDatabase().save(result);
        }
        return result;
    }

    public PlayerTable getPlayer(String name) {
        PlayerTable result = getDatabase().find(PlayerTable.class).where().eq("name", name).findUnique();
        return result;
    }

    public PlayerTable getPlayer(OfflinePlayer player) {
        PlayerTable result = getDatabase().find(PlayerTable.class).where().eq("uuid", player.getUniqueId()).findUnique();
        if (result == null) {
            result = new PlayerTable(player, new Date());
            getDatabase().save(result);
        } else if (!result.getName().equals(player.getName())) {
            if (player.getName() == null) {
                plugin.getLogger().warning("Unknown for player with UUID " + player.getUniqueId());
                return null;
            }
            result.setName(player.getName());
            getDatabase().save(result);
        }
        return result;
    }

    public BanTable getBan(int id) {
        BanTable result = getDatabase().find(BanTable.class).where().idEq(id).findUnique();
        return result;
    }

    /**
     * Carefully find a player by name. An issue is that
     * CraftBukkit will return a fake OfflinePlayer when queried
     * by name, so let's not do that.
     */
    public PlayerTable getPlayerByName(String name) {
        // First, try an online player
        Player player = plugin.getServer().getPlayerExact(name);
        if (player != null) {
            return getPlayer(player);
        }
        // Then try to get it from our own database.
        return getPlayer(name);
    }

    /**
     * Get a player from a CommandSender. If sender is not a
     * player, we can assume that it is a ConsoleSender and return
     * null.
     */
    public PlayerTable getPlayerBySender(CommandSender sender) {
        if (!(sender instanceof Player)) return null;
        return getPlayer((Player)sender);
    }

    public void storeBan(BanTable ban) {
        getDatabase().save(ban);
    }

    public void deleteBan(BanTable ban) {
        getDatabase().delete(ban);
    }

    public void updateBan(BanTable ban) {
        updateBan(ban, new Date());
    }

    public void updateBan(BanTable ban, Date now) {
        if (!ban.expires()) return;
        if (now.compareTo(ban.getExpiry()) >= 0) {
            plugin.getLogger().info(Msg.format("%s [%04d] on %s has expired.", ban.getType().getNiceName(), ban.getId(), ban.getPlayer().getName()));
            ban.setType(ban.getType().lift());
            getDatabase().save(ban);
        }
    }

    public void updateBans(List<BanTable> list) {
        Date now = new Date();
        for (BanTable ban : list) {
            updateBan(ban, now);
        }
    }

    public void updateAllBans() {
        List<BanTable> list = getDatabase().find(BanTable.class).where().in("type", (Object[])BanType.getExpiringBans()).isNotNull("expiry").findList();
        updateBans(list);
    }

    public List<LegacyTable> getLegacy() {
        List<LegacyTable> result = getDatabase().find(LegacyTable.class).orderBy("time asc").findList();
        return result;
    }

    public void importLegacy(CommandSender sender) {
        List<LegacyTable> legacies = getLegacy();
        sender.sendMessage("Importing " + legacies.size() + " bans...");
        List<String> consoleNames = new ArrayList<String>();
        consoleNames.add("Tekkit");
        consoleNames.add("server");
        consoleNames.add("Server");
        consoleNames.add("Served Time");
        consoleNames.add("Console");
        int i = 0;
        for (LegacyTable legacy : legacies) {
            BanTable ban = new BanTable();
            PlayerTable player = getPlayer(legacy.getPlayer());
            if (player == null) {
                // if (MinecraftServer.getServer().getUserCache().a(legacy.getPlayer()) != null) { // TODO removeme!
                //     OfflinePlayer tmp = plugin.getServer().getOfflinePlayer(legacy.getPlayer());
                //     if (tmp != null && tmp.getName() != null) {
                //         sender.sendMessage("Added new player: " + tmp.getName());
                //         player = getPlayer(tmp);
                //     }
                // }
                // if (player == null) {
                sender.sendMessage("Unknown player: " + legacy.getId() + ": " + legacy.getPlayer());
                continue;
                // }
            }
            ban.setPlayer(player);
            PlayerTable admin = null;
            if (consoleNames.contains(legacy.getAdmin())) {
                // admin = null;
            } else {
                admin = getPlayer(legacy.getAdmin());
                if (admin == null) {
                    sender.sendMessage("Unknown admin: " + legacy.getId());
                    continue;
                }
            }
            ban.setAdmin(admin);
            ban.setReason(legacy.getReason());
            ban.setTime(legacy.getTime());
            ban.setExpiry(legacy.getExpiry());
            try {
                ban.setType(BanType.valueOf(legacy.getType().toUpperCase()));
            } catch (IllegalArgumentException iae) {
                sender.sendMessage("Unknown type: " + legacy.getId());
                continue;
            }
            getDatabase().save(ban);
            if (++i % 1000 == 0) sender.sendMessage("Imported " + i + "/" + legacies.size() + " ban records.");
        }
        sender.sendMessage("Finished importing " + i + " ban records. Failed: " + (legacies.size() - i));
    }

    /**
     * Export to Bans class.
     * Called by BansPlugin.getBans()
     */
    public List<Ban> exportBans(Player player) {
        PlayerTable playerTable = getPlayer(player);
        if (player == null) return new ArrayList<Ban>(0);
        List<BanTable> banList = playerTable.getBans();
        if (banList == null) return new ArrayList<Ban>(0);
        List<Ban> result = new ArrayList<Ban>(banList.size());
        for (BanTable entry : banList) {
            result.add(new Ban(entry));
        }
        return result;
    }
}

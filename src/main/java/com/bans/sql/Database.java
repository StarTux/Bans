package com.winthier.bans.sql;

import com.winthier.bans.Ban;
import com.winthier.bans.BanType;
import com.winthier.bans.BansPlugin;
import com.winthier.bans.util.Msg;
import com.winthier.sql.SQLDatabase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.persistence.PersistenceException;
import lombok.Getter;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Getter
public class Database {
    @Getter private static Database instance;
    public final BansPlugin plugin;
    private SQLDatabase db;

    public Database(BansPlugin plugin) {
        instance = this;
        this.plugin = plugin;
    }

    public boolean init() {
        db = new SQLDatabase(plugin);
        db.registerTables(PlayerTable.class, BanTable.class);
        return db.createAllTables();
    }

    /**
     * Use for async events.
     */
    public PlayerTable getPlayerNoCreate(UUID uuid) {
        PlayerTable result = db.find(PlayerTable.class).where().eq("uuid", uuid).findUnique();
        return result;
    }

    public PlayerTable getPlayer(UUID uuid) {
        PlayerTable result = db.find(PlayerTable.class).where().eq("uuid", uuid).findUnique();
        if (result == null) {
            result = new PlayerTable(uuid, new Date());
            db.save(result);
        }
        return result;
    }

    public PlayerTable getPlayer(String name) {
        PlayerTable result = db.find(PlayerTable.class).where().eq("name", name).findUnique();
        return result;
    }

    public PlayerTable getPlayer(OfflinePlayer player) {
        PlayerTable result = db.find(PlayerTable.class).where().eq("uuid", player.getUniqueId()).findUnique();
        if (result == null) {
            result = new PlayerTable(player, new Date());
            db.save(result);
        } else if (!result.getName().equals(player.getName())) {
            if (player.getName() == null) {
                plugin.getLogger().warning("Unknown for player with UUID " + player.getUniqueId());
                return null;
            }
            result.setName(player.getName());
            db.save(result);
        }
        return result;
    }

    public BanTable getBan(int id) {
        BanTable result = db.find(BanTable.class).where().idEq(id).findUnique();
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
        db.save(ban);
    }

    public void deleteBan(BanTable ban) {
        db.delete(ban);
    }

    public void updateBan(BanTable ban) {
        updateBan(ban, new Date());
    }

    public void updateBan(BanTable ban, Date now) {
        if (!ban.expires()) return;
        if (now.compareTo(ban.getExpiry()) >= 0) {
            plugin.getLogger().info(Msg.format("%s [%04d] on %s has expired.", ban.getType().getNiceName(), ban.getId(), ban.getPlayer().getName()));
            ban.setType(ban.getType().lift());
            db.save(ban);
        }
    }

    public void updateBans(List<BanTable> list) {
        Date now = new Date();
        for (BanTable ban : list) {
            updateBan(ban, now);
        }
    }

    public void updateAllBans() {
        List<BanTable> list = db.find(BanTable.class).where().in("type", Arrays.asList(BanType.BAN.key, BanType.MUTE.key, BanType.JAIL.key)).isNotNull("expiry").findList();
        updateBans(list);
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

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
import lombok.Getter;
import org.bukkit.entity.Player;

@Getter
public final class Database {
    @Getter private static Database instance;
    public final BansPlugin plugin;
    private SQLDatabase db;

    public Database(BansPlugin plugin) {
        instance = this;
        this.plugin = plugin;
    }

    public boolean init() {
        db = new SQLDatabase(plugin);
        db.registerTables(BanTable.class);
        return db.createAllTables();
    }

    public BanTable getBan(int id) {
        BanTable result = db.find(BanTable.class).where().idEq(id).findUnique();
        return result;
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
            plugin.getLogger().info(Msg.format("%s [%04d] on %s has expired.", ban.getType().getNiceName(), ban.getId(), ban.getPlayerName()));
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

    public List<BanTable> getPlayerBans(UUID uuid) {
        return db.find(BanTable.class).where().eq("player", uuid).findList();
    }

    /**
     * Export to Bans class.
     * Called by BansPlugin.getBans()
     */
    public List<Ban> exportBans(Player player) {
        if (player == null) return new ArrayList<Ban>(0);
        List<BanTable> banList = getPlayerBans(player.getUniqueId());
        if (banList == null) return new ArrayList<Ban>(0);
        List<Ban> result = new ArrayList<Ban>(banList.size());
        for (BanTable entry : banList) {
            result.add(new Ban(entry));
        }
        return result;
    }
}

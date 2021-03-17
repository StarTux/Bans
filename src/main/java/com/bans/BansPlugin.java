package com.winthier.bans;

import com.winthier.bans.command.Commands;
import com.winthier.bans.listener.PlayerListener;
import com.winthier.bans.sql.Database;
import com.winthier.bans.sql.IPBanTable;
import com.winthier.connect.Connect;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class BansPlugin extends JavaPlugin {
    public final Database database = new Database(this);
    public final Commands commands = new Commands(this);
    public final PlayerListener playerListener = new PlayerListener(this);
    private ConnectHandler connectHandler = new ConnectHandler(this);
    private Location jailLocation = null;

    @Override
    public void onEnable() {
        reloadConfig();
        saveDefaultConfig();
        database.init();
        commands.init();
        playerListener.init();
        connectHandler.init();
    }

    @Override
    public void onDisable() {
    }

    /**
     * Broadcast a recent ban to players with the bans.notify
     * permission on this server as well as on connected remote
     * servers, if available.
     */
    public void broadcast(Ban ban) {
        playerListener.onLocalBan(ban);
        connectHandler.broadcast(ban);
    }

    public void broadcast(IPBanTable ipban) {
        playerListener.onIPBan(ipban);
        connectHandler.broadcast(ipban);
    }

    public boolean isOnline(UUID uuid) {
        return Connect.getInstance().findOnlinePlayer(uuid) != null;
    }

    public Location getJailLocation() {
        if (jailLocation == null) {
            Location dfl = getServer().getWorlds().get(0).getSpawnLocation();
            try {
                ConfigurationSection config = getConfig().getConfigurationSection("jail");
                String worldName = config.getString("world", dfl.getWorld().getName());
                World world = getServer().getWorld(worldName);
                if (world == null) throw new NullPointerException();
                double x = config.getDouble("x", dfl.getX());
                double y = config.getDouble("y", dfl.getX());
                double z = config.getDouble("z", dfl.getX());
                float yaw = (float) config.getDouble("yaw");
                float pitch = (float) config.getDouble("pitch");
                jailLocation = new Location(world, x, y, z, yaw, pitch);
            } catch (NullPointerException npe) {
                jailLocation = dfl;
            }
        }
        return jailLocation;
    }

    public void setJailLocation(Location location) {
        jailLocation = location;
        ConfigurationSection config = getConfig().getConfigurationSection("jail");
        config.set("world", location.getWorld().getName());
        config.set("x", location.getX());
        config.set("y", location.getY());
        config.set("z", location.getZ());
        config.set("yaw", location.getYaw());
        config.set("pitch", location.getPitch());
        saveConfig();
    }

    public List<Ban> getBans(Player player) {
        return database.exportBans(player);
    }
}

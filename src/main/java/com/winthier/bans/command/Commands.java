package com.winthier.bans.command;

import com.cavetale.core.playercache.PlayerCache;
import com.winthier.bans.Ban;
import com.winthier.bans.BanType;
import com.winthier.bans.BansPlugin;
import com.winthier.bans.sql.BanTable;
import com.winthier.bans.sql.IPBanTable;
import com.winthier.bans.sql.MetaTable;
import com.winthier.bans.sql.MetaTable.MetaType;
import com.winthier.bans.util.Json;
import com.winthier.bans.util.Msg;
import com.winthier.bans.util.Timespan;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.event.ClickEvent.suggestCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

public final class Commands implements TabExecutor {
    public final BansPlugin plugin;
    private final Map<PluginCommand, Method> commandMap = new HashMap<PluginCommand, Method>();

    public Commands(final BansPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        commandMap.clear();
        for (String name : plugin.getDescription().getCommands().keySet()) {
            PluginCommand command = plugin.getCommand(name);
            command.setExecutor(this);
            Method method = null;
            try {
                method = getClass().getMethod(name, CommandSender.class, String[].class);
            } catch (NoSuchMethodException nsme) {
                plugin.getLogger().warning("Method not found for command " + name);
                continue;
            }
            commandMap.put(command, method);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Method method = commandMap.get(command);
        if (method == null) {
            plugin.getLogger().warning("Method not found for command " + label);
            return false;
        }
        try {
            return (boolean) method.invoke(this, sender, args);
        } catch (IllegalAccessException iae) {
            iae.printStackTrace();
            sender.sendMessage(text("An internal error occured. Please report this incident", RED));
        } catch (IllegalArgumentException iae) {
            iae.printStackTrace();
            sender.sendMessage(text("An internal error occured. Please report this incident", RED));
        } catch (InvocationTargetException ite) {
            if (ite.getCause() instanceof BansException bansException) {
                sender.sendMessage(bansException.getComponent());
            } else {
                ite.printStackTrace();
                sender.sendMessage(text("An internal error occured. Please report this incident", RED));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return PlayerCache.completeNames(args[args.length - 1]);
    }

    private void storeBan(CommandSender sender, BanTable ban) {
        List<BanTable> playerBans = plugin.database.getPlayerBans(ban.getPlayer());
        switch (ban.getType()) {
        case BAN:
        case MUTE:
        case JAIL:
            for (BanTable record : playerBans) {
                if (ban.getType().isActive() && ban.getType() == record.getType()) {
                    throw new BansException("Player " + ban.getPlayerName() + " is already " + ban.getType().getPassive());
                }
            }
            break;
        case KICK:
            if (!plugin.isOnline(ban.getPlayer())) {
                throw new BansException("Player " + ban.getPlayerName() + " is not online.");
            }
            break;
        default:
            break;
        }
        plugin.database.storeBan(ban);
        MetaTable meta = new MetaTable(ban.getId(), MetaType.CREATE, ban.getAdmin(), ban.getTime(), ban.getReason());
        plugin.database.storeMeta(meta);
        plugin.broadcast(new Ban(ban));
    }

    String getPlayerName(UUID uuid) {
        String name = PlayerCache.nameForUuid(uuid);
        if (name != null) return name;
        return "N/A";
    }

    private void liftBan(CommandSender sender, UUID player, BanType type) {
        boolean failed = false;
        BanType newType = type.lift();
        BanTable found = null;
        for (BanTable record : plugin.database.getPlayerBans(player)) {
            if (record.getType() != type) {
                continue;
            }
            if (!sender.hasPermission("bans.override.lift") && !record.isOwnedBy(sender)) {
                failed = true;
                continue;
            }
            record.setType(newType);
            record.setExpiry(new Date());
            plugin.database.storeBan(record);
            MetaTable meta = new MetaTable(record.getId(), MetaType.MODIFY, getSenderUuid(sender), new Date(), "Lifted");
            plugin.database.storeMeta(meta);
            found = record;
        }
        if (failed) {
            throw new BansException(getPlayerName(player) + " could not be " + newType.getPassive() + ": Not your " + type.getNiceName().toLowerCase());
        }
        if (found == null) {
            throw new BansException(getPlayerName(player) + " is not " + type.getPassive());
        }
        // Set the admin to whoever lifted the ban for the announcement.
        Ban ban = new Ban(found.getId(), found.getType(), PlayerCache.forUuid(found.getPlayer()),
                          (sender instanceof OfflinePlayer
                           ? PlayerCache.of((OfflinePlayer) sender)
                           : null),
                          null, found.getTime(), found.getExpiry());
        plugin.broadcast(ban);
    }

    UUID getSenderUuid(CommandSender sender) {
        if (sender instanceof Player) return ((Player) sender).getUniqueId();
        return null;
    }

    String getSenderName(UUID uuid) {
        if (uuid == null) return "Server";
        String name = PlayerCache.nameForUuid(uuid);
        return name != null ? name : "N/A";
    }

    public boolean issuePerm(BanType type, CommandSender sender, String[] args) {
        if (args.length < 1) return false;
        // Build args
        final String playerName = args[0];
        final String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        // Find players
        final UUID player = PlayerCache.uuidForName(playerName);
        if (player == null) {
            throw new BansException("Unknown player: " + playerName);
        }
        final UUID admin = getSenderUuid(sender);
        // Build ban
        final BanTable ban = new BanTable(type, player, admin, reason, new Date(), null);
        storeBan(sender, ban);
        sender.sendMessage(text("Player " + playerName + " " + type.getPassive(), YELLOW));
        return true;
    }

    public boolean issueTemp(BanType type, CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        // Build args
        final String playerName = args[0];
        final String timeArg = args[1];
        final String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        // Parse expiry
        final Timespan timespan = Timespan.parseTimespan(timeArg);
        if (timespan == null) {
            throw new BansException("Bad time format: " + timeArg);
        }
        // Find players
        final UUID player = PlayerCache.uuidForName(playerName);
        if (player == null) {
            throw new BansException("Unknown player: " + playerName);
        }
        final UUID admin = getSenderUuid(sender);
        // Build ban
        final Date now = new Date();
        final BanTable ban = new BanTable(type, player, admin, reason, now, timespan.addTo(now));
        storeBan(sender, ban);
        sender.sendMessage("Player " + playerName + " " + type.getPassive() + " for " + timespan.toNiceString());
        return true;
    }

    public boolean liftBan(BanType type, CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        // Build args
        final String playerName = args[0];
        // Find player
        final UUID player = PlayerCache.uuidForName(playerName);
        if (player == null) {
            throw new BansException("Unknown player: " + playerName);
        }
        // Lift ban
        liftBan(sender, player, type);
        sender.sendMessage("Player " + playerName + " " + type.lift().getPassive());
        return true;
    }

    public boolean ban(CommandSender sender, String[] args) {
        return issuePerm(BanType.BAN, sender, args);
    }

    public boolean tempban(CommandSender sender, String[] args) {
        return issueTemp(BanType.BAN, sender, args);
    }

    public boolean unban(CommandSender sender, String[] args) {
        return liftBan(BanType.BAN, sender, args);
    }

    public boolean mute(CommandSender sender, String[] args) {
        return issuePerm(BanType.MUTE, sender, args);
    }

    public boolean tempmute(CommandSender sender, String[] args) {
        return issueTemp(BanType.MUTE, sender, args);
    }

    public boolean unmute(CommandSender sender, String[] args) {
        return liftBan(BanType.MUTE, sender, args);
    }

    public boolean jail(CommandSender sender, String[] args) {
        return issuePerm(BanType.JAIL, sender, args);
    }

    public boolean tempjail(CommandSender sender, String[] args) {
        return issueTemp(BanType.JAIL, sender, args);
    }

    public boolean unjail(CommandSender sender, String[] args) {
        return liftBan(BanType.JAIL, sender, args);
    }

    public boolean kick(CommandSender sender, String[] args) {
        return issuePerm(BanType.KICK, sender, args);
    }

    public boolean warn(CommandSender sender, String[] args) {
        return issuePerm(BanType.WARNING, sender, args);
    }

    public boolean note(CommandSender sender, String[] args) {
        return issuePerm(BanType.NOTE, sender, args);
    }

    public boolean showban(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        String idArg = args[0];
        // Parse args
        int id = -1;
        try {
            id = Integer.parseInt(idArg);
        } catch (NumberFormatException nfe) {
            throw new BansException("Invalid ban id: " + idArg);
        }
        if (id < 0) {
            throw new BansException("Invalid ban id: " + idArg);
        }
        // Fetch ban
        BanTable ban = plugin.database.getBan(id);
        if (ban != null && ban.getType() == BanType.NOTE && !sender.hasPermission("bans.note")) {
            ban = null;
        }
        if (ban == null) {
            throw new BansException("Ban not found: " + id);
        }
        Date now = new Date();
        plugin.database.updateBan(ban, now);
        // Send info
        sender.sendMessage(textOfChildren(text("[", GOLD), text("Ban Info", YELLOW), text("]", GOLD)));
        sender.sendMessage(textOfChildren(text("Id: ", GRAY), text(ban.getId())));
        sender.sendMessage(textOfChildren(text("Type: ", GRAY), text(ban.getType().getNiceName())));
        sender.sendMessage(textOfChildren(text("Player: ", GRAY), text(ban.getPlayerName())));
        sender.sendMessage(textOfChildren(text("Issued by: ", GRAY), text(ban.getAdminName())));
        sender.sendMessage(textOfChildren(text("Time: ", GRAY), text(Msg.formatDate(ban.getTime()))));
        if (ban.getExpiry() != null) {
            sender.sendMessage(textOfChildren(text("Expiry: ", GRAY), text(Msg.formatDate(ban.getExpiry()))));
            Timespan duration = Timespan.difference(ban.getTime(), ban.getExpiry());
            sender.sendMessage(textOfChildren(text("Duration: ", GRAY), text(duration.toNiceString())));
            if (!ban.hasExpired()) {
                Timespan timeLeft = Timespan.difference(now, ban.getExpiry());
                sender.sendMessage(textOfChildren(text("Time left: ", GRAY), text(timeLeft.toNiceString())));
            }
        }
        if (ban.getReason() != null) {
            sender.sendMessage(textOfChildren(text("Reason: ", GRAY), text(ban.getReason())));
        }
        for (MetaTable meta : plugin.database.findMeta(ban)) {
            sender.sendMessage(textOfChildren(text(meta.getType().name().toLowerCase(), YELLOW),
                                              text(" " + Msg.formatDate(meta.getTime()), DARK_GRAY),
                                              text(getSenderName(meta.getSender()) + ": ", GRAY),
                                              text(meta.getContent())));
        }
        return true;
    }

    public boolean editban(CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        final String idArg = args[0];
        final String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        // Parse args
        int id = -1;
        try {
            id = Integer.parseInt(idArg);
        } catch (NumberFormatException nfe) {
            throw new BansException("Invalid ban id: " + idArg);
        }
        if (id < 0) {
            throw new BansException("Invalid ban id: " + idArg);
        }
        if (reason == null || reason.length() == 0) {
            throw new BansException("Ban reason required");
        }
        // Fetch ban
        BanTable ban = plugin.database.getBan(id);
        if (ban == null) {
            throw new BansException("Ban not found: " + id);
        }
        if (!ban.isOwnedBy(sender) && !sender.hasPermission("bans.override.edit")) {
            throw new BansException("You don't have permission to edit this ban");
        }
        // Edit ban
        ban.setReason(reason);
        plugin.database.storeBan(ban);
        final MetaTable meta = new MetaTable(ban.getId(), MetaType.MODIFY, getSenderUuid(sender), new Date(), "Edit: " + reason);
        plugin.database.storeMeta(meta);
        // Finish
        sender.sendMessage(text("Ban reason updated", YELLOW));
        return true;
    }

    public boolean commentban(CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        final String idArg = args[0];
        final String comment = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        // Parse args
        int id = -1;
        try {
            id = Integer.parseInt(idArg);
        } catch (NumberFormatException nfe) {
            throw new BansException("Invalid ban id: " + idArg);
        }
        if (id < 0) {
            throw new BansException("Invalid ban id: " + idArg);
        }
        if (comment == null || comment.length() == 0) {
            throw new BansException("Comment required");
        }
        // Fetch ban
        final BanTable ban = plugin.database.getBan(id);
        if (ban == null) {
            throw new BansException("Ban not found: " + id);
        }
        if (!ban.isOwnedBy(sender) && !sender.hasPermission("bans.override.comment")) {
            throw new BansException("You don't have permission comment on this ban");
        }
        // Edit ban
        final MetaTable meta = new MetaTable(ban.getId(), MetaType.COMMENT, getSenderUuid(sender), new Date(), comment);
        plugin.database.storeMeta(meta);
        // Finish
        plugin.broadcast(meta);
        sender.sendMessage(text("Comment stored", YELLOW));
        return true;
    }

    public boolean deleteban(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final String idArg = args[0];
        // Parse args
        int id = -1;
        try {
            id = Integer.parseInt(idArg);
        } catch (NumberFormatException nfe) {
            throw new BansException("Invalid ban id: " + idArg);
        }
        if (id < 0) {
            throw new BansException("Invalid ban id: " + idArg);
        }
        // Fetch ban
        final BanTable ban = plugin.database.getBan(id);
        if (ban == null) {
            throw new BansException("Ban not found: " + id);
        }
        if (!ban.isOwnedBy(sender) && !sender.hasPermission("bans.override.delete")) {
            throw new BansException("You don't have permission to delete this ban");
        }
        // Delete ban
        plugin.database.deleteBan(ban);
        // Finish
        sender.sendMessage(text("Ban deleted", YELLOW));
        return true;
    }

    public boolean mybans(CommandSender sender, String[] args) {
        if (args.length != 0) return false;
        if (!(sender instanceof OfflinePlayer)) {
            throw new BansException("Player expected");
        }
        final UUID player = ((OfflinePlayer) sender).getUniqueId();
        final List<BanTable> playerBans = plugin.database.getPlayerBans(player);
        // Check validity
        if (player == null || playerBans.isEmpty()) {
            sender.sendMessage(text("No bans on your record", DARK_AQUA));
            return true;
        }
        sender.sendMessage(text("Your ban record:", DARK_AQUA));
        for (BanTable ban : playerBans) {
            if (ban.getType() == BanType.NOTE) continue;
            sender.sendMessage(textOfChildren(text(Msg.formatDate(ban.getTime()), AQUA, ITALIC),
                                              text(" - ", DARK_AQUA),
                                              text(ban.getType().unlift().getPassive(), AQUA),
                                              text(" by ", DARK_AQUA),
                                              text(ban.getAdminName(), AQUA)));
            if (ban.getReason() != null) {
                sender.sendMessage(textOfChildren(text("  Reason: ", DARK_AQUA),
                                                  text(ban.getReason(), AQUA, ITALIC)));
            }
        }
        return true;
    }

    public boolean baninfo(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final String playerName = args[0];
        final UUID player = PlayerCache.uuidForName(playerName);
        // Check validity
        if (player == null) {
            throw new BansException("Player not found: " + playerName);
        }
        final List<BanTable> playerBans = plugin.database.getPlayerBans(player);
        if (playerBans.isEmpty()) {
            throw new BansException("No bans: " + playerName);
        }
        // Update
        plugin.database.updateBans(playerBans);
        // Build message
        sender.sendMessage(textOfChildren(text("[", GOLD),
                                          text("Ban record of ", YELLOW),
                                          text(playerName, YELLOW, ITALIC),
                                          text("]", GOLD)));
        if (playerBans.isEmpty()) {
            sender.sendMessage(text("No entries found", RED));
            return true;
        }
        final Map<BanType, Integer> count = new EnumMap<BanType, Integer>(BanType.class);
        for (BanType type : BanType.values()) count.put(type, 0);
        Collections.sort(playerBans, Comparator.comparing(BanTable::getTime));
        for (BanTable ban : playerBans) {
            if (ban.getType() == BanType.NOTE) {
                if (!sender.hasPermission("bans.note")) continue;
            }
            final String cmd = "/showban " + ban.getId();
            sender.sendMessage(textOfChildren((ban.getType().isActive()
                                               ? text(ban.getType().getNiceName(), DARK_RED, BOLD)
                                               : text(ban.getType().getNiceName(), RED)),
                                              space(),
                                              text("[", YELLOW),
                                              text(String.format("%04d", ban.getId()), WHITE),
                                              text("]", YELLOW),
                                              space(),
                                              text(Msg.formatDate(ban.getTime()), GRAY),
                                              (ban.getExpiry() != null
                                               ? textOfChildren(space(),
                                                                text(Msg.formatDate(ban.getExpiry()), DARK_GRAY),
                                                                space(),
                                                                text("(", WHITE),
                                                                text("" + Timespan.difference(ban.getTime(), ban.getExpiry()), GRAY),
                                                                text(")", WHITE))
                                               : empty()))
                               .insertion(cmd)
                               .hoverEvent(showText(text(cmd, GRAY)))
                               .clickEvent(suggestCommand(cmd)));
            if (ban.getReason() != null) {
                sender.sendMessage(textOfChildren(text(ban.getAdminName() + ": ", GRAY),
                                                  text(ban.getReason(), WHITE)));
            }
            count.put(ban.getType(), count.get(ban.getType()) + 1);
        }
        final List<Component> line = new ArrayList<>();
        // Statistics
        int bans = count.get(BanType.BAN) + count.get(BanType.UNBAN);
        int kicks = count.get(BanType.KICK);
        int mutes = count.get(BanType.MUTE) + count.get(BanType.UNMUTE);
        int warns = count.get(BanType.WARNING) + count.get(BanType.WARNED);
        int jails = count.get(BanType.JAIL) + count.get(BanType.UNJAIL);
        int notes = count.get(BanType.NOTE);
        if (bans > 0) {
            line.add(text(" Bans: " + bans, RED));
        }
        if (kicks > 0) {
            line.add(text(" Kicks: " + kicks, RED));
        }
        if (mutes > 0) {
            line.add(text(" Mutes: " + mutes, RED));
        }
        if (warns > 0) {
            line.add(text(" Warnings: " + warns, RED));
        }
        if (jails > 0) {
            line.add(text(" Jails: " + jails, RED));
        }
        if (notes > 0) {
            line.add(text(" Notes: " + notes, RED));
        }
        if (count.get(BanType.BAN) > 0) {
            line.add(text(" BANNED", DARK_RED, BOLD));
        }
        if (count.get(BanType.MUTE) > 0) {
            line.add(text(" MUTED", DARK_RED, BOLD));
        }
        if (count.get(BanType.JAIL) > 0) {
            line.add(text(" JAILED", DARK_RED, BOLD));
        }
        sender.sendMessage(join(noSeparators(), line));
        return true;
    }

    public boolean bans(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return false;
        } else if (args.length == 1 && "UpdateBans".equalsIgnoreCase(args[0])) {
            plugin.database.updateAllBans();
            sender.sendMessage("Updated all bans. See console.");
            return true;
        } else if (args.length == 1 && "SetJail".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player)) {
                throw new BansException("Player expected");
            }
            plugin.setJailLocation(((Player) sender).getLocation());
            sender.sendMessage(text("Jail location set", YELLOW));
            return true;
        } else if (args.length == 1 && "Reload".equalsIgnoreCase(args[0])) {
            plugin.reloadConfig();
            sender.sendMessage(text("Configuration reloaded", YELLOW));
            return true;
        }
        switch (args[0]) {
        case "processwhitelist": {
            if (args.length != 2) return false;
            File file = new File(plugin.getDataFolder(), args[1]);
            @SuppressWarnings("unchecked")
            List<Object> list1 = (List<Object>) Json.load(file, List.class);
            if (list1 == null) {
                sender.sendMessage("Whitelist not found: " + args[1]);
                return true;
            }
            Set<UUID> banned = plugin.database.findBannedUuids();
            sender.sendMessage("Found " + banned.size() + " banned uuids");
            List<Map<Object, Object>> list2 = new ArrayList<>();
            for (Object o : list1) {
                if (!(o instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<Object, Object> map = (Map<Object, Object>) o;
                Object p = map.get("uuid");
                if (p == null) continue;
                UUID uuid = UUID.fromString(p.toString());
                if (banned.contains(uuid)) {
                    sender.sendMessage("Removing banned: " + uuid + ": " + map.get("name"));
                    continue;
                }
                list2.add(map);
            }
            Json.save(file, list2, true);
            sender.sendMessage("Processed " + file.getName() + ": " + list1.size() + " => " + list2.size());
            return true;
        }
        default:
            return false;
        }
    }

    public boolean banlist(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        final int page;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException nfe) {
                sender.sendMessage("Invalid page: " + args[0]);
                return true;
            }
            if (page < 1) {
                sender.sendMessage("Invalid page: " + page);
                return true;
            }
        } else {
            page = 1;
        }
        plugin.database.getDb().find(BanTable.class)
            .limit(10)
            .offset((page - 1) * 10)
            .orderByDescending("time")
            .findListAsync(list -> banlistCallback(sender, list, page));
        return true;
    }

    private void banlistCallback(CommandSender sender, List<BanTable> list, int page) {
        if (list.isEmpty()) {
            sender.sendMessage(text("Page " + page + " is empty!", RED));
            return;
        }
        sender.sendMessage(text("Bans list (page " + page + ")", YELLOW));
        for (BanTable ban : list) {
            if (ban.getType() == BanType.NOTE) {
                if (!sender.hasPermission("bans.note")) continue;
            }
            sender.sendMessage(textOfChildren(text("[", YELLOW),
                                              text(String.format("%04d", ban.getId()), WHITE),
                                              text("]", YELLOW),
                                              space(),
                                              (ban.getType().isActive()
                                               ? text(ban.getType().getNiceName(), DARK_RED, BOLD)
                                               : text(ban.getType().getNiceName(), RED)),
                                              space(),
                                              text(Msg.formatDateShort(ban.getTime()), GRAY),
                                              space(),
                                              text(ban.getPlayerName(), WHITE),
                                              text("/", DARK_GRAY),
                                              text(ban.getAdminName(), YELLOW),
                                              space(),
                                              text(ban.getReason(), GRAY))
                               .hoverEvent(showText(text("/showban " + ban.getId(), GRAY)))
                               .clickEvent(suggestCommand("/showban " + ban.getId())));
        }
    }

    private String parseIP(String in) {
        if (in.contains(".")) {
            String[] comps = in.split("\\.");
            if (comps.length != 4) return null;
            for (int i = 0; i < 4; i += 1) {
                int num;
                try {
                    num = Integer.parseInt(comps[i]);
                } catch (NumberFormatException nfe) {
                    return null;
                }
                if (num < 0 || num > 255) return null;
                comps[i] = "" + num;
            }
            return String.join(".", comps);
        } else if (in.contains(":")) {
            String[] comps = in.split(":");
            if (comps.length != 8) return null;
            for (int i = 0; i < 8; i += 1) {
                if (comps[i].length() != 4) return null;
                int num;
                try {
                    num = Integer.parseInt(comps[i], 16);
                } catch (NumberFormatException nfe) {
                    return null;
                }
                comps[i] = Integer.toHexString(num);
            }
            return String.join(":", comps);
        } else {
            return null;
        }
    }

    public boolean ipban(CommandSender sender, String[] args) {
        if (args.length == 0) return false;
        switch (args[0]) {
        case "lift": {
            if (args.length != 2) return false;
            String ip = parseIP(args[1]);
            if (ip == null) {
                sender.sendMessage(text("Invalid IP: " + args[1], RED));
                return true;
            }
            plugin.database.getDb().find(IPBanTable.class)
                .eq("ip", ip)
                .deleteAsync(count -> {
                        if (count == 0) {
                            sender.sendMessage(text("IP ban not found: " + ip, RED));
                        } else {
                            sender.sendMessage(text(count + " IP ban(s) lifted: " + ip, YELLOW));
                        }
                    });
            return true;
        }
        case "list": {
            if (args.length > 2) return false;
            final int page;
            if (args.length >= 2) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException nfe) {
                    sender.sendMessage("Invalid page: " + args[0]);
                    return true;
                }
                if (page < 1) {
                    sender.sendMessage("Invalid page: " + page);
                    return true;
                }
            } else {
                page = 1;
            }
            plugin.database.getDb().find(IPBanTable.class)
                .limit(10)
                .offset((page - 1) * 10)
                .orderByDescending("time")
                .findListAsync(list -> {
                        if (list.isEmpty()) {
                            sender.sendMessage(text("Page is empty!", RED));
                            return;
                        }
                        for (IPBanTable row : list) {
                            sender.sendMessage(textOfChildren(text("- " + row.getIp(), YELLOW),
                                                              text(" " + row.getAdminName(), BLUE),
                                                              text(" " + row.getReason(), WHITE))
                                               .insertion(row.getIp()));
                        }
                    });
            return true;
        }
        default: {
            String ip = parseIP(args[0]);
            if (ip == null) {
                sender.sendMessage(text("Invalid IP: " + args[0], RED));
                return true;
            }
            String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            IPBanTable row = new IPBanTable(ip, getSenderUuid(sender), reason, new Date());
            try {
                plugin.database.getDb().insert(row);
            } catch (Exception e) {
                e.printStackTrace();
                sender.sendMessage(text("IP already banned: " + ip + " (or error, see console)", RED));
            }
            sender.sendMessage(text("IP banned: " + ip, YELLOW));
            plugin.broadcast(row);
            return true;
        }
        }
    }
}

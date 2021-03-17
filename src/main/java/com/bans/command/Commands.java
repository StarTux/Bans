package com.winthier.bans.command;

import com.winthier.bans.Ban;
import com.winthier.bans.BanType;
import com.winthier.bans.BansPlugin;
import com.winthier.bans.PlayerInfo;
import com.winthier.bans.sql.BanTable;
import com.winthier.bans.sql.IPBanTable;
import com.winthier.bans.sql.MetaTable.MetaType;
import com.winthier.bans.sql.MetaTable;
import com.winthier.bans.util.Msg;
import com.winthier.bans.util.Timespan;
import com.winthier.playercache.PlayerCache;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

public final class Commands implements CommandExecutor {
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
            sender.sendMessage("" + ChatColor.RED + "An internal error occured. Please report this incident.");
        } catch (IllegalArgumentException iae) {
            iae.printStackTrace();
            sender.sendMessage("" + ChatColor.RED + "An internal error occured. Please report this incident.");
        } catch (InvocationTargetException ite) {
            if (ite.getCause() instanceof CommandException) {
                sender.sendMessage("" + ChatColor.RED + ite.getCause().getMessage());
            } else {
                ite.printStackTrace();
                sender.sendMessage("" + ChatColor.RED + "An internal error occured. Please report this incident.");
            }
        }
        return true;
    }

    private void storeBan(CommandSender sender, BanTable ban) {
        List<BanTable> playerBans = plugin.database.getPlayerBans(ban.getPlayer());
        switch (ban.getType()) {
        case BAN:
        case MUTE:
        case JAIL:
            for (BanTable record : playerBans) {
                if (ban.getType().isActive() && ban.getType() == record.getType()) {
                    throw new CommandException(Msg.format("Player %s is already %s.", ban.getPlayerName(), ban.getType().getPassive()));
                }
            }
            break;
        case KICK:
            if (!plugin.isOnline(ban.getPlayer())) {
                throw new CommandException("Player " + ban.getPlayerName() + " is not online.");
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
            throw new CommandException(Msg.format("%s could not be %s: Not your %s.",
                                                  getPlayerName(player), newType.getPassive(),
                                                  type.getNiceName().toLowerCase()));
        }
        if (found == null) throw new CommandException(Msg.format("%s is not %s.", getPlayerName(player), type.getPassive()));
        // Set the admin to whoever lifted the ban for the announcement.
        Ban ban = new Ban(found.getId(), found.getType(), new PlayerInfo(found.getPlayer()),
                          (sender instanceof OfflinePlayer ? new PlayerInfo((OfflinePlayer) sender) : null),
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
        String playerName = args[0];
        String reason = Msg.buildMessage(args, 1);
        // Find players
        UUID player = PlayerCache.uuidForName(playerName);
        if (player == null) throw new CommandException("Unknown player: " + playerName);
        UUID admin = getSenderUuid(sender);
        // Build ban
        BanTable ban = new BanTable(type, player, admin, reason, new Date(), null);
        storeBan(sender, ban);
        Msg.send(sender, "&ePlayer %s %s.", playerName, type.getPassive());
        return true;
    }

    public boolean issueTemp(BanType type, CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        // Build args
        String playerName = args[0];
        String timeArg = args[1];
        String reason = Msg.buildMessage(args, 2);
        // Parse expiry
        Timespan timespan = Timespan.parseTimespan(timeArg);
        if (timespan == null) throw new CommandException("Bad time format: " + timeArg);
        // Find players
        UUID player = PlayerCache.uuidForName(playerName);
        if (player == null) throw new CommandException("Unknown player: " + playerName);
        UUID admin = getSenderUuid(sender);
        // Build ban
        Date now = new Date();
        BanTable ban = new BanTable(type, player, admin, reason, now, timespan.addTo(now));
        storeBan(sender, ban);
        Msg.send(sender, "&ePlayer %s %s for %s.", playerName, type.getPassive(), timespan.toNiceString());
        return true;
    }

    public boolean liftBan(BanType type, CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        // Build args
        String playerName = args[0];
        // Find player
        UUID player = PlayerCache.uuidForName(playerName);
        if (player == null) throw new CommandException("Unknown player: " + playerName);
        // Lift ban
        liftBan(sender, player, type);
        Msg.send(sender, "&ePlayer %s %s.", playerName, type.lift().getPassive());
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
            throw new CommandException("Invalid ban id: " + idArg);
        }
        if (id < 0) throw new CommandException("Invalid ban id: " + idArg);
        // Fetch ban
        BanTable ban = plugin.database.getBan(id);
        if (ban != null && ban.getType() == BanType.NOTE && !sender.hasPermission("bans.note")) ban = null;
        if (ban == null) throw new CommandException("Ban not found: " + id);
        Date now = new Date();
        plugin.database.updateBan(ban, now);
        // Send info
        StringBuilder sb = new StringBuilder();
        sb.append(Msg.format("&6[&eBan Info&6]"));
        sb.append(Msg.format("\n &7Id: &c%04d", ban.getId()));
        sb.append(Msg.format("\n &7Type: &c%s", ban.getType().getNiceName()));
        sb.append(Msg.format("\n &7Player: &c%s", ban.getPlayerName()));
        sb.append(Msg.format("\n &7Issued by: &c%s", ban.getAdminName()));
        sb.append(Msg.format("\n &7Time: &c%s", Msg.formatDate(ban.getTime())));
        if (ban.getExpiry() != null) {
            sb.append(Msg.format("\n &7Expiry: &c%s", Msg.formatDate(ban.getExpiry())));
            Timespan duration = Timespan.difference(ban.getTime(), ban.getExpiry());
            sb.append(Msg.format("\n &7Duration: &c%s", duration.toNiceString()));
            if (!ban.hasExpired()) {
                Timespan timeLeft = Timespan.difference(now, ban.getExpiry());
                sb.append(Msg.format("\n &7Time left: &c%s", timeLeft.toNiceString()));
            }
        }
        if (ban.getReason() != null) {
            sb.append(Msg.format("\n &7Reason: &c%s", ban.getReason()));
        }
        sender.sendMessage(sb.toString());
        for (MetaTable meta : plugin.database.findMeta(ban)) {
            sender.sendMessage(Msg.format("&e%s &8%s &f%s&7: %s",
                                          meta.getType().name().toLowerCase(),
                                          Msg.formatDate(meta.getTime()),
                                          getSenderName(meta.getSender()),
                                          meta.getContent()));
        }
        return true;
    }

    public boolean editban(CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        String idArg = args[0];
        String reason = Msg.buildMessage(args, 1);
        // Parse args
        int id = -1;
        try {
            id = Integer.parseInt(idArg);
        } catch (NumberFormatException nfe) {
            throw new CommandException("Invalid ban id: " + idArg);
        }
        if (id < 0) throw new CommandException("Invalid ban id: " + idArg);
        if (reason == null || reason.length() == 0) throw new CommandException("Ban reason required");
        // Fetch ban
        BanTable ban = plugin.database.getBan(id);
        if (ban == null) throw new CommandException("Ban not found: " + id);
        if (!ban.isOwnedBy(sender) && !sender.hasPermission("bans.override.edit")) {
            throw new CommandException("You don't have permission to edit this ban");
        }
        // Edit ban
        ban.setReason(reason);
        plugin.database.storeBan(ban);
        MetaTable meta = new MetaTable(ban.getId(), MetaType.MODIFY, getSenderUuid(sender), new Date(), "Edit: " + reason);
        plugin.database.storeMeta(meta);
        // Finish
        Msg.send(sender, "&eBan reason updated");
        return true;
    }

    public boolean commentban(CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        String idArg = args[0];
        String comment = Msg.buildMessage(args, 1);
        // Parse args
        int id = -1;
        try {
            id = Integer.parseInt(idArg);
        } catch (NumberFormatException nfe) {
            throw new CommandException("Invalid ban id: " + idArg);
        }
        if (id < 0) throw new CommandException("Invalid ban id: " + idArg);
        if (comment == null || comment.length() == 0) throw new CommandException("Comment required");
        // Fetch ban
        BanTable ban = plugin.database.getBan(id);
        if (ban == null) throw new CommandException("Ban not found: " + id);
        if (!ban.isOwnedBy(sender) && !sender.hasPermission("bans.override.comment")) {
            throw new CommandException("You don't have permission comment on this ban");
        }
        // Edit ban
        MetaTable meta = new MetaTable(ban.getId(), MetaType.COMMENT, getSenderUuid(sender), new Date(), comment);
        plugin.database.storeMeta(meta);
        // Finish
        Msg.send(sender, "&eComment stored");
        return true;
    }

    public boolean deleteban(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        String idArg = args[0];
        // Parse args
        int id = -1;
        try {
            id = Integer.parseInt(idArg);
        } catch (NumberFormatException nfe) {
            throw new CommandException("Invalid ban id: " + idArg);
        }
        if (id < 0) throw new CommandException("Invalid ban id: " + idArg);
        // Fetch ban
        BanTable ban = plugin.database.getBan(id);
        if (ban == null) throw new CommandException("Ban not found: " + id);
        if (!ban.isOwnedBy(sender) && !sender.hasPermission("bans.override.delete")) {
            throw new CommandException("You don't have permission to delete this ban");
        }
        // Delete ban
        plugin.database.deleteBan(ban);
        // Finish
        Msg.send(sender, "&eBan deleted");
        return true;
    }

    public boolean mybans(CommandSender sender, String[] args) {
        if (args.length != 0) return false;
        if (!(sender instanceof OfflinePlayer)) throw new CommandException("Player expected");
        UUID player = ((OfflinePlayer) sender).getUniqueId();
        List<BanTable> playerBans = plugin.database.getPlayerBans(player);
        // Check validity
        if (player == null || playerBans.isEmpty()) {
            Msg.send(sender, "&3No bans on your record.");
            return true;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(Msg.format("&3Your ban record:"));
        for (BanTable ban : playerBans) {
            if (ban.getType() == BanType.NOTE) continue;
            sb.append(Msg.format("\n&b &o%s&3 -&b %s by &o%s&b.", Msg.formatDate(ban.getTime()), ban.getType().unlift().getPassive(), ban.getAdminName()));
            if (ban.getReason() != null) {
                sb.append(Msg.format("\n &3Reason: &b&o%s", ban.getReason()));
            }
        }
        sender.sendMessage(sb.toString());
        return true;
    }

    public boolean baninfo(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        String playerName = args[0];
        UUID player = PlayerCache.uuidForName(playerName);
        // Check validity
        if (player == null) throw new CommandException("Player not found: " + playerName);
        List<BanTable> playerBans = plugin.database.getPlayerBans(player);
        if (playerBans.isEmpty()) throw new CommandException("No bans: " + playerName);
        // Update
        plugin.database.updateBans(playerBans);
        // Build message
        sender.sendMessage(Msg.format("&6[&eBan record of &6&o%s&6]", playerName));
        if (playerBans.isEmpty()) {
            sender.sendMessage(Msg.format("&e No entries found"));
            return true;
        }
        Map<BanType, Integer> count = new EnumMap<BanType, Integer>(BanType.class);
        for (BanType type : BanType.values()) count.put(type, 0);
        Collections.sort(playerBans, (o1, o2) -> o1.getTime().compareTo(o2.getTime()));
        for (BanTable ban : playerBans) {
            StringBuilder sb = new StringBuilder();
            if (ban.getType() == BanType.NOTE) {
                if (!sender.hasPermission("bans.note")) continue;
            }
            if (ban.getType().isActive()) {
                sb.append(Msg.format("&4&l"));
            } else {
                sb.append(Msg.format("&c"));
            }
            sb.append(Msg.format("%s &e[&f%04d&e] &7%s", ban.getType().getNiceName(), ban.getId(), Msg.formatDate(ban.getTime())));
            if (ban.getExpiry() != null) {
                sb.append(Msg.format(" &8%s &f(&7%s&f)", Msg.formatDate(ban.getExpiry()), Timespan.difference(ban.getTime(), ban.getExpiry())));
            }
            ComponentBuilder cb = new ComponentBuilder(sb.toString());
            BaseComponent[] tooltip = TextComponent.fromLegacyText(ChatColor.YELLOW + "/showban " + ban.getId());
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip));
            cb.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/showban " + ban.getId()));
            sender.sendMessage(cb.create());
            String reason = ban.getReason();
            if (reason == null) reason = "N/A";
            sender.sendMessage(Msg.format("&7&o%s&8:&f %s", ban.getAdminName(), reason));
            count.put(ban.getType(), count.get(ban.getType()) + 1);
        }
        StringBuilder sb = new StringBuilder();
        // Statistics
        int bans = count.get(BanType.BAN) + count.get(BanType.UNBAN);
        int kicks = count.get(BanType.KICK);
        int mutes = count.get(BanType.MUTE) + count.get(BanType.UNMUTE);
        int warns = count.get(BanType.WARNING) + count.get(BanType.WARNED);
        int jails = count.get(BanType.JAIL) + count.get(BanType.UNJAIL);
        int notes = count.get(BanType.NOTE);
        if (bans > 0) sb.append(Msg.format("&c Bans: %s", bans));
        if (kicks > 0) sb.append(Msg.format("&c Kicks: %s", kicks));
        if (mutes > 0) sb.append(Msg.format("&c Mutes: %s", mutes));
        if (warns > 0) sb.append(Msg.format("&c Warnings: %s", warns));
        if (jails > 0) sb.append(Msg.format("&c Jails: %s", jails));
        if (notes > 0) sb.append(Msg.format("&c Notes: %s", notes));
        if (count.get(BanType.BAN) > 0) sb.append(Msg.format("&4&l BANNED"));
        if (count.get(BanType.MUTE) > 0) sb.append(Msg.format("&4&l MUTED"));
        if (count.get(BanType.JAIL) > 0) sb.append(Msg.format("&4&l JAILED"));
        sender.sendMessage(sb.toString());
        return true;
    }

    public boolean bans(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return false;
        } else if (args.length == 1 && "UpdateBans".equalsIgnoreCase(args[0])) {
            plugin.database.updateAllBans();
            sender.sendMessage("Updated all bans. See console.");
        } else if (args.length == 1 && "SetJail".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player)) throw new CommandException("Player expected");
            plugin.setJailLocation(((Player) sender).getLocation());
            Msg.send(sender, "&eJail location set");
        } else if (args.length == 1 && "Reload".equalsIgnoreCase(args[0])) {
            plugin.reloadConfig();
            Msg.send(sender, "&eConfiguration reloaded");
        }
        return true;
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

    void banlistCallback(CommandSender sender, List<BanTable> list, int page) {
        if (list.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Page " + page + " is empty!");
            return;
        }
        sender.sendMessage(ChatColor.YELLOW + "Bans list (page " + page + ")");
        for (BanTable ban : list) {
            if (ban.getType() == BanType.NOTE) {
                if (!sender.hasPermission("bans.note")) continue;
            }
            String banColor = Msg.format(ban.getType().isActive() ? "&4&l" : "&c");
            ComponentBuilder cb = new ComponentBuilder();
            cb.append(Msg.format("&e[&f%04d&e] %s%s&7 %s&r %s&8/&e%s&7 %s",
                                 ban.getId(), banColor, ban.getType().getNiceName(),
                                 Msg.formatDateShort(ban.getTime()),
                                 ban.getPlayerName(), ban.getAdminName(), ban.getReason()));
            BaseComponent[] tooltip = TextComponent.fromLegacyText(ChatColor.YELLOW + "/showban " + ban.getId());
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip));
            cb.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/showban " + ban.getId()));
            sender.sendMessage(cb.create());
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
                sender.sendMessage(ChatColor.RED + "Invalid IP: " + args[1]);
                return true;
            }
            plugin.database.getDb().find(IPBanTable.class)
                .eq("ip", ip)
                .deleteAsync(count -> {
                        if (count == 0) {
                            sender.sendMessage(Component.text("IP ban not found: " + ip).color(NamedTextColor.RED));
                        } else {
                            sender.sendMessage(Component.text(count + " IP ban(s) lifted: " + ip).color(NamedTextColor.YELLOW));
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
                            sender.sendMessage(ChatColor.RED + "Page is empty!");
                            return;
                        }
                        for (IPBanTable row : list) {
                            Component component = Component.text("- " + row.getIp()).color(NamedTextColor.YELLOW).insertion(row.getIp())
                                .append(Component.text(" " + row.getAdminName()).color(NamedTextColor.BLUE))
                                .append(Component.text(" " + row.getReason()).color(NamedTextColor.WHITE));
                            sender.sendMessage(component);
                        }
                    });
            return true;
        }
        default: {
            String ip = parseIP(args[0]);
            if (ip == null) {
                sender.sendMessage(ChatColor.RED + "Invalid IP: " + args[0]);
                return true;
            }
            String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            IPBanTable row = new IPBanTable(ip, getSenderUuid(sender), reason, new Date());
            try {
                plugin.database.getDb().insert(row);
            } catch (Exception e) {
                e.printStackTrace();
                sender.sendMessage(ChatColor.RED + "IP already banned: " + ip + " (or error, see console)");
            }
            sender.sendMessage(ChatColor.YELLOW + "IP banned: " + ip);
            plugin.broadcast(row);
            return true;
        }
        }
    }
}

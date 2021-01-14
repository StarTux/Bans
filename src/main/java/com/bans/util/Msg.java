package com.winthier.bans.util;

import com.winthier.bans.Ban;
import com.winthier.bans.BanType;
import com.winthier.bans.BansPlugin;
import com.winthier.bans.sql.BanTable;
import com.winthier.bans.sql.MetaTable;
import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Msg {
    private Msg() { }

    public static String format(String msg, Object... args) {
        msg = ChatColor.translateAlternateColorCodes('&', msg);
        if (args.length > 0) msg = String.format(msg, args);
        return msg;
    }

    public static void send(CommandSender sender, String msg, Object... args) {
        sender.sendMessage(format(msg, args));
    }

    public static String formatDate(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return String.format("%s %02d %02d %02d:%02d",
                             DateFormatSymbols.getInstance().getShortMonths()[cal.get(Calendar.MONTH)],
                             cal.get(Calendar.DAY_OF_MONTH),
                             cal.get(Calendar.YEAR),
                             cal.get(Calendar.HOUR_OF_DAY),
                             cal.get(Calendar.MINUTE));
    }

    public static String formatDateShort(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return String.format("%d/%d", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * Build a message from an array starting with an index.  This
     * is used for turning command line arguments into a ban
     * reason.
     */
    public static String buildMessage(String[] args, int beginIndex) {
        if (beginIndex >= args.length) return null;
        StringBuilder sb = new StringBuilder(args[beginIndex]);
        for (int i = beginIndex + 1; i < args.length; ++i) {
            sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }

    /**
     * Announce a recent ban to everyone who has permission.
     */
    public static void announce(BansPlugin plugin, Ban ban) {
        if (ban.getType() == BanType.NOTE) return;
        String msg;
        if (ban.getType().isLifted()) {
            msg = format("&e&o%s&e was %s by &o%s&e.",
                         ban.getPlayer().getName(), ban.getType().getPassive(), ban.getAdminName());
        } else if (ban.getExpiry() == null) {
            msg = format("&e&o%s&e was %s by &o%s&e.",
                         ban.getPlayer().getName(), ban.getType().getPassive(), ban.getAdminName());
            if (ban.getReason() != null) msg += Msg.format(" Reason: &o%s", ban.getReason());
        } else {
            Timespan timespan = Timespan.difference(ban.getTime(), ban.getExpiry());
            msg = format("&e&o%s&e was %s for &o%s&e by &o%s&e.",
                         ban.getPlayer().getName(), ban.getType().getPassive(), timespan.toNiceString(), ban.getAdminName());
            if (ban.getReason() != null) msg += Msg.format(" Reason: &o%s", ban.getReason());
        }
        plugin.getServer().getConsoleSender().sendMessage(msg);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.hasPermission("bans.notify")) {
                player.sendMessage(msg);
            }
        }
    }

    public static String getBanMessage(BansPlugin plugin, Ban ban, List<MetaTable> comments) {
        StringBuilder sb = new StringBuilder();
        sb.append(Msg.format("&cYou have been %s by &o%s&c.", ban.getType().getPassive(), ban.getAdminName()));
        if (ban.getExpiry() != null) {
            Date expiry = ban.getExpiry();
            Date now = new Date();
            Timespan timespan = Timespan.difference(now, expiry);
            if (now.compareTo(expiry) < 0) {
                sb.append(Msg.format("\n&cExpiry: &o%s&c (&o%s&c left)", Msg.formatDate(expiry), timespan.toNiceString()));
            }
        }
        if (ban.getReason() != null) {
            sb.append(Msg.format("\n&cReason: &o%s", ban.getReason()));
        }
        switch (ban.getType()) {
        case BAN:
            sb.append("\n");
            String appealMsg = plugin.getConfig().getString("AppealMessage");
            sb.append(Msg.format(appealMsg));
            //sb.append(Msg.format("\n&cAppeal at &9&nhttp://www.winthier.com/appeal"));
            break;
        default:
            break;
        }
        for (MetaTable meta : comments) {
            sb.append(Msg.format("\n&cComment: &o%s", meta.getContent()));
        }
        return sb.toString();
    }

    public static String getBanMessage(BansPlugin plugin, Ban ban) {
        return getBanMessage(plugin, ban, Collections.emptyList());
    }

    public static String getBanMessage(BansPlugin plugin, BanTable ban) {
        return getBanMessage(plugin, new Ban(ban), Collections.emptyList());
    }

    public static String getBanMessage(BansPlugin plugin, BanTable ban, List<MetaTable> comments) {
        return getBanMessage(plugin, new Ban(ban), comments);
    }
}

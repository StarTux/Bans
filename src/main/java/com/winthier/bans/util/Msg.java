package com.winthier.bans.util;

import com.winthier.bans.Ban;
import com.winthier.bans.BanType;
import com.winthier.bans.BansPlugin;
import com.winthier.bans.sql.BanTable;
import com.winthier.bans.sql.MetaTable;
import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

    public static String formatDate(long time) {
        return formatDate(new Date(time));
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
        } else if (ban.getExpiry() == 0L) {
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

    public static Component getBanComponent(BansPlugin plugin, Ban ban, List<MetaTable> comments) {
        TextComponent.Builder result = Component.text().color(NamedTextColor.RED)
            .append(Component.text("You have been " + ban.getType().getPassive() + " by "))
            .append(Component.text(ban.getAdminName(), null, TextDecoration.ITALIC))
            .append(Component.text("."));
        if (ban.getExpiry() != 0L) {
            long now = System.currentTimeMillis();
            long expiry = ban.getExpiry();
            Timespan timespan = Timespan.difference(now, expiry);
            if (now < expiry) {
                result.append(Component.text("Expiry: "))
                    .append(Component.text(formatDate(expiry), null, TextDecoration.ITALIC))
                    .append(Component.text(" ("))
                    .append(Component.text(timespan.toNiceString(), null, TextDecoration.ITALIC))
                    .append(Component.text(" left)"));
            }
        }
        if (ban.getReason() != null) {
            result.append(Component.text("\nReason: "))
                .append(Component.text(ban.getReason(), null, TextDecoration.ITALIC));
        }
        switch (ban.getType()) {
        case BAN:
            result.append(Component.text("\nAppeal at "))
                .append(Component.text("cavetale.com/ban-appeal", null, TextDecoration.UNDERLINED)
                        .hoverEvent(HoverEvent.showText(Component.text("cavetale.com/ban-appeal", NamedTextColor.RED, TextDecoration.UNDERLINED)))
                        .clickEvent(ClickEvent.openUrl("https://cavetale.com/ban-appeal")));
            break;
        default: break;
        }
        for (MetaTable meta : comments) {
            result.append(Component.text("\nComment: "))
                .append(Component.text(meta.getContent(), null, TextDecoration.ITALIC));
        }
        return result.build();
    }

    public static Component getBanComponent(BansPlugin plugin, BanTable ban, List<MetaTable> comments) {
        return getBanComponent(plugin, new Ban(ban), comments);
    }
}

package com.winthier.bans.util;

import com.winthier.bans.Ban;
import com.winthier.bans.BanType;
import com.winthier.bans.BansPlugin;
import com.winthier.bans.sql.BanTable;
import com.winthier.bans.sql.MetaTable;
import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.event.ClickEvent.openUrl;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

public final class Msg {
    private Msg() { }

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
     * Announce a recent ban to everyone who has permission.
     */
    public static void announce(BansPlugin plugin, Ban ban) {
        if (ban.getType() == BanType.NOTE) return;
        final Component msg;
        if (ban.getType().isLifted()) {
            msg = textOfChildren(text(ban.getPlayer().getName(), YELLOW, ITALIC),
                                 text(" was ", GRAY),
                                 text(ban.getType().getPassive(), YELLOW),
                                 text(" by ", GRAY),
                                 text(ban.getAdminName(), YELLOW, ITALIC));
        } else if (ban.getExpiry() == 0L) {
            msg = textOfChildren(text(ban.getPlayer().getName(), YELLOW, ITALIC),
                                 text(" was ", GRAY),
                                 text(ban.getType().getPassive(), YELLOW),
                                 text(" by ", GRAY),
                                 text(ban.getAdminName(), YELLOW, ITALIC),
                                 (ban.getReason() != null
                                  ? textOfChildren(text(" Reason: ", YELLOW),
                                                   text(ban.getReason(), YELLOW, ITALIC))
                                  : empty()));
        } else {
            final Timespan timespan = Timespan.difference(ban.getTime(), ban.getExpiry());
            msg = textOfChildren(text(ban.getPlayer().getName(), YELLOW, ITALIC),
                                 text(" was ", GRAY),
                                 text(ban.getType().getPassive(), YELLOW),
                                 text(" for ", GRAY),
                                 text(timespan.toNiceString(), YELLOW, ITALIC),
                                 text(" by ", GRAY),
                                 text(ban.getAdminName(), YELLOW, ITALIC),
                                 (ban.getReason() != null
                                  ? textOfChildren(text(" Reason: ", YELLOW),
                                                   text(ban.getReason(), YELLOW, ITALIC))
                                  : empty()));
        }
        plugin.getServer().getConsoleSender().sendMessage(msg);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.hasPermission("bans.notify")) {
                player.sendMessage(msg);
            }
        }
    }

    public static Component getBanComponent(Ban ban, List<MetaTable> comments) {
        final List<Component> result = new ArrayList<>();
        result.add(newline());
        result.add(textOfChildren(text("You have been "),
                                  text(ban.getType().getPassive(), DARK_RED),
                                  text(" by "),
                                  text(ban.getAdminName(), null, ITALIC))
                   .color(RED));
        if (ban.getExpiry() != 0L) {
            final long now = System.currentTimeMillis();
            final long expiry = ban.getExpiry();
            final Timespan timespan = Timespan.difference(now, expiry);
            if (now < expiry) {
                result.add(textOfChildren(text("Expiry: "),
                                          text(formatDate(expiry), null, ITALIC),
                                          text(" ("),
                                          text(timespan.toNiceString(), null, ITALIC),
                                          text(" left)"))
                           .color(RED));
            }
        }
        if (ban.getReason() != null) {
            result.add(newline());
            result.add(textOfChildren(text("Reason: ", RED),
                                      text(ban.getReason(), DARK_RED, ITALIC)));
        }
        if (ban.getType() == BanType.BAN) {
            result.add(newline());
            result.add(textOfChildren(text("Appeal at ", RED),
                                      text("cavetale.com/ban-appeal", DARK_RED, UNDERLINED))
                       .hoverEvent(showText(text("cavetale.com/ban-appeal", RED, UNDERLINED)))
                       .clickEvent(openUrl("https://cavetale.com/ban-appeal")));
        }
        for (MetaTable meta : comments) {
            result.add(newline());
            result.add(textOfChildren(text("Comment: "),
                                      text(meta.getContent(), null, ITALIC))
                       .color(RED));
        }
        return join(noSeparators(), result);
    }

    public static Component getBanComponent(BanTable ban, List<MetaTable> comments) {
        return getBanComponent(new Ban(ban), comments);
    }
}

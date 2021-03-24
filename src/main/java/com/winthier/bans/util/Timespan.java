package com.winthier.bans.util;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Timespan {
    public final long amount;
    public final TimeUnit unit;

    private Timespan(final long amount, final TimeUnit unit) {
        this.amount = amount;
        this.unit = unit;
    }

    public static Timespan parseTimespan(String string) {
        Pattern pattern = Pattern.compile("([0-9]+)([a-zA-Z]+)");
        Matcher matcher = pattern.matcher(string);
        if (!matcher.matches()) return null;
        long amount = 0L;
        TimeUnit unit = null;
        try {
            amount = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
        if (amount <= 0) return null;
        unit = TimeUnit.fromString(matcher.group(2));
        if (unit == null) return null;
        return new Timespan(amount, unit);
    }

    public long getMillis() {
        return amount * unit.millis;
    }

    public Date addTo(Date date) {
        return new Date(date.getTime() + getMillis());
    }

    @Override
    public String toString() {
        return "" + amount + unit.shortName;
    }

    public String toNiceString() {
        if (amount == 1) {
            return "" + amount + " " + unit.name().toLowerCase();
        } else {
            return "" + amount + " " + unit.name().toLowerCase() + "s";
        }
    }

    public static Timespan difference(Date a, Date b) {
        long diff = b.getTime() - a.getTime();
        long minutes = (diff - 1) / 1000 / 60 + 1;
        if (minutes <= 0) return new Timespan(0, TimeUnit.MINUTE);
        if (minutes <= 60) return new Timespan(minutes, TimeUnit.MINUTE);
        long hours = (diff - 1) / 1000 / 60 / 60 + 1;
        if (hours <= 72) return new Timespan(hours, TimeUnit.HOUR);
        if (hours <= 31 * 24) return new Timespan(hours / 24, TimeUnit.DAY);
        if (hours <= 98 * 24) return new Timespan(hours / 24 / 7, TimeUnit.WEEK);
        return new Timespan(hours / 24 / 30, TimeUnit.MONTH);
    }
}

enum TimeUnit {
    MINUTE("m", 1),
    HOUR("h", 60),
    DAY("d", 60 * 24),
    WEEK("w", 60 * 24 * 7),
    MONTH("M", 60 * 24 * 30),
    YEAR("y", 60 * 24 * 365);

    public final String shortName;
    public final long millis;
    private static final Map<String, TimeUnit> NAMES = new HashMap<String, TimeUnit>();

    static {
        for (TimeUnit unit : TimeUnit.values()) {
            NAMES.put(unit.shortName, unit);
            NAMES.put(unit.name().toLowerCase(), unit);
        }
    }

    TimeUnit(final String shortName, final long minutes) {
        this.shortName = shortName;
        millis = minutes * 60 * 1000;
    }

    public static TimeUnit fromString(String string) {
        TimeUnit result = NAMES.get(string);
        if (result != null) return result;
        return NAMES.get(string.toLowerCase());
    }
}

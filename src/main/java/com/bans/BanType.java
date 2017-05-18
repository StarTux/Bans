package com.winthier.bans;

public enum BanType {
    BAN,
    UNBAN,
    KICK,
    MUTE,
    UNMUTE,
    JAIL,
    UNJAIL,
    WARNING,
    WARNED,
    NOTE,
    ;

    public final String key;

    BanType() {
        this.key = name().toLowerCase();
    }

    public BanType lift() {
        switch (this) {
        case BAN: return UNBAN;
        case MUTE: return UNMUTE;
        case JAIL: return UNJAIL;
        case WARNING: return WARNED;
        default: return this;
        }
    }

    public BanType unlift() {
        switch (this) {
        case UNBAN: return BAN;
        case UNMUTE: return MUTE;
        case UNJAIL: return JAIL;
        case WARNED: return WARNING;
        default: return this;
        }
    }

    public String getNiceName() {
        switch (this) {
        case BAN: return "Ban";
        case UNBAN: return "Unban";
        case KICK: return "Kick";
        case MUTE: return "Mute";
        case UNMUTE: return "Unmute";
        case JAIL: return "Jail";
        case UNJAIL: return "Unjail";
        case WARNING: case WARNED: return "Warn";
        case NOTE: return "Note";
        default: return name();
        }
    }

    public String getPassive() {
        switch (this) {
        case BAN: return "banned";
        case UNBAN: return "unbanned";
        case KICK: return "kicked";
        case MUTE: return "muted";
        case UNMUTE: return "unmuted";
        case JAIL: return "jailed";
        case UNJAIL: return "unjailed";
        case WARNING: case WARNED: return "warned";
        default: return name().toLowerCase() + "d";
        }
    }

    public boolean isActive() {
        switch (this) {
        case BAN: case MUTE: case JAIL: case WARNING: case NOTE: return true;
        default: return false;
        }
    }

    public boolean isLifted() {
        switch (this) {
        case UNBAN: case UNMUTE: case UNJAIL: return true;
        default: return false;
        }
    }

    /**
     * Can this ban type expire? Bans only expire if an expiration
     * date is set in BanTable.
     */
    public boolean expires() {
        switch (this) {
        case BAN: case MUTE: case JAIL: return true;
        default: return false;
        }
    }
}

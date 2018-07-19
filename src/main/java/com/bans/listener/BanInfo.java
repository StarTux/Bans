package com.winthier.bans.listener;

import com.winthier.bans.Ban;

/**
 * Carry ban information an online player.
 */
public final class BanInfo {
    private Ban mute;
    private Ban jail;

    public synchronized boolean isMuted() {
        return mute != null;
    }

    public synchronized void setMute(Ban mute) {
        this.mute = mute;
    }

    public synchronized Ban getMute() {
        return mute;
    }

    public synchronized boolean isJailed() {
        return jail != null;
    }

    public synchronized void setJail(Ban jail) {
        this.jail = jail;
    }

    public synchronized Ban getJail() {
        return jail;
    }
}

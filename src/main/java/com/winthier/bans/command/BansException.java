package com.winthier.bans.command;

import lombok.EqualsAndHashCode;
import lombok.Value;
import net.kyori.adventure.text.Component;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText;

@Value
@EqualsAndHashCode(callSuper = true)
public final class BansException extends RuntimeException {
    private final Component component;

    public BansException(final Component component) {
        super(plainText().serialize(component));
        this.component = component;
    }

    public BansException(final String message) {
        this(text(message, RED));
    }
}

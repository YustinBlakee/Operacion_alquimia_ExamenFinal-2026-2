package com.alquimia.domain;

import java.util.Arrays;
import java.util.Locale;
import static com.alquimia.domain.AlquimiaException.Code.*;

public enum Device {
    STAR_LABS("star-labs", new Position(-500, -200)),
    CCPD("ccpd", new Position(100, -100)),
    STAR_SATELLITE("star-satellite", new Position(500, 100));

    private final String id;
    private final Position position;

    Device(String id, Position position) { this.id = id; this.position = position; }
    public String id() { return id; }
    public Position position() { return position; }

    public static Device fromId(String id) {
        String normalized = normalize(id);
        return Arrays.stream(values()).filter(device -> normalize(device.id).equals(normalized)).findFirst()
            .orElseThrow(() -> new AlquimiaException(UNKNOWN_DEVICE,
                "Dispositivo desconocido. Use star-labs, ccpd o star-satellite."));
    }

    private static String normalize(String id) {
        if (id == null) return "";
        StringBuilder normalized = new StringBuilder();
        id.toLowerCase(Locale.ROOT).codePoints()
            .filter(character -> character != '-' && character != '_' && character != '.'
                && !Character.isWhitespace(character) && !Character.isSpaceChar(character))
            .forEach(normalized::appendCodePoint);
        return normalized.toString();
    }
}

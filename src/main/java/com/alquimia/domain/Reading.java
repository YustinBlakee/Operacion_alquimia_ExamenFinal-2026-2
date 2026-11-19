package com.alquimia.domain;

import java.util.List;
import static com.alquimia.domain.AlquimiaException.Code.INVALID_DATA;

/** Instantanea inmutable de los datos completos de un dispositivo. */
public record Reading(Device device, double distance, List<String> message) {
    public Reading {
        if (device == null || !Double.isFinite(distance) || distance < 0 || distance > 1_000_000_000) {
            throw new AlquimiaException(INVALID_DATA, "La distancia debe estar entre 0 y 1000000000 y ser finita.");
        }
        if (message == null || message.isEmpty() || message.size() > 1000) {
            throw new AlquimiaException(INVALID_DATA, "El mensaje debe contener entre 1 y 1000 posiciones.");
        }
        if (message.stream().anyMatch(word -> word == null || word.length() > 100
            || (!word.isEmpty() && word.codePoints().anyMatch(Character::isWhitespace)))) {
            throw new AlquimiaException(INVALID_DATA,
                "Cada posicion debe contener una palabra de hasta 100 caracteres o una cadena vacia.");
        }
        message = List.copyOf(message);
    }

    public static Reading of(Device device, Double distance, List<String> message) {
        if (distance == null) throw new AlquimiaException(INVALID_DATA, "La distancia es obligatoria.");
        return new Reading(device, distance, message);
    }
}

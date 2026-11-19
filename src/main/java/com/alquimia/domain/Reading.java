package com.alquimia.domain;

import java.util.List;
import static com.alquimia.domain.AlquimiaException.Code.INVALID_DATA;

/** Instantanea inmutable de los datos completos de un dispositivo. */
public record Reading(Device device, double distance, List<String> message) {
    public Reading {
        if (device == null || !Double.isFinite(distance) || distance < 0) {
            throw new AlquimiaException(INVALID_DATA, "El dispositivo es obligatorio y la distancia debe ser finita y no negativa.");
        }
        if (message == null) {
            throw new AlquimiaException(INVALID_DATA, "El fragmento del mensaje es obligatorio; use un arreglo vacio si no se recibieron palabras.");
        }
        if (message.stream().anyMatch(word -> word == null)) {
            throw new AlquimiaException(INVALID_DATA, "Las posiciones del mensaje no pueden ser nulas; use una cadena vacia para palabras ausentes.");
        }
        message = List.copyOf(message);
    }

    public static Reading of(Device device, Double distance, List<String> message) {
        if (distance == null) throw new AlquimiaException(INVALID_DATA, "La distancia es obligatoria.");
        return new Reading(device, distance, message);
    }
}

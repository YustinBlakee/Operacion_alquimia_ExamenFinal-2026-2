package com.alquimia.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import static com.alquimia.domain.AlquimiaException.Code.*;

/** Servicio de dominio puro: no conoce Spring, HTTP ni almacenamiento. */
public final class IntelligenceProcessor {
    public static final double DISTANCE_TOLERANCE = 0.01;
    private final Trilateration trilateration = new Trilateration();

    public IntelligenceResult process(List<Reading> readings) {
        if (readings == null) throw new AlquimiaException(INVALID_DATA, "Las lecturas son obligatorias.");
        Map<Device, Reading> byDevice = new EnumMap<>(Device.class);
        for (Reading reading : readings) {
            if (reading == null) throw new AlquimiaException(INVALID_DATA, "Una lectura no puede ser nula.");
            if (byDevice.putIfAbsent(reading.device(), reading) != null) {
                throw new AlquimiaException(INVALID_DATA, "No se permiten dispositivos repetidos.");
            }
        }
        if (byDevice.size() != Device.values().length) {
            throw new AlquimiaException(INSUFFICIENT_DATA, "Se necesitan lecturas de los tres dispositivos.");
        }
        return new IntelligenceResult(trilateration.locate(List.copyOf(byDevice.values())), reconstruct(byDevice));
    }

    private String reconstruct(Map<Device, Reading> readings) {
        int length = readings.get(Device.STAR_LABS).message().size();
        if (readings.values().stream().anyMatch(reading -> reading.message().size() != length)) {
            throw new AlquimiaException(INVALID_DATA, "Los fragmentos deben tener la misma cantidad de posiciones.");
        }
        String[] words = new String[length];
        for (int i = 0; i < length; i++) {
            for (Reading reading : readings.values()) {
                String word = reading.message().get(i);
                if (word.isEmpty()) continue;
                if (words[i] != null && !words[i].equals(word)) {
                    throw new AlquimiaException(CONFLICTING_MESSAGE, "Palabras contradictorias en la posicion " + i + ".");
                }
                words[i] = word;
            }
            if (words[i] == null) {
                throw new AlquimiaException(INCOMPLETE_MESSAGE, "Falta la palabra en la posicion " + i + ".");
            }
        }
        return String.join(" ", words);
    }
}

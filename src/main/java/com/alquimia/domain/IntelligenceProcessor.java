package com.alquimia.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import static com.alquimia.domain.AlquimiaException.Code.*;

/** Servicio de dominio puro: no conoce Spring, HTTP ni almacenamiento. */
public final class IntelligenceProcessor {
    public static final double DISTANCE_TOLERANCE = 0.01;

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
        return new IntelligenceResult(locate(byDevice), reconstruct(byDevice));
    }

    private Position locate(Map<Device, Reading> readings) {
        Reading first = readings.get(Device.STAR_LABS);
        Reading second = readings.get(Device.CCPD);
        Reading third = readings.get(Device.STAR_SATELLITE);
        Position p = first.device().position();
        Position q = second.device().position();
        Position r = third.device().position();
        // Restar las ecuaciones de los circulos elimina x^2 e y^2.
        double a = 2 * (q.x() - p.x()), b = 2 * (q.y() - p.y());
        double c = square(first.distance()) - square(second.distance())
            + square(q.x()) + square(q.y()) - square(p.x()) - square(p.y());
        double d = 2 * (r.x() - p.x()), e = 2 * (r.y() - p.y());
        double f = square(first.distance()) - square(third.distance())
            + square(r.x()) + square(r.y()) - square(p.x()) - square(p.y());
        double determinant = a * e - b * d;
        Position result = new Position((c * e - b * f) / determinant, (a * f - c * d) / determinant);
        for (Reading reading : readings.values()) {
            Position sensor = reading.device().position();
            double calculated = Math.hypot(result.x() - sensor.x(), result.y() - sensor.y());
            if (!Double.isFinite(calculated) || Math.abs(calculated - reading.distance()) > DISTANCE_TOLERANCE) {
                throw new AlquimiaException(INCONSISTENT_DISTANCES,
                    "Las distancias no corresponden a una misma ubicacion (tolerancia: 0.01 unidades).");
            }
        }
        return result;
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

    private double square(double value) { return value * value; }
}

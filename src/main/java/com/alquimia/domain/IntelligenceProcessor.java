package com.alquimia.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import static com.alquimia.domain.AlquimiaException.Code.*;

/** Servicio de dominio puro: no conoce Spring, HTTP ni almacenamiento. */
public final class IntelligenceProcessor {
    public static final double DISTANCE_TOLERANCE = 0.01;
    private final Trilateration trilateration;
    private final MessageReconstructor messageReconstructor = new MessageReconstructor();

    public IntelligenceProcessor() { this(DISTANCE_TOLERANCE); }

    public IntelligenceProcessor(double distanceTolerance) {
        trilateration = new Trilateration(distanceTolerance);
    }

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
        List<Reading> completeReadings = List.copyOf(byDevice.values());
        return new IntelligenceResult(trilateration.locate(completeReadings), messageReconstructor.reconstruct(completeReadings));
    }
}

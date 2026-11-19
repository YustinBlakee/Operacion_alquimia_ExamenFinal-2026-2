package com.alquimia.infrastructure.adapter.out;

import com.alquimia.application.port.out.ReadingRepository;
import com.alquimia.domain.*;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import static com.alquimia.domain.AlquimiaException.Code.*;

/** El bloqueo protege operaciones compuestas y evita actualizaciones perdidas. */
public final class InMemoryReadingRepository implements ReadingRepository {
    private final Map<Device, Reading> readings = new EnumMap<>(Device.class);

    public synchronized List<Reading> findAll() { return List.copyOf(readings.values()); }

    public synchronized Reading find(Device device) {
        Reading reading = readings.get(device);
        if (reading == null) throw new AlquimiaException(NOT_FOUND, "El dispositivo no tiene informacion registrada.");
        return reading;
    }

    public synchronized Reading insert(Reading reading) {
        if (readings.containsKey(reading.device())) {
            throw new AlquimiaException(ALREADY_REGISTERED, "El dispositivo ya tiene datos. Use PUT o PATCH.");
        }
        readings.put(reading.device(), reading);
        return reading;
    }

    public synchronized Reading replace(Reading reading) {
        find(reading.device());
        readings.put(reading.device(), reading);
        return reading;
    }

    public synchronized Reading update(Device device, UnaryOperator<Reading> change) {
        Reading updated = change.apply(find(device));
        readings.put(device, updated);
        return updated;
    }

    public synchronized void delete(Device device) {
        find(device);
        readings.remove(device);
    }
}

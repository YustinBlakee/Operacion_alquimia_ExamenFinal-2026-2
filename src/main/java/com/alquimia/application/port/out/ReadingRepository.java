package com.alquimia.application.port.out;

import com.alquimia.domain.*;
import java.util.List;
import java.util.function.UnaryOperator;

/** Cada mutacion y la captura de findAll deben ser atomicas. */
public interface ReadingRepository {
    List<Reading> findAll();
    Reading find(Device device);
    Reading insert(Reading reading);
    Reading replace(Reading reading);
    Reading update(Device device, UnaryOperator<Reading> change);
    void delete(Device device);
}

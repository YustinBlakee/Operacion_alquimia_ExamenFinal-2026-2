package com.alquimia.application.port.in;

import com.alquimia.domain.*;
import java.util.List;

public interface IntelligenceUseCases {
    IntelligenceResult process(List<Reading> readings);
    IntelligenceResult processStored();
    List<Reading> findAll();
    Reading find(Device device);
    Reading register(Reading reading);
    Reading replace(Reading reading);
    Reading update(Device device, Double distance, List<String> message);
    void delete(Device device);
}

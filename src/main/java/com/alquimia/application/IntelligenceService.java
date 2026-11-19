package com.alquimia.application;

import com.alquimia.application.port.in.IntelligenceUseCases;
import com.alquimia.application.port.out.ReadingRepository;
import com.alquimia.domain.*;
import java.util.List;
import static com.alquimia.domain.AlquimiaException.Code.INVALID_DATA;

public final class IntelligenceService implements IntelligenceUseCases {
    private final ReadingRepository repository;
    private final IntelligenceProcessor processor;

    public IntelligenceService(ReadingRepository repository, IntelligenceProcessor processor) {
        this.repository = repository;
        this.processor = processor;
    }

    public IntelligenceResult process(List<Reading> readings) { return processor.process(readings); }
    public IntelligenceResult processStored() { return processor.process(repository.findAll()); }
    public List<Reading> findAll() { return repository.findAll(); }
    public Reading find(Device device) { return repository.find(device); }
    public Reading register(Reading reading) { return repository.insert(reading); }
    public Reading replace(Reading reading) { return repository.replace(reading); }

    public Reading update(Device device, Double distance, List<String> message) {
        if (distance == null && message == null) {
            throw new AlquimiaException(INVALID_DATA, "Incluya distance, message o ambos.");
        }
        return repository.update(device, previous -> Reading.of(device,
            distance == null ? previous.distance() : distance,
            message == null ? previous.message() : message));
    }

    public void delete(Device device) { repository.delete(device); }
}
